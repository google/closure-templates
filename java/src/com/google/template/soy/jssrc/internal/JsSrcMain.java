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
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.internal.i18n.SoyBidiUtils;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.internal.InsertMsgsVisitor;
import com.google.template.soy.msgs.internal.InsertMsgsVisitor.EncounteredPlrselMsgException;
import com.google.template.soy.shared.internal.ApiCallScopeUtils;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.internal.MainEntryPointUtils;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.ApiCall;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.IsUsingIjData;
import com.google.template.soy.sharedpasses.IsUsingIjDataVisitor;
import com.google.template.soy.sharedpasses.opti.SimplifyVisitor;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoySyntaxExceptionUtils;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Main entry point for the JS Src backend (output target).
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class JsSrcMain {


  /** The scope object that manages the API call scope. */
  private final GuiceSimpleScope apiCallScope;

  /** The instanceof of SimplifyVisitor to use. */
  private final SimplifyVisitor simplifyVisitor;

  /** Provider for getting an instance of OptimizeBidiCodeGenVisitor. */
  private final Provider<OptimizeBidiCodeGenVisitor> optimizeBidiCodeGenVisitorProvider;

  /** Provider for getting an instance of GenJsCodeVisitor. */
  private final Provider<GenJsCodeVisitor> genJsCodeVisitorProvider;


  /**
   * @param apiCallScope The scope object that manages the API call scope.
   * @param simplifyVisitor The instance of SimplifyVisitor to use.
   * @param optimizeBidiCodeGenVisitorProvider Provider for getting an instance of
   *     OptimizeBidiCodeGenVisitor.
   * @param genJsCodeVisitorProvider Provider for getting an instance of GenJsCodeVisitor.
   */
  @Inject
  public JsSrcMain(
      @ApiCall GuiceSimpleScope apiCallScope, SimplifyVisitor simplifyVisitor,
      Provider<OptimizeBidiCodeGenVisitor> optimizeBidiCodeGenVisitorProvider,
      Provider<GenJsCodeVisitor> genJsCodeVisitorProvider) {
    this.apiCallScope = apiCallScope;
    this.simplifyVisitor = simplifyVisitor;
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

    // Generate code with the opt_ijData param if either (a) the user specified the compiler flag
    // --isUsingIjData or (b) any of the Soy code in the file set references injected data.
    boolean isUsingIjData =
        jsSrcOptions.isUsingIjData() || (new IsUsingIjDataVisitor()).exec(soyTree);

    // Make sure that we don't try to use goog.i18n.bidi when we aren't supposed to use Closure.
    Preconditions.checkState(
        !jsSrcOptions.getUseGoogIsRtlForBidiGlobalDir() ||
        jsSrcOptions.shouldProvideRequireSoyNamespaces() ||
        jsSrcOptions.shouldProvideRequireJsFunctions(),
        "Do not specify useGoogIsRtlForBidiGlobalDir without either" +
        " shouldProvideRequireSoyNamespaces or shouldProvideRequireJsFunctions.");

    apiCallScope.enter();
    try {
      // Seed the scoped parameters.
      apiCallScope.seed(SoyJsSrcOptions.class, jsSrcOptions);
      apiCallScope.seed(Key.get(Boolean.class, IsUsingIjData.class), isUsingIjData);
      BidiGlobalDir bidiGlobalDir = SoyBidiUtils.decodeBidiGlobalDirFromOptions(
          jsSrcOptions.getBidiGlobalDir(),
          jsSrcOptions.getUseGoogIsRtlForBidiGlobalDir());
      ApiCallScopeUtils.seedSharedParams(apiCallScope, msgBundle, bidiGlobalDir);

      // Replace MsgNodes.
      if (jsSrcOptions.shouldGenerateGoogMsgDefs()) {
        (new ReplaceMsgsWithGoogMsgsVisitor()).exec(soyTree);
        (new MoveGoogMsgDefNodesEarlierVisitor()).exec(soyTree);
        Preconditions.checkState(
            bidiGlobalDir != null,
            "If enabling shouldGenerateGoogMsgDefs, must also set bidi global directionality.");
      } else {
        Preconditions.checkState(
            bidiGlobalDir == null || bidiGlobalDir.isStaticValue(),
            "If using bidiGlobalIsRtlCodeSnippet, must also enable shouldGenerateGoogMsgDefs.");
        try {
          (new InsertMsgsVisitor(msgBundle, false)).exec(soyTree);
        } catch (EncounteredPlrselMsgException e) {
          throw SoySyntaxExceptionUtils.createWithNode(
              "JS code generation currently only supports plural/select messages when" +
                  " shouldGenerateGoogMsgDefs is true.",
              e.msgNode);
        }
      }

      // Do the code generation.
      optimizeBidiCodeGenVisitorProvider.get().exec(soyTree);
      simplifyVisitor.exec(soyTree);
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

    ImmutableList<SoyFileNode> srcsToCompile = ImmutableList.copyOf(Iterables.filter(
        soyTree.getChildren(), SoyFileNode.MATCH_SRC_FILENODE));

    if (srcsToCompile.size() != jsFileContents.size()) {
      throw new AssertionError(String.format("Expected to generate %d code chunk(s), got %d",
          srcsToCompile.size(), jsFileContents.size()));
    }

    Multimap<String, Integer> outputs =
        mapOutputsToSrcs(locale, outputPathFormat, inputPathsPrefix, srcsToCompile);

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

  /**
   * Maps output paths to indices of inputs that should be emitted to them.
   */
  private Multimap<String, Integer> mapOutputsToSrcs(String locale, String outputPathFormat,
      String inputPathsPrefix, ImmutableList<SoyFileNode> fileNodes) {
    Multimap<String, Integer> outputs = ArrayListMultimap.create();

    // First, check that the parent directories for all output files exist, and group the output
    // files by the inputs that go there.
    // This means that the compiled source from multiple input files might be written to a single
    // output file, as is the case when there are multiple inputs, and the output format string
    // contains no wildcards.
    for (int i = 0; i < fileNodes.size(); ++i) {
      SoyFileNode inputFile = fileNodes.get(i);
      String inputFilePath = inputFile.getFilePath();
      String outputFilePath = MainEntryPointUtils.buildFilePath(
          outputPathFormat, locale, inputFilePath, inputPathsPrefix);

      BaseUtils.ensureDirsExistInPath(outputFilePath);
      outputs.put(outputFilePath, i);
    }
    return outputs;
  }
}
