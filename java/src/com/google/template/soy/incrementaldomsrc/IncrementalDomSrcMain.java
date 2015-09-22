/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.incrementaldomsrc;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;
import com.google.inject.Key;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.html.passes.HtmlTransformVisitor;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.internal.i18n.SoyBidiUtils;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.internal.OptimizeBidiCodeGenVisitor;
import com.google.template.soy.passes.IjDataQueries;
import com.google.template.soy.shared.internal.ApiCallScopeUtils;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.internal.GuiceSimpleScope.WithScope;
import com.google.template.soy.shared.internal.MainEntryPointUtils;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.ApiCall;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.IsUsingIjData;
import com.google.template.soy.sharedpasses.opti.SimplifyVisitor;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Main entry point for the Incremental DOM  JS Src backend (output target).
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public class IncrementalDomSrcMain {


  /** The scope object that manages the API call scope. */
  private final GuiceSimpleScope apiCallScope;

  /** The instanceof of SimplifyVisitor to use. */
  private final SimplifyVisitor simplifyVisitor;

  /** Provider for getting an instance of OptimizeBidiCodeGenVisitor. */
  private final Provider<OptimizeBidiCodeGenVisitor> optimizeBidiCodeGenVisitorProvider;

  /** Provider for getting an instance of GenJsCodeVisitor. */
  private final Provider<GenIncrementalDomCodeVisitor> genIncrementalDomCodeVisitorProvider;

  /** For reporting errors during code generation. */
  private final ErrorReporter errorReporter;


  /**
   * @param apiCallScope The scope object that manages the API call scope.
   * @param simplifyVisitor The instance of SimplifyVisitor to use.
   * @param optimizeBidiCodeGenVisitorProvider Provider for getting an instance of
   *     OptimizeBidiCodeGenVisitor.
   * @param genIncrementalDomCodeVisitorProvider Provider for getting an instance of 
   *     GenIncrementalDomCodeVisitor.
   */
  @Inject
  public IncrementalDomSrcMain(
      @ApiCall GuiceSimpleScope apiCallScope,
      SimplifyVisitor simplifyVisitor,
      Provider<OptimizeBidiCodeGenVisitor> optimizeBidiCodeGenVisitorProvider,
      Provider<GenIncrementalDomCodeVisitor> genIncrementalDomCodeVisitorProvider,
      ErrorReporter errorReporter) {
    this.apiCallScope = apiCallScope;
    this.simplifyVisitor = simplifyVisitor;
    this.optimizeBidiCodeGenVisitorProvider = optimizeBidiCodeGenVisitorProvider;
    this.genIncrementalDomCodeVisitorProvider = genIncrementalDomCodeVisitorProvider;
    this.errorReporter = errorReporter;
  }


  /**
   * Generates Incremental DOM JS source code given a Soy parse tree, an options object, and an
   * optional bundle of translated messages.
   *
   * @param soyTree The Soy parse tree to generate JS source code for.
   * @param jsSrcOptions The compilation options relevant to this backend.
   * @return A list of strings where each string represents the JS source code that belongs in one
   *     JS file. The generated JS files correspond one-to-one to the original Soy source files.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public List<String> genJsSrc(
      SoyFileSetNode soyTree,
      SoyJsSrcOptions jsSrcOptions)
      throws SoySyntaxException {

    // Generate code with the opt_ijData param if either (a) the user specified the compiler flag
    // --isUsingIjData or (b) any of the Soy code in the file set references injected data.
    boolean isUsingIjData = jsSrcOptions.isUsingIjData()
        || IjDataQueries.isUsingIj(soyTree);

    // Make sure that we don't try to use goog.i18n.bidi when we aren't supposed to use Closure.
    Preconditions.checkState(
        !jsSrcOptions.getUseGoogIsRtlForBidiGlobalDir()
        || jsSrcOptions.shouldProvideRequireSoyNamespaces()
        || jsSrcOptions.shouldProvideRequireJsFunctions()
        || jsSrcOptions.shouldGenerateGoogModules(),
        "Do not specify useGoogIsRtlForBidiGlobalDir without one of "
        + "shouldProvideRequireSoyNamespaces, shouldProvideRequireJsFunctions or "
        + "shouldGenerateGoogModules.");

    try (WithScope withScope = apiCallScope.enter()) {
      // Seed the scoped parameters.
      apiCallScope.seed(SoyJsSrcOptions.class, jsSrcOptions);
      apiCallScope.seed(Key.get(Boolean.class, IsUsingIjData.class), isUsingIjData);
      BidiGlobalDir bidiGlobalDir = SoyBidiUtils.decodeBidiGlobalDirFromJsOptions(
          jsSrcOptions.getBidiGlobalDir(),
          jsSrcOptions.getUseGoogIsRtlForBidiGlobalDir());
      ApiCallScopeUtils.seedSharedParams(apiCallScope, null /* msgBundle */, bidiGlobalDir);

