/*
 * Copyright 2020 Google Inc.
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

import com.google.common.io.Files;
import com.google.template.soy.logging.AnnotatedLoggingConfig;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.kohsuke.args4j.Option;

/** Produces a Soy internal logging config with additional Soy-only details. */
public final class SoyAnnotatedLoggingConfigGenerator extends AbstractSoyCompiler {

  @Option(name = "--raw_logging_config_file", usage = "The logging config file to annotate.")
  private File rawLoggingConfigFile;

  @Option(
      name = "--java_package",
      usage = "The VE metadata Java package to use with the given logging config.")
  private String javaPackage;

  @Option(
      name = "--js_package",
      usage = "The VE metadata JS package to use with the given logging config.")
  private String jsPackage;

  @Option(
      name = "--class_name",
      usage = "The VE metadata class name to use with the given logging config.")
  private String className;

  @Option(name = "--output_file", usage = "Where to write the annotated logging config.")
  private File outputFile;

  @Option(
      name = "--java_resource_filename",
      usage =
          "The filename of the Java resource containing the VE metadata as a binary"
              + " RuntimeVeMetadata proto.")
  private String javaResourceFilename;

  SoyAnnotatedLoggingConfigGenerator(PluginLoader loader, SoyInputCache cache) {
    super(loader, cache);
  }

  private SoyAnnotatedLoggingConfigGenerator() {}

  public static void main(String[] args) {
    new SoyAnnotatedLoggingConfigGenerator().runMain(args);
  }

  @Override
  protected void compile(SoyFileSet.Builder sfsBuilder) throws IOException {
    AnnotatedLoggingConfig loggingConfig =
        sfsBuilder
            .build()
            .generateAnnotatedLoggingConfig(
                Files.asCharSource(rawLoggingConfigFile, UTF_8),
                javaPackage,
                jsPackage,
                className,
                javaResourceFilename);

    try (OutputStream output = new FileOutputStream(outputFile)) {
      loggingConfig.writeTo(output);
    }
  }

  @Override
  protected boolean requireSources() {
    return false;
  }
}
