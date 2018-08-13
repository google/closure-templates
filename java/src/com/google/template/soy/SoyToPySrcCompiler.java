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

package com.google.template.soy;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.template.soy.pysrc.SoyPySrcOptions;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.kohsuke.args4j.Option;

/**
 * Executable for compiling a set of Soy files into corresponding Python source files.
 *
 * <p>Note: The Python output and runtime libraries are targeted at Python v2.7. Support for Python
 * v3.1+ is also intended through the use of __future__ and version agnostic syntax, HOWEVER at the
 * moment testing support is only guaranteed for v2.7.
 *
 */
public final class SoyToPySrcCompiler extends AbstractSoyCompiler {

  @Option(
    name = "--outputPathFormat",
    required = true,
    usage =
        "[Required] A format string that specifies how to build the path to each"
            + " output file. There will be one output Python file (UTF-8) for each input Soy"
            + " file. The format string can include literal characters as well as the"
            + " placeholders {INPUT_DIRECTORY}, {INPUT_FILE_NAME}, and"
            + " {INPUT_FILE_NAME_NO_EXT}. Additionally periods are not allowed in the"
            + " outputted filename outside of the final py extension."
  )
  private String outputPathFormat = "";

  @Option(
    name = "--runtimePath",
    required = true,
    usage =
        "[Required] The module path used to find the python runtime libraries. This"
            + " should be in dot notation format."
  )
  private String runtimePath = "";

  @Option(
    name = "--environmentModulePath",
    usage =
        "A custom python module which will override the environment.py module if custom"
            + " functionality is required for interacting with your runtime environment. This"
            + " module must implement all functions of the environment module if provided."
  )
  private String environmentModulePath = "";

  @Option(
    name = "--translationClass",
    usage =
        "The full class name of the python runtime translation class."
            + " The name should include the absolute module path and class name in dot notation"
            + " format (e.g. \"my.package.module.TranslatorClass\")."
            + " It is required for {msg} command."
  )
  private String translationClass = "";

  @Option(
    name = "--bidiIsRtlFn",
    usage =
        "The full name of a function used to determine if bidi is rtl for setting global"
            + " directionality. The name should include the absolute module path and function"
            + "name in dot notation format (e.g. \"my.app.bidi.is_rtl\"). Only applicable if"
            + " your Soy code uses bidi functions/directives."
  )
  private String bidiIsRtlFn = "";

  @Option(
    name = "--namespaceManifestPath",
    usage =
        "A list of paths to a manifest file which provides a map of soy namespaces to"
            + " their Python paths. If this is provided, direct imports will be used,"
            + " drastically improving runtime performance.",
    handler = SoyCmdLineParser.StringListOptionHandler.class
  )
  private List<String> namespaceManifestPaths = new ArrayList<>();

  @Option(
    name = "--outputNamespaceManifest",
    usage =
        "The name fo the manifest file containing a map of all soy namespaces to their Python paths"
            + " to write. Default is to not write this file."
  )
  private String outputNamespaceManifest = null;

  /**
   * Compiles a set of Soy files into corresponding Python source files.
   *
   * @param args Should contain command-line flags and the list of paths to the Soy files.
   * @throws IOException If there are problems reading the input files or writing the output file.
   */
  public static void main(final String[] args) throws IOException {
    new SoyToPySrcCompiler().runMain(args);
  }

  @Override
  void validateFlags() {
    if (runtimePath.length() == 0) {
      exitWithError("Must provide the Python runtime library path.");
    }
    if (outputPathFormat.isEmpty()) {
      exitWithError("Must provide the output path format.");
    }
  }

  @Override
  protected void compile(SoyFileSet.Builder sfsBuilder) throws IOException {
    // Disallow external call entirely in Python.
    sfsBuilder.setAllowExternalCalls(false);
    // Require strict templates in Python.
    sfsBuilder.setStrictAutoescapingRequired(true);
    SoyFileSet sfs = sfsBuilder.build();
    // Load the manifest if available.
    ImmutableMap<String, String> manifest = loadNamespaceManifest(namespaceManifestPaths);
    if (!manifest.isEmpty() && outputNamespaceManifest == null) {
      exitWithError("Namespace manifests provided without outputting a new manifest.");
    }

    // Create SoyPySrcOptions.
    SoyPySrcOptions pySrcOptions =
        new SoyPySrcOptions(
            runtimePath,
            environmentModulePath,
            bidiIsRtlFn,
            translationClass,
            manifest,
            outputNamespaceManifest);

    // Compile.
    sfs.compileToPySrcFiles(outputPathFormat, pySrcOptions);
  }

  /**
   * Load the manifest files provided at namespaceManifestPaths, deserialize (via gson), and combine
   * into a map containing all soy namespaces to their Python paths.
   */
  private ImmutableMap<String, String> loadNamespaceManifest(List<String> namespaceManifestPaths) {
    if (namespaceManifestPaths.isEmpty()) {
      return ImmutableMap.of();
    }

    ImmutableMap.Builder<String, String> manifest = new ImmutableMap.Builder<>();
    for (String manifestPath : namespaceManifestPaths) {
      try (Reader manifestFile = Files.newReader(new File(manifestPath), StandardCharsets.UTF_8)) {
        Properties prop = new Properties();
        prop.load(manifestFile);
        for (String namespace : prop.stringPropertyNames()) {
          manifest.put(namespace, prop.getProperty(namespace));
        }
      } catch (IOException e) {
        exitWithError("Unable to read the namespaceManifest file at " + manifestPath);
      }
    }

    return manifest.build();
  }
}
