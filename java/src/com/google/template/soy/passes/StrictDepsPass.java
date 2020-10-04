/*
 * Copyright 2011 Google Inc.
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

package com.google.template.soy.passes;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.soytree.ImportsContext.ImportsTemplateRegistry;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateMetadata;

/**
 * Visitor to check that there are no external calls. Used by backends that disallow external calls,
 * such as the Tofu (JavaObj) backend.
 */
public final class StrictDepsPass implements CompilerFileSetPass {

  private static final SoyErrorKind CALL_TO_UNDEFINED_TEMPLATE =
      SoyErrorKind.of("Undefined template ''{0}''.{1}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind CALL_TO_INDIRECT_DEPENDENCY =
      SoyErrorKind.of(
          "Call is satisfied only by indirect dependency {0}. Add it as a direct dependency."
          ,
          StyleAllowance.NO_PUNCTUATION);

  private final ErrorReporter errorReporter;

  public StrictDepsPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    for (SoyFileNode file : sourceFiles) {
      for (TemplateLiteralNode node :
          SoyTreeUtils.getAllNodesOfType(file, TemplateLiteralNode.class)) {
        checkTemplateLiteralNode(node, file.getTemplateRegistry());
      }
    }
    return Result.CONTINUE;
  }

  // TODO(gboyer): Consider some deltemplate checking, but it's hard to make a coherent case for
  // deltemplates since it's legitimate to have zero implementations, or to have the implementation
  // in a different part of the dependency graph (if it's late-bound).
  private void checkTemplateLiteralNode(
      TemplateLiteralNode node, ImportsTemplateRegistry registry) {
    TemplateMetadata callee = registry.getBasicTemplateOrElement(node.getResolvedName());

    if (callee == null) {
      reportUndefinedTemplateErrors(node, registry);
    } else {
      SoyFileKind calleeKind = callee.getSoyFileKind();
      String callerFilePath = node.getSourceLocation().getFilePath().path();
      String calleeFilePath = callee.getSourceLocation().getFilePath().path();
      if (calleeKind == SoyFileKind.INDIRECT_DEP) {
        errorReporter.report(
            node.getSourceLocation(),
            CALL_TO_INDIRECT_DEPENDENCY,
            calleeFilePath);
      }
    }
  }

  private void reportUndefinedTemplateErrors(
      TemplateLiteralNode node, ImportsTemplateRegistry registry) {
    Identifier ident = node.getIdentifier();
    // Cross-check the called template's name against the list of imported symbols and the list of
    // known fully-namespaced file names, and report suggestions for the undefined template.
    String closestImportedSymbol =
        SoyErrors.getClosest(registry.getImportedSymbols(), ident.originalName());
    if (!Strings.isNullOrEmpty(closestImportedSymbol)) {
      // Clarify that imports shouldn't be called with a "."
      closestImportedSymbol = "'" + closestImportedSymbol + "' (with no '.')";
    }
    String extraErrorMessage =
        SoyErrors.getDidYouMeanMessage(
            closestImportedSymbol,
            SoyErrors.getClosest(registry.getBasicTemplateOrElementNames(), ident.identifier()));

    errorReporter.report(
        node.getSourceLocation(),
        CALL_TO_UNDEFINED_TEMPLATE,
        ident.identifier(),
        extraErrorMessage);
  }
}
