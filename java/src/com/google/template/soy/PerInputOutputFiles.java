/*
 * Copyright 2019 Google Inc.
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.io.MoreFiles;
import com.google.template.soy.shared.internal.MainEntryPointUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.kohsuke.args4j.Option;

/**
 * A set of flags and utilities interpreting them for mapping source files to output files.
 *
 * <p>TODO(lukes): use this for soy_py and soy_js
 */
final class PerInputOutputFiles {
  // Concatenating JS files is not safe unless we know that the last statement from one
  // couldn't combine with the isFirst statement of the next.  Inserting a semicolon will
  // prevent this from happening.
  static final Joiner JS_JOINER = Joiner.on("\n;\n");

  @Option(
      name = "--outputPathFormat",
      usage =
          "[Required] A format string that specifies how to build the path to each"
              + " output file. If not generating localized JS, then there will be one output"
              + " JS file (UTF-8) for each input Soy file. If generating localized JS, then"
              + " there will be one output JS file for each combination of input Soy file and"
              + " locale. The format string can include literal characters as well as the"
              + " placeholders {INPUT_DIRECTORY}, {INPUT_FILE_NAME},"
              + " {INPUT_FILE_NAME_NO_EXT}, {LOCALE}, {LOCALE_LOWER_CASE}. Note"
              + " {LOCALE_LOWER_CASE} also turns dash into underscore, e.g. pt-BR becomes"
              + " pt_br.")
  private String outputPathFormat;

  @Option(
      name = "--inputRoots",
      usage =
          "[Optional] May be set along with the --outputDirectory flag to modify how input paths "
              + "are transformed into output paths.  See --outputDirectory for more information.",
      handler = SoyCmdLineParser.PathListOptionHandler.class)
  private List<Path> inputRoots = new ArrayList<>();

  @Option(
      name = "--outputDirectory",
      usage =
          "[Optional] The directory where to output generated code.. The generated file names will"
              + " be based on the input file names with the following rules:\n"
              + " * All files will be output with a '.js' suffix.\n"
              + " * If a src file starts with a registered --inputRoot then the output path will "
              + "be the same relative path within the output directory.\n "
              + " * If generated localized srcs, the file name will use the locale as an "
              + "additional segment, e.g. foo.soy -> foo__fr.soy.js"
              + "\n\nThis flag may not be used if --outputPathFormat is set")
  private Path outputDirectory;

  private final String extension;
  private final Joiner fileJoiner;

  PerInputOutputFiles(String extension, Joiner fileJoiner) {
    this.extension = extension;
    this.fileJoiner = fileJoiner;
  }

  void validateFlags() {
    if (outputPathFormat != null && (!inputRoots.isEmpty() || outputDirectory != null)) {
      exitWithError("Must set either --outputPathFormat or --outputDirectory and --inputRoots.");
    }
    if (outputPathFormat == null && outputDirectory == null) {
      exitWithError("Must set at least one of --outputPathFormat or --outputDirectory.");
    }
  }

  void writeFiles(List<File> srcs, List<String> jsOuts, @Nullable String locale) {
    if (srcs.size() != jsOuts.size()) {
      throw new AssertionError(
          String.format(
              "Expected to generate %d code chunk(s), got %d", srcs.size(), jsOuts.size()));
    }

    ListMultimap<Path, String> outputPathToJs =
        MultimapBuilder.linkedHashKeys().arrayListValues().build();
    for (int i = 0; i < srcs.size(); i++) {
      outputPathToJs.put(getOutputPath(srcs.get(i), locale), jsOuts.get(i));
    }
    for (Path outputPath : outputPathToJs.keySet()) {
      if (outputPath.getParent() != null) {
        outputPath.getParent().toFile().mkdirs();
      }
      try {
        // Having multiple input files map to the same output file is only possible with the
        // --outputPathFormat flag
        MoreFiles.asCharSink(outputPath, UTF_8)
            .write(fileJoiner.join(outputPathToJs.get(outputPath)));
      } catch (IOException ioe) {
        throw new CommandLineError("Failed to write: " + outputPath + ": " + ioe.getMessage(), ioe);
      }
    }
  }

  @VisibleForTesting
  Path getOutputPath(File input, @Nullable String locale) {
    Path path = input.toPath();
    String transformedLocale = locale == null ? null : Ascii.toLowerCase(locale).replace('-', '_');
    if (outputDirectory != null) {
      for (Path root : inputRoots) {
        if (path.startsWith(root)) {
          path = root.relativize(path);
          break;
        }
      }
      String fileName = path.getFileName().toString();
      int extensionLocation = fileName.lastIndexOf('.');
      if (extensionLocation == -1) {
        // consider making this an error, possibly an error if it isn't .soy
        extensionLocation = fileName.length();
      }
      fileName =
          fileName.substring(0, extensionLocation)
              + (transformedLocale != null ? "_" + transformedLocale : "")
              + "."
              + extension;
      path = path.resolveSibling(fileName);
      path = outputDirectory.resolve(path);
      return path;
    } else {
      return Paths.get(
          MainEntryPointUtils.buildFilePath(outputPathFormat, locale, path.toString()));
    }
  }

  /**
   * Prints an error message and the usage string, and then exits.
   *
   * @param errorMsg The error message to print.
   */
  static final RuntimeException exitWithError(String errorMsg) {
    throw new CommandLineError("Error: " + errorMsg);
  }
}
