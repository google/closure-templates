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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.internal.i18n.SoyBidiUtils;
import com.google.template.soy.pysrc.SoyPySrcOptions;
import com.google.template.soy.pysrc.internal.GenPyExprsVisitor.GenPyExprsVisitorFactory;
import com.google.template.soy.shared.internal.SoyScopedData;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Main entry point for the Python Src backend (output target).
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class PySrcMain {

  private static final SoyErrorKind DUPLICATE_NAMESPACE_ERROR =
      SoyErrorKind.of(
          "Multiple files are providing the same namespace: {0}. Soy namespaces must be unique.");

  /** The scope object that manages the API call scope. */
  private final SoyScopedData.Enterable apiCallScope;

  public PySrcMain(SoyScopedData.Enterable apiCallScope) {
    this.apiCallScope = apiCallScope;
  }

  /**
   * Generates Python source code given a Soy parse tree and an options object.
   *
   * @param soyTree The Soy parse tree to generate Python source code for.
   * @param pySrcOptions The compilation options relevant to this backend.
   * @param currentManifest The namespace manifest for current sources.
   * @param errorReporter The Soy error reporter that collects errors during code generation.
   * @return A list of strings where each string represents the Python source code that belongs in
   *     one Python file. The generated Python files correspond one-to-one to the original Soy
   *     source files.
   */
  private List<String> genPySrc(
      SoyFileSetNode soyTree,
      SoyPySrcOptions pySrcOptions,
      ImmutableMap<String, String> currentManifest,
      ErrorReporter errorReporter) {

    BidiGlobalDir bidiGlobalDir =
        SoyBidiUtils.decodeBidiGlobalDirFromPyOptions(pySrcOptions.getBidiIsRtlFn());
    try (SoyScopedData.InScope inScope = apiCallScope.enter(/* msgBundle= */ null, bidiGlobalDir)) {
      return createVisitor(pySrcOptions, inScope.getBidiGlobalDir(), errorReporter, currentManifest)
          .gen(soyTree, errorReporter);
    }
  }

  /**
   * Generates Python source files given a Soy parse tree, an options object, and information on
   * where to put the output files.
   *
   * @param soyTree The Soy parse tree to generate Python source code for.
   * @param pySrcOptions The compilation options relevant to this backend.
   * @param errorReporter The Soy error reporter that collects errors during code generation.
   * @throws IOException If there is an error in opening/writing an output Python file.
   */
  public List<String> genPyFiles(
      SoyFileSetNode soyTree, SoyPySrcOptions pySrcOptions, ErrorReporter errorReporter)
      throws IOException {

    // Generate the manifest and add it to the current manifest.
    ImmutableMap<String, String> manifest =
        generateManifest(
            getSoyNamespaces(soyTree),
            pySrcOptions.getInputToOutputFilePaths(),
            pySrcOptions.getOutputDirectoryFlag(),
            errorReporter);

    // Generate the Python source.
    List<String> pyFileContents = genPySrc(soyTree, pySrcOptions, manifest, errorReporter);

    if (soyTree.getChildren().size() != pyFileContents.size()) {
      throw new AssertionError(
          String.format(
              "Expected to generate %d code chunk(s), got %d",
              soyTree.getChildren().size(), pyFileContents.size()));
    }

    // Write out the manifest file.
    if (pySrcOptions.namespaceManifestFile() != null) {
      try (Writer out =
          Files.newWriter(new File(pySrcOptions.namespaceManifestFile()), StandardCharsets.UTF_8)) {
        for (String namespace : manifest.keySet()) {
          out.write(String.format("%s=%s\n", namespace, manifest.get(namespace)));
        }
      }
    }

    // Return the python outputs.
    return pyFileContents;
  }

  /**
   * Generate the manifest file by finding the output file paths and converting them into a Python
   * import format.
   */
  private static ImmutableMap<String, String> generateManifest(
      Map<SourceFilePath, String> soyNamespaces,
      Map<SourceFilePath, Path> inputToOutputPaths,
      Optional<Path> outputDirectoryFlag,
      ErrorReporter errorReporter) {
    Map<String, String> manifest = new HashMap<>();

    for (SourceFilePath inputFilePath : inputToOutputPaths.keySet()) {
      String outputFilePath = inputToOutputPaths.get(inputFilePath).toString();
      String pythonPath = outputFilePath.replace(".py", "").replace('/', '.');

      String namespace = soyNamespaces.get(inputFilePath);
      if (manifest.containsKey(namespace)) {
        errorReporter.report(SourceLocation.UNKNOWN, DUPLICATE_NAMESPACE_ERROR, namespace);
      }

      manifest.put(namespace, pythonPath);
    }

    return ImmutableMap.copyOf(manifest);
  }

  private static ImmutableMap<SourceFilePath, String> getSoyNamespaces(SoyFileSetNode soyTree) {
    ImmutableMap.Builder<SourceFilePath, String> namespaces = new ImmutableMap.Builder<>();
    for (SoyFileNode soyFile : soyTree.getChildren()) {
      namespaces.put(soyFile.getFilePath(), soyFile.getNamespace());
    }
    return namespaces.build();
  }

  @VisibleForTesting
  static GenPyCodeVisitor createVisitor(
      SoyPySrcOptions pySrcOptions,
      BidiGlobalDir bidiGlobalDir,
      ErrorReporter errorReporter,
      ImmutableMap<String, String> currentManifest) {
    final IsComputableAsPyExprVisitor isComputableAsPyExprs = new IsComputableAsPyExprVisitor();
    // There is a circular dependency between the GenPyExprsVisitorFactory and GenPyCallExprVisitor
    // here we resolve it with a mutable field in a custom provider
    final PythonValueFactoryImpl pluginValueFactory =
        new PythonValueFactoryImpl(errorReporter, bidiGlobalDir);
    class PyCallExprVisitorSupplier implements Supplier<GenPyCallExprVisitor> {
      GenPyExprsVisitorFactory factory;

      @Override
      public GenPyCallExprVisitor get() {
        return new GenPyCallExprVisitor(
            isComputableAsPyExprs, pluginValueFactory, checkNotNull(factory));
      }
    }
    PyCallExprVisitorSupplier provider = new PyCallExprVisitorSupplier();
    GenPyExprsVisitorFactory genPyExprsFactory =
        new GenPyExprsVisitorFactory(isComputableAsPyExprs, pluginValueFactory, provider);
    provider.factory = genPyExprsFactory;

    return new GenPyCodeVisitor(
        pySrcOptions,
        currentManifest,
        isComputableAsPyExprs,
        genPyExprsFactory,
        provider.get(),
        pluginValueFactory);
  }
}
