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

package com.google.template.soy;

import com.google.common.base.Optional;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.SoyMsgBundleHandler;
import com.google.template.soy.msgs.SoyMsgBundleHandler.OutputFileOptions;
import com.google.template.soy.xliffmsgplugin.XliffMsgPluginModule;
import java.io.File;
import java.io.IOException;
import org.kohsuke.args4j.Option;

/**
 * Executable for extracting messages from a set of Soy files into an output messages file.
 *
 * <p>The command-line arguments should contain command-line flags and the list of paths to the Soy
 * files.
 *
 */
public final class SoyMsgExtractor extends AbstractSoyCompiler {

  @Option(
    name = "--allowExternalCalls",
    usage =
        "Whether to allow external calls. New projects should set this to false, and"
            + " existing projects should remove existing external calls and then set this"
            + " to false. It will save you a lot of headaches. Currently defaults to true"
            + " for backward compatibility."
  )
  private boolean allowExternalCalls = true;

  @Option(
    name = "--outputFile",
    required = true,
    usage =
        "The path to the output file to write. If a file already"
            + " exists at this location, it will be overwritten. The file extension must"
            + " match the output format requested."
  )
  private File outputFile;

  @Option(
    name = "--sourceLocaleString",
    usage = "The locale string of the source language (default 'en')."
  )
  private String sourceLocaleString = "en";

  @Option(
    name = "--targetLocaleString",
    usage =
        "The locale string of the target language (default empty). If empty, then the"
            + " output messages file will not specify a target locale string. Note that this"
            + " option may not be applicable for certain message plugins (in which case this"
            + " value will be ignored by the message plugin)."
  )
  private String targetLocaleString = "";

  @Option(
    name = "--messagePluginModule",
    usage =
        "Specifies the full class name of a Guice module that binds a SoyMsgPlugin."
            + " If not specified, the default is"
            + " com.google.template.soy.xliffmsgplugin.XliffMsgPluginModule, which binds"
            + " the XliffMsgPlugin."
  )
  private Module messagePluginModule = new XliffMsgPluginModule();

  /**
   * Extracts messages from a set of Soy files into an output messages file.
   *
   * @param args Should contain command-line flags and the list of paths to the Soy files.
   * @throws IOException If there are problems reading the input files or writing the output file.
   * @throws com.google.template.soy.base.SoySyntaxException If a syntax error is detected.
   */
  public static void main(String... args) throws IOException {
    new SoyMsgExtractor().runMain(args);
  }

  @Override
  Optional<Module> msgPluginModule() {
    return Optional.of(messagePluginModule);
  }

  @Override
  void compile(SoyFileSet.Builder sfsBuilder, Injector injector) throws IOException {
    sfsBuilder.setAllowExternalCalls(allowExternalCalls);
    SoyFileSet sfs = sfsBuilder.build();

    SoyMsgBundle msgBundle = sfs.extractMsgs();
    OutputFileOptions options = new OutputFileOptions();
    options.setSourceLocaleString(sourceLocaleString);
    if (targetLocaleString.length() > 0) {
      options.setTargetLocaleString(targetLocaleString);
    }
    injector
        .getInstance(SoyMsgBundleHandler.class)
        .writeToExtractedMsgsFile(msgBundle, options, outputFile);
  }
}
