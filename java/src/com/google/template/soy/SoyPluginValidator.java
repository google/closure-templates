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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.error.SoyCompilationException;
import com.google.template.soy.error.SoyErrors;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.kohsuke.args4j.Option;

/** Executable that validates SoySourceFunctions. */
public final class SoyPluginValidator extends AbstractSoyCompiler {
  // bazel requires we have an output, so we need a path to write an output to.
  @Option(
      name = "--output",
      required = true,
      usage =
          "[Required] The file name of the output file to be written. "
              + "This will only contain the word 'true'.")
  private File output;

  @Option(
      name = "--pluginRuntimeJars",
      required = false,
      usage =
          "[Optional] The list of jars that contain the plugin runtime"
              + " logic, for use validating plugin dependencies at compile time. If not set,"
              + " runtime jars are assumed to be on the classpath.",
      handler = SoyCmdLineParser.FileListOptionHandler.class)
  private List<File> pluginRuntimeJars;

  SoyPluginValidator(PluginLoader loader, SoyInputCache cache) {
    super(loader, cache);
  }

  SoyPluginValidator() {}

  public static void main(final String[] args) {
    new SoyPluginValidator().runMain(args);
  }

  @Override
  boolean requireSources() {
    return false;
  }

  @Override
  String formatCompilationException(SoyCompilationException sce) {
    return SoyErrors.formatErrorsMessageOnly(sce.getErrors());
  }

  @Override
  protected void compile(SoyFileSet.Builder sfsBuilder) throws IOException {
    if (pluginRuntimeJars != null) {
      sfsBuilder.setPluginRuntimeJars(pluginRuntimeJars);
    }
    sfsBuilder.build().validateUserPlugins();
    Files.write(output.toPath(), ImmutableList.of("true"), UTF_8);
  }
}
