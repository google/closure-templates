/*
 * Copyright 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.pysrc.internal;

import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.shared.internal.FindCalleesNotInFileVisitor;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.RuntimePath;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoySyntaxExceptionUtils;
import com.google.template.soy.soytree.TemplateNode;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;


/**
 * Visitor for generating full Python code (i.e. statements) for parse tree nodes.
 *
 * <p> {@link #exec} should be called on a full parse tree. Python source code will be generated
 * for all the Soy files. The return value is a list of strings, each string being the content of
 * one generated Python file (corresponding to one Soy file).
 *
 */
final class GenPyCodeVisitor extends AbstractSoyNodeVisitor<List<String>> {

  /** The module path for the runtime libraries. */
  private final String runtimePath;

  /** The contents of the generated Python files. */
  private List<String> pyFilesContents;

  /** The PyCodeBuilder to build the current Python file being generated (during a run). */
  private PyCodeBuilder pyCodeBuilder;

  /**
   * @param runtimePath The module path for the runtime libraries.
   */
  @Inject
  GenPyCodeVisitor(@RuntimePath String runtimePath) {
    this.runtimePath = runtimePath;
  }

  @Override public List<String> exec(SoyNode node) {
    pyFilesContents = new ArrayList<String>();
    pyCodeBuilder = null;
    visit(node);
    return pyFilesContents;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitSoyFileSetNode(SoyFileSetNode node) {
    for (SoyFileNode soyFile : node.getChildren()) {
      try {
        visit(soyFile);
      } catch (SoySyntaxException sse) {
        throw sse.associateMetaInfo(null, soyFile.getFilePath(), null);
      }
    }
  }

  /**
   * Visit a SoyFileNode and generate it's Python ouptut.
   *
   * <p>This visitor generates the necessary imports and configuration needed for all Python output
   * files. This includes imports of runtime libraries, external templates called from within this
   * file, and namespacing configuration.
   *
   * <p>Template generation is deferred to other visitors.
   *
   * Example Output:
   * <pre>
   * # coding=utf-8
   * """ This file was automatically generated from my-templates.soy.
   * Please don't edit this file by hand.
   * """
   *
   * ...
   * </pre>
   */
  @Override protected void visitSoyFileNode(SoyFileNode node) {

    if (node.getSoyFileKind() != SoyFileKind.SRC) {
      return;  // don't generate code for deps
    }

    pyCodeBuilder = new PyCodeBuilder();

    // Encode all source files in utf-8 to allow for special unicode characters in the generated
    // literals.
    pyCodeBuilder.appendLine("# coding=utf-8");

    pyCodeBuilder.appendLine("\"\"\" This file was automatically generated from ",
        node.getFileName(), ".");
    pyCodeBuilder.appendLine("Please don't edit this file by hand.");

    // Output a section containing optionally-parsed compiler directives in comments.
    pyCodeBuilder.appendLine();
    if (node.getNamespace() != null) {
      pyCodeBuilder.appendLine(
          "Templates in namespace ", node.getNamespace(), ".");
    }
    pyCodeBuilder.appendLine("\"\"\"");

    // Add code to define Python namespaces and add import calls for libraries.
    pyCodeBuilder.appendLine();
    addCodeToRequireGeneralDeps();
    addCodeToRequireSoyNamespaces(node);
    addCodeToRegisterCurrentSoyNamespace(node);
    addCodeToFixUnicodeStrings();

    // Add code for each template.
    for (TemplateNode template : node.getChildren()) {
      pyCodeBuilder.appendLine().appendLine();
      try {
        visit(template);
      } catch (SoySyntaxException sse) {
        throw sse.associateMetaInfo(null, null, template.getTemplateNameForUserMsgs());
      }
    }

    pyFilesContents.add(pyCodeBuilder.getCode());
    pyCodeBuilder = null;
  }

  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to require general dependencies.
   */
  private void addCodeToRequireGeneralDeps() {
    pyCodeBuilder.appendLine("from __future__ import unicode_literals");

    pyCodeBuilder.appendLine("import math");
    pyCodeBuilder.appendLine("import random");

    // TODO(dcphillips): limit this based on usage?
    pyCodeBuilder.appendLine("from " + runtimePath + " import bidi");
    pyCodeBuilder.appendLine("from " + runtimePath + " import directives");
    pyCodeBuilder.appendLine("from " + runtimePath + " import runtime");
    pyCodeBuilder.appendLine("from " + runtimePath + " import sanitize");
    pyCodeBuilder.appendLine();
  }

  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to require Soy namespaces.
   * @param soyFile The node we're visiting.
   */
  private void addCodeToRequireSoyNamespaces(SoyFileNode soyFile) {
    for (String calleeNotInFile : (new FindCalleesNotInFileVisitor()).exec(soyFile)) {
      int lastDotIndex = calleeNotInFile.lastIndexOf('.');
      if (lastDotIndex == -1) {
        throw SoySyntaxExceptionUtils.createWithNode(
            "Called template \"" + calleeNotInFile + "\" does not reside in a namespace.",
            soyFile);
      }
      String calleeModule = calleeNotInFile.substring(0, lastDotIndex);
      if (calleeModule.length() > 0) {
        String calleeNamespace = calleeModule;
        String calleeName = calleeModule;
        lastDotIndex = calleeModule.lastIndexOf('.');
        if (lastDotIndex != -1) {
          calleeNamespace = calleeModule.substring(0, lastDotIndex);
          calleeName = calleeModule.substring(lastDotIndex + 1);
        }
        pyCodeBuilder.appendLine(calleeName, " = runtime.namespaced_import('", calleeName,
             "', namespace='", calleeNamespace, "')");
      }
    }
    pyCodeBuilder.appendLine();
  }

  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add module constant to register this module's
   * Soy namespace.
   * @param soyFile The node we're visiting.
   */
  private void addCodeToRegisterCurrentSoyNamespace(SoyFileNode soyFile) {
    String namespace = soyFile.getNamespace();
    pyCodeBuilder.appendLine("SOY_NAMESPACE = '" + namespace + "'");
  }

  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to turn byte strings into unicode strings
   * for Python 2.
   */
  private void addCodeToFixUnicodeStrings() {
    pyCodeBuilder.appendLine("try:");
    pyCodeBuilder.increaseIndent();
    pyCodeBuilder.appendLine("str = unicode");
    pyCodeBuilder.decreaseIndent();
    pyCodeBuilder.appendLine("except NameError:");
    pyCodeBuilder.increaseIndent();
    pyCodeBuilder.appendLine("pass");
    pyCodeBuilder.decreaseIndent();
    pyCodeBuilder.appendLine();
  }
}
