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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.internal.i18n.SoyBidiUtils;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.internal.InsertMsgsVisitor;
import com.google.template.soy.passes.CombineConsecutiveRawTextNodesPass;
import com.google.template.soy.shared.internal.ApiCallScopeUtils;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.internal.MainEntryPointUtils;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.ApiCall;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateRegistry;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Main entry point for the JS Src backend (output target).
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
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
  public JsSrcMain(
      @ApiCall GuiceSimpleScope apiCallScope,
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
   * @param templateRegistry The template registry that contains all the template information.
   * @param jsSrcOptions The compilation options relevant to this backend.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @param errorReporter The Soy error reporter that collects errors during code generation.
   * @return A list of strings where each string represents the JS source code that belongs in one
   *     JS file. The generated JS files correspond one-to-one to the original Soy source files.
   */
  public List<String> genJsSrc(
      SoyFileSetNode soyTree,
      TemplateRegistry templateRegistry,
      SoyJsSrcOptions jsSrcOptions,
      @Nullable SoyMsgBundle msgBundle,
      ErrorReporter errorReporter) {

    // Make sure that we don't try to use goog.i18n.bidi when we aren't supposed to use Closure.
    Preconditions.checkState(
        !jsSrcOptions.getUseGoogIsRtlForBidiGlobalDir()
            || jsSrcOptions.shouldProvideRequireSoyNamespaces()
            || jsSrcOptions.shouldProvideRequireJsFunctions(),
        "Do not specify useGoogIsRtlForBidiGlobalDir without either"
            + " shouldProvideRequireSoyNamespaces or shouldProvideRequireJsFunctions.");

    try (GuiceSimpleScope.InScope inScope = apiCallScope.enter()) {
      // Seed the scoped parameters.
      inScope.seed(SoyJsSrcOptions.class, jsSrcOptions);
      BidiGlobalDir bidiGlobalDir =
          SoyBidiUtils.decodeBidiGlobalDirFromJsOptions(
              jsSrcOptions.getBidiGlobalDir(), jsSrcOptions.getUseGoogIsRtlForBidiGlobalDir());
      ApiCallScopeUtils.seedSharedParams(inScope, msgBundle, bidiGlobalDir);

      // Replace MsgNodes.
      if (jsSrcOptions.shouldGenerateGoogMsgDefs()) {
        new ExtractMsgVariablesVisitor().exec(soyTree);
        Preconditions.checkState(
            bidiGlobalDir != null,
            "If enabling shouldGenerateGoogMsgDefs, must also set bidi global directionality.");
      } else {
        Preconditions.checkState(
            bidiGlobalDir == null || bidiGlobalDir.isStaticValue(),
            "If using bidiGlobalIsRtlCodeSnippet, must also enable shouldGenerateGoogMsgDefs.");
        new InsertMsgsVisitor(msgBundle, errorReporter).exec(soyTree);
        new CombineConsecutiveRawTextNodesPass().run(soyTree);
      }

      // Do the code generation.
      optimizeBidiCodeGenVisitorProvider.get().exec(soyTree);
      return genJsCodeVisitorProvider.get().gen(soyTree, templateRegistry, errorReporter);
    }
  }

  /**
   * Generates JS source files given a Soy parse tree, an options object, an optional bundle of
   * translated messages, and information on where to put the output files.
   *
   * @param soyTree The Soy parse tree to generate JS source code for.
   * @param templateRegistry The template registry that contains all the template information.
   * @param jsSrcOptions The compilation options relevant to this backend.
   * @param locale The current locale that we're generating JS for, or null if not applicable.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @param outputPathFormat The format string defining how to build the output file path
   *     corresponding to an input file path.
   * @param inputPathsPrefix The input path prefix, or empty string if none.
   * @param errorReporter The Soy error reporter that collects errors during code generation.
   * @throws SoySyntaxException If a syntax error is found.
   * @throws IOException If there is an error in opening/writing an output JS file.
   */
  public void genJsFiles(
      SoyFileSetNode soyTree,
      TemplateRegistry templateRegistry,
      SoyJsSrcOptions jsSrcOptions,
      @Nullable String locale,
      @Nullable SoyMsgBundle msgBundle,
      String outputPathFormat,
      String inputPathsPrefix,
      ErrorReporter errorReporter)
      throws IOException {

    List<String> jsFileContents =
        genJsSrc(soyTree, templateRegistry, jsSrcOptions, msgBundle, errorReporter);

    ImmutableList<SoyFileNode> srcsToCompile =
        ImmutableList.copyOf(
            Iterables.filter(soyTree.getChildren(), SoyFileNode.MATCH_SRC_FILENODE));

    if (srcsToCompile.size() != jsFileContents.size()) {
      throw new AssertionError(
          String.format(
              "Expected to generate %d code chunk(s), got %d",
              srcsToCompile.size(), jsFileContents.size()));
    }

    Multimap<String, Integer> outputs =
        MainEntryPointUtils.mapOutputsToSrcs(
            locale, outputPathFormat, inputPathsPrefix, srcsToCompile);

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
