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
import com.google.template.soy.logging.VeMetadataGenerator;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import org.kohsuke.args4j.Option;

/**
 * Generates VE metadata files.
 *
 * <p>These files contain a constant for each VE that has metadata so that generated code can
 * reference these constants and access the VE metadata.
 */
public final class SoyVeMetadataGenerator extends AbstractSoyCompiler {

  @Option(name = "--mode", usage = "Which VE metadata type to output.")
  private VeMetadataGenerator.Mode mode;

  @Option(name = "--generator", usage = "The build label that is generating this metadata.")
  private String generator;

  @Option(
      name = "--annotated_logging_config_file",
      usage = "The annotated logging config file with the metadata.")
  private File annotatedLoggingConfigFile;

  @Option(name = "--output_file", usage = "Where to write the VE metadata file.")
  private File outputFile;

  @Option(
      name = "--resource_output_file",
      usage = "Where to write the VE resource file (if the this Mode has a resource file).")
  private File resourceOutputFile;

  SoyVeMetadataGenerator(PluginLoader loader, SoyInputCache cache) {
    super(loader, cache);
  }

  private SoyVeMetadataGenerator() {}

  public static void main(String[] args) {
    new SoyVeMetadataGenerator().runMain(args);
  }

  @Override
  protected void compile(SoyFileSet.Builder sfsBuilder) throws IOException {
    sfsBuilder
        .build()
        .generateAndWriteVeMetadata(
            mode,
            Files.asByteSource(annotatedLoggingConfigFile),
            generator,
            Files.asCharSink(outputFile, UTF_8),
            Optional.ofNullable(resourceOutputFile).map(Files::asByteSink));
  }

  @Override
  protected boolean requireSources() {
    return false;
  }
}
