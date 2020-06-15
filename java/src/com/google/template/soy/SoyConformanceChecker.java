/*
 * Copyright 2018 Google Inc.
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

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.google.template.soy.conformance.ConformanceConfig;
import com.google.template.soy.conformance.ValidatedConformanceConfig;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.args4j.Option;

/** Executable that enforces soy conformance rules against a set of soy source files. */
public final class SoyConformanceChecker extends AbstractSoyCompiler {
  @Option(
      name = "--conformanceConfig",
      aliases = "--conformanceConfigs",
      usage = "Location of conformance config protos in text proto format.",
      handler = SoyCmdLineParser.FileListOptionHandler.class)
  private List<File> conformanceConfigs = new ArrayList<>();

  SoyConformanceChecker(PluginLoader loader, SoyInputCache cache) {
    super(loader, cache);
  }

  SoyConformanceChecker() {}

  public static void main(final String[] args) throws IOException {
    new SoyConformanceChecker().runMain(args);
  }

  @Override
  protected void validateFlags() {
    if (conformanceConfigs.isEmpty()) {
      exitWithError("Must set --conformanceConfig");
    }
  }

  @Override
  protected void compile(SoyFileSet.Builder sfsBuilder) {
    ValidatedConformanceConfig conformanceConfig = parseConformanceConfig();
    sfsBuilder.setConformanceConfig(conformanceConfig).build().checkConformance();
  }

  private ValidatedConformanceConfig parseConformanceConfig() {
    ValidatedConformanceConfig config = ValidatedConformanceConfig.EMPTY;
    for (File conformanceConfig : conformanceConfigs) {
      try (InputStreamReader stream =
          new InputStreamReader(new FileInputStream(conformanceConfig), StandardCharsets.UTF_8)) {
        ConformanceConfig.Builder builder = ConformanceConfig.newBuilder();
        TextFormat.getParser().merge(stream, builder);
        config = config.concat(ValidatedConformanceConfig.create(builder.build()));
      } catch (IllegalArgumentException e) {
        throw new CommandLineError(
            "Error parsing conformance proto: " + conformanceConfig + ": " + e.getMessage());
      } catch (InvalidProtocolBufferException e) {
        throw new CommandLineError(
            "Invalid conformance proto: " + conformanceConfig + ": " + e.getMessage());
      } catch (IOException e) {
        throw new CommandLineError(
            "Unable to read conformance proto: " + conformanceConfig + ": " + e.getMessage());
      }
    }
    return config;
  }
}
