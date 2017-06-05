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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.internal.i18n.SoyBidiUtils;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.internal.OptimizeBidiCodeGenVisitor;
import com.google.template.soy.passes.CombineConsecutiveRawTextNodesVisitor;
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
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Main entry point for the Incremental DOM JS Src backend (output target).
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public class IncrementalDomSrcMain {

  /** The scope object that manages the API call scope. */
  private final GuiceSimpleScope apiCallScope;

  /** Provider for getting an instance of OptimizeBidiCodeGenVisitor. */
  private final Provider<OptimizeBidiCodeGenVisitor> optimizeBidiCodeGenVisitorProvider;

  /** Provider for getting an instance of GenJsCodeVisitor. */
  private final Provider<GenIncrementalDomCodeVisitor> genIncrementalDomCodeVisitorProvider;

  /**
   * @param apiCallScope The scope object that manages the API call scope.
   * @param optimizeBidiCodeGenVisitorProvider Provider for getting an instance of
   *     OptimizeBidiCodeGenVisitor.
   * @param genIncrementalDomCodeVisitorProvider Provider for getting an instance of
   *     GenIncrementalDomCodeVisitor.
   */
  @Inject
  public IncrementalDomSrcMain(
      @ApiCall GuiceSimpleScope apiCallScope,
      Provider<OptimizeBidiCodeGenVisitor> optimizeBidiCodeGenVisitorProvider,
      Provider<GenIncrementalDomCodeVisitor> genIncrementalDomCodeVisitorProvider) {
    this.apiCallScope = apiCallScope;
    this.optimizeBidiCodeGenVisitorProvider = optimizeBidiCodeGenVisitorProvider;
    this.genIncrementalDomCodeVisitorProvider = genIncrementalDomCodeVisitorProvider;
  }

  /**
   * Generates Incremental DOM JS source code given a Soy parse tree, an options object, and an
   * optional bundle of translated messages.
   *
   * @param soyTree The Soy parse tree to generate JS source code for.
   * @param registry The template registry that contains all the template information.
   * @param options The compilation options relevant to this backend.
   * @param errorReporter The Soy error reporter that collects errors during code generation.
   * @return A list of strings where each string represents the JS source code that belongs in one
   *     JS file. The generated JS files correspond one-to-one to the original Soy source files.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public List<String> genJsSrc(
      SoyFileSetNode soyTree,
      TemplateRegistry registry,
      SoyIncrementalDomSrcOptions options,
      ErrorReporter errorReporter)
      throws SoySyntaxException {

    SoyJsSrcOptions incrementalJSSrcOptions = options.toJsSrcOptions();

    try (GuiceSimpleScope.InScope inScope = apiCallScope.enter()) {
      // Seed the scoped parameters.
      inScope.seed(SoyJsSrcOptions.class, incrementalJSSrcOptions);
      BidiGlobalDir bidiGlobalDir =
          SoyBidiUtils.decodeBidiGlobalDirFromJsOptions(
              incrementalJSSrcOptions.getBidiGlobalDir(),
              incrementalJSSrcOptions.getUseGoogIsRtlForBidiGlobalDir());
      ApiCallScopeUtils.seedSharedParams(inScope, null /* msgBundle */, bidiGlobalDir);

      // Do the code generation.
      optimizeBidiCodeGenVisitorProvider.get().exec(soyTree);

      new HtmlContextVisitor(errorReporter).exec(soyTree);

      new UnescapingVisitor().exec(soyTree);

      // Must happen after HtmlContextVisitor, so it can infer context for {msg} nodes.
      new IncrementalDomExtractMsgVariablesVisitor().exec(soyTree);
      // some of the above passes may slice up raw text nodes, recombine them.
      new CombineConsecutiveRawTextNodesVisitor().exec(soyTree);
      return genIncrementalDomCodeVisitorProvider.get().gen(soyTree, registry, errorReporter);
    }
  }

  /**
   * Generates Incremental DOM JS source files given a Soy parse tree, an options object, an
   * optional bundle of translated messages, and information on where to put the output files.
   *
   * @param soyTree The Soy parse tree to generate JS source code for.
   * @param templateRegistry The template registry that contains all the template information.
   * @param jsSrcOptions The compilation options relevant to this backend.
   * @param outputPathFormat The format string defining how to build the output file path
   *     corresponding to an input file path.
   * @param errorReporter The Soy error reporter that collects errors during code generation.
   * @throws SoySyntaxException If a syntax error is found.
   * @throws IOException If there is an error in opening/writing an output JS file.
   */
  public void genJsFiles(
      SoyFileSetNode soyTree,
      TemplateRegistry templateRegistry,
      SoyIncrementalDomSrcOptions jsSrcOptions,
      String outputPathFormat,
      ErrorReporter errorReporter)
      throws IOException {

    List<String> jsFileContents = genJsSrc(soyTree, templateRegistry, jsSrcOptions, errorReporter);

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