// TODO(sparhami) figure out how to deal with msg nodes - need to support some sort of innerHTML,
// which means we need autoescaping for just those subtrees.
//      new ReplaceMsgsWithGoogMsgsVisitor(errorReporter).exec(soyTree);
//      new MoveGoogMsgDefNodesEarlierVisitor(errorReporter).exec(soyTree);
//      Preconditions.checkState(
//          bidiGlobalDir != null,
//          "If enabling shouldGenerateGoogMsgDefs, must also set bidi global directionality.");

      // Do the code generation.
      optimizeBidiCodeGenVisitorProvider.get().exec(soyTree);
      simplifyVisitor.exec(soyTree);

      new HtmlTransformVisitor(errorReporter).exec(soyTree);
      IncrementalDomOutputOptimizers.collapseOpenTags(soyTree);
      IncrementalDomOutputOptimizers.collapseElements(soyTree);

      return genIncrementalDomCodeVisitorProvider.get().exec(soyTree);
    }
  }

  /**
   * Generates Incremental DOM JS source files given a Soy parse tree, an options object, an
   * optional bundle of translated messages, and information on where to put the output files.
   *
   * @param soyTree The Soy parse tree to generate JS source code for.
   * @param jsSrcOptions The compilation options relevant to this backend.
   * @param outputPathFormat The format string defining how to build the output file path
   *     corresponding to an input file path.
   * @throws SoySyntaxException If a syntax error is found.
   * @throws IOException If there is an error in opening/writing an output JS file.
   */
  public void genJsFiles(
      SoyFileSetNode soyTree,
      SoyJsSrcOptions jsSrcOptions,
      String outputPathFormat)
      throws SoySyntaxException, IOException {

    List<String> jsFileContents = genJsSrc(soyTree, jsSrcOptions);

    ImmutableList<SoyFileNode> srcsToCompile = ImmutableList.copyOf(Iterables.filter(
        soyTree.getChildren(), SoyFileNode.MATCH_SRC_FILENODE));

    if (srcsToCompile.size() != jsFileContents.size()) {
      throw new AssertionError(String.format("Expected to generate %d code chunk(s), got %d",
          srcsToCompile.size(), jsFileContents.size()));
    }

    Multimap<String, Integer> outputs = MainEntryPointUtils.mapOutputsToSrcs(
        null /* locale */, outputPathFormat, "" /* inputPathsPrefix */, srcsToCompile);

    for (String outputFilePath : outputs.keySet()) {
      Writer out = Files.newWriter(new File(outputFilePath), UTF_8);
      try {
        boolean isFirst = true;
        for (int inputFileIndex : outputs.get(outputFilePath)) {
          if (isFirst) {
            isFirst = false;
          } else {
            // Concatenating JS files is not safe unless we know that the last statement from one
            // couldn't combine with the isFirst statement of the next.  Inserting a semicolon will
            // prevent this from happening.
            out.write("\n;\n");
          }
          out.write(jsFileContents.get(inputFileIndex));
        }
      } finally {
        out.close();
      }
    }
  }
}
