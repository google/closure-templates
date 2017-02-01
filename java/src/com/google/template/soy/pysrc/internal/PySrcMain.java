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

package com.google.template.soy.pysrc.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.internal.i18n.SoyBidiUtils;
import com.google.template.soy.pysrc.SoyPySrcOptions;
import com.google.template.soy.pysrc.internal.PyApiCallScopeBindingAnnotations.PyCurrentManifest;
import com.google.template.soy.shared.internal.ApiCallScopeUtils;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.internal.GuiceSimpleScope.WithScope;
import com.google.template.soy.shared.internal.MainEntryPointUtils;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.ApiCall;
import com.google.template.soy.sharedpasses.opti.SimplifyVisitor;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateRegistry;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.inject.Inject;

/**
 * Main entry point for the Python Src backend (output target).
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class PySrcMain {

  /** The scope object that manages the API call scope. */
  private final GuiceSimpleScope apiCallScope;

  /** The instanceof of SimplifyVisitor to use. */
  private final SimplifyVisitor simplifyVisitor;

  /** Provider for getting an instance of GenPyCodeVisitor. */
  private final Provider<GenPyCodeVisitor> genPyCodeVisitorProvider;

  /**
   * @param apiCallScope The scope object that manages the API call scope.
   * @param simplifyVisitor The instance of SimplifyVisitor to use.
   * @param genPyCodeVisitorProvider Provider for getting an instance of GenPyCodeVisitor.
   */
  @Inject
  public PySrcMain(
      @ApiCall GuiceSimpleScope apiCallScope,
      SimplifyVisitor simplifyVisitor,
      Provider<GenPyCodeVisitor> genPyCodeVisitorProvider) {
    this.apiCallScope = apiCallScope;
    this.simplifyVisitor = simplifyVisitor;
    this.genPyCodeVisitorProvider = genPyCodeVisitorProvider;
  }

  /**
   * Generates Python source code given a Soy parse tree and an options object.
   *
   * @param soyTree The Soy parse tree to generate Python source code for.
   * @param pySrcOptions The compilation options relevant to this backend.
   * @param currentManifest The namespace manifest for current sources.
   * @return A list of strings where each string represents the Python source code that belongs in
   *     one Python file. The generated Python files correspond one-to-one to the original Soy
   *     source files.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public List<String> genPySrc(
      SoyFileSetNode soyTree,
      TemplateRegistry templateRegistry,
      SoyPySrcOptions pySrcOptions,
      ImmutableMap<String, String> currentManifest,
      ErrorReporter errorReporter)
      throws SoySyntaxException {

    try (WithScope withScope = apiCallScope.enter()) {
      // Seed the scoped parameters.
      apiCallScope.seed(SoyPySrcOptions.class, pySrcOptions);
      apiCallScope.seed(
          new Key<ImmutableMap<String, String>>(PyCurrentManifest.class) {}, currentManifest);

      BidiGlobalDir bidiGlobalDir =
          SoyBidiUtils.decodeBidiGlobalDirFromPyOptions(pySrcOptions.getBidiIsRtlFn());
      ApiCallScopeUtils.seedSharedParams(apiCallScope, null, bidiGlobalDir);

      simplifyVisitor.simplify(soyTree, templateRegistry);
      return genPyCodeVisitorProvider.get().gen(soyTree, errorReporter);
    }
  }

  /**
   * Generates Python source files given a Soy parse tree, an options object, and information on
   * where to put the output files.
   *
   * @param soyTree The Soy parse tree to generate Python source code for.
   * @param pySrcOptions The compilation options relevant to this backend.
   * @param outputPathFormat The format string defining how to build the output file path
   *     corresponding to an input file path.
   * @param inputPathsPrefix The input path prefix, or empty string if none.
   * @throws SoySyntaxException If a syntax error is found.
   * @throws IOException If there is an error in opening/writing an output Python file.
   */
  public void genPyFiles(
      SoyFileSetNode soyTree,
      TemplateRegistry templateRegistry,
      SoyPySrcOptions pySrcOptions,
      String outputPathFormat,
      String inputPathsPrefix,
      ErrorReporter errorReporter)
      throws SoySyntaxException, IOException {

    ImmutableList<SoyFileNode> srcsToCompile =
        ImmutableList.copyOf(
            Iterables.filter(soyTree.getChildren(), SoyFileNode.MATCH_SRC_FILENODE));

    // Determine the output paths.
    List<String> soyNamespaces = getSoyNamespaces(soyTree);
    Multimap<String, Integer> outputs =
        MainEntryPointUtils.mapOutputsToSrcs(
            null, outputPathFormat, inputPathsPrefix, srcsToCompile);

    // Generate the manifest and add it to the current manifest.
    ImmutableMap<String, String> manifest = generateManifest(soyNamespaces, outputs);

    // Generate the Python source.
    List<String> pyFileContents =
        genPySrc(soyTree, templateRegistry, pySrcOptions, manifest, errorReporter);

    if (srcsToCompile.size() != pyFileContents.size()) {
      throw new AssertionError(
          String.format(
              "Expected to generate %d code chunk(s), got %d",
              srcsToCompile.size(), pyFileContents.size()));
    }

    // Write out the Python outputs.
    for (String outputFilePath : outputs.keySet()) {
      try (Writer out = Files.newWriter(new File(outputFilePath), StandardCharsets.UTF_8)) {
        for (int inputFileIndex : outputs.get(outputFilePath)) {
          out.write(pyFileContents.get(inputFileIndex));
        }
      }
    }

    // Write out the manifest file.
    if (pySrcOptions.doesOutputNamespaceManifest()) {
      String manifestFormat = outputPathFormat.replace(".py", ".MF");
      String manifestPath = MainEntryPointUtils.buildFilePath(manifestFormat, null, "manifest", "");
      try (Writer out = Files.newWriter(new File(manifestPath), StandardCharsets.UTF_8)) {
        Properties prop = new Properties();
        for (String namespace : manifest.keySet()) {
          prop.put(namespace, manifest.get(namespace));
        }
        prop.store(out, null);
      }
    }
  }

  /**
   * Generate the manifest file by finding the output file paths and converting them into a Python
   * import format.
   */
  private static ImmutableMap<String, String> generateManifest(
      List<String> soyNamespaces, Multimap<String, Integer> outputs) {
    ImmutableMap.Builder<String, String> manifest = new ImmutableMap.Builder<>();
    for (String outputFilePath : outputs.keySet()) {
      for (int inputFileIndex : outputs.get(outputFilePath)) {
        String pythonPath = outputFilePath.replace(".py", "").replace('/', '.');

        manifest.put(soyNamespaces.get(inputFileIndex), pythonPath);
      }
    }
    return manifest.build();
  }

  private List<String> getSoyNamespaces(SoyFileSetNode soyTree) {
    List<String> namespaces = new ArrayList<>();
    for (SoyFileNode soyFile : soyTree.getChildren()) {
      namespaces.add(soyFile.getNamespace());
    }
    return namespaces;
  }
}
