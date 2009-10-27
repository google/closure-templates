/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.jssrc.internal;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.internal.InsertMsgsVisitor;
import com.google.template.soy.shared.internal.ApiCallScopeUtils;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.ApiCall;
import com.google.template.soy.sharedpasses.AssertSyntaxVersionV2Visitor;
import com.google.template.soy.sharedpasses.CheckSoyDocVisitor;
import com.google.template.soy.sharedpasses.RemoveHtmlCommentsVisitor;
import com.google.template.soy.soytree.SoyFileSetNode;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;


/**
 * Main entry point for the JS Src backend (output target).
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class JsSrcMain {


  /** The scope object that manages the API call scope. */
  private final GuiceSimpleScope apiCallScope;

  /** Provider for getting an instance of OptimizeBidiCodeGenVisitor. */
  private final Provider<OptimizeBidiCodeGenVisitor> optimizeBidiCodeGenVisitorProvider;

  /** Provider for getting an instance of GenJsCodeVisitor. */
  private final Provider<GenJsCodeVisitor> genJsCodeVisitorProvider;


  /**
   * @param apiCallScope The scope object that manages the API call scope.
   * @param optimizeBidiCodeGenVisitorProvider Provider for getting an instance of
   *     OptimizeBidiCodeGenVisitor.
   * @param genJsCodeVisitorProvider Provider for getting an instance of GenJsCodeVisitor.
   */
  @Inject
  JsSrcMain(@ApiCall GuiceSimpleScope apiCallScope,
            Provider<OptimizeBidiCodeGenVisitor> optimizeBidiCodeGenVisitorProvider,
            Provider<GenJsCodeVisitor> genJsCodeVisitorProvider) {
    this.apiCallScope = apiCallScope;
    this.optimizeBidiCodeGenVisitorProvider = optimizeBidiCodeGenVisitorProvider;
    this.genJsCodeVisitorProvider = genJsCodeVisitorProvider;
  }


  /**
   * Generates JS source code given a Soy parse tree, an options object, and an optional bundle of
   * translated messages.
   *
   * @param soyTree The Soy parse tree to generate JS source code for.
   * @param jsSrcOptions The compilation options relevant to this backend.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @return A list of strings where each string represents the JS source code that belongs in one
   *     JS file. The generated JS files correspond one-to-one to the original Soy source files.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public List<String> genJsSrc(
      SoyFileSetNode soyTree, SoyJsSrcOptions jsSrcOptions, @Nullable SoyMsgBundle msgBundle)
      throws SoySyntaxException {

    if (jsSrcOptions.shouldAllowDeprecatedSyntax()) {
      // Passes that we only want to run if allowing V1 syntax.
      (new RemoveHtmlCommentsVisitor()).exec(soyTree);
      (new CheckSoyDocVisitor(false)).exec(soyTree);

    } else {
      // Passes that we only want to run if enforcing V2 syntax.
      (new AssertSyntaxVersionV2Visitor()).exec(soyTree);
      (new CheckSoyDocVisitor(true)).exec(soyTree);
    }

    if (jsSrcOptions.shouldGenerateGoogMsgDefs()) {
      (new ReplaceMsgsWithGoogMsgsVisitor()).exec(soyTree);
      (new MoveGoogMsgNodesEarlierVisitor()).exec(soyTree);
      Preconditions.checkState(
          jsSrcOptions.getBidiGlobalDir() != 0,
          "If enabling shouldGenerateGoogMsgDefs, must also set bidiGlobalDir.");
    } else {
      (new InsertMsgsVisitor(msgBundle)).exec(soyTree);
    }

    apiCallScope.enter();
    try {
      // Seed the scoped parameters.
      apiCallScope.seed(SoyJsSrcOptions.class, jsSrcOptions);
      ApiCallScopeUtils.seedSharedParams(
          apiCallScope, msgBundle, jsSrcOptions.getBidiGlobalDir());

      // Do the code generation.
      optimizeBidiCodeGenVisitorProvider.get().exec(soyTree);
      return genJsCodeVisitorProvider.get().exec(soyTree);

    } finally {
      apiCallScope.exit();
    }
  }


  /**
   * Generates JS source files given a Soy parse tree, an options object, an optional bundle of
   * translated messages, and information on where to put the output files.
   *
   * @param soyTree The Soy parse tree to generate JS source code for.
   * @param jsSrcOptions The compilation options relevant to this backend.
   * @param locale The current locale that we're generating JS for, or null if not applicable.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @param outputPathFormat The format string defining how to build the output file path
   *     corresponding to an input file path.
   * @param inputPathsPrefix The input path prefix, or empty string if none.
   * @throws SoySyntaxException If a syntax error is found.
   * @throws IOException If there is an error in opening/writing an output JS file.
   */
  public void genJsFiles(
      SoyFileSetNode soyTree, SoyJsSrcOptions jsSrcOptions, @Nullable String locale,
      @Nullable SoyMsgBundle msgBundle, String outputPathFormat, String inputPathsPrefix)
      throws SoySyntaxException, IOException {

    List<String> jsFileContents = genJsSrc(soyTree, jsSrcOptions, msgBundle);

    int numFiles = soyTree.numChildren();
    if (numFiles != jsFileContents.size()) {
      throw new AssertionError();
    }

    for (int i = 0; i < numFiles; i++) {
      String inputFilePath = soyTree.getChild(i).getFilePath();
      String outputFilePath =
          JsSrcUtils.buildFilePath(outputPathFormat, locale, inputFilePath, inputPathsPrefix);

      BaseUtils.ensureDirsExistInPath(outputFilePath);
      Files.write(jsFileContents.get(i), new File(outputFilePath), Charsets.UTF_8);
    }
  }

}
