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

import com.google.common.io.Files;
import com.google.template.soy.msgs.SoyMsgBundleHandler;
import com.google.template.soy.msgs.SoyMsgBundleHandler.OutputFileOptions;
import com.google.template.soy.msgs.SoyMsgPlugin;
import com.google.template.soy.xliffmsgplugin.XliffMsgPlugin;
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
    name = "--messagePlugin",
    usage =
        "Specifies the full class name of a SoyMsgPlugin.  If not specified, the default is "
            + "com.google.template.soy.xliffmsgplugin.XliffMsgPlugin."
  )
  private SoyMsgPlugin messagePlugin = new XliffMsgPlugin();

  /**
   * Extracts messages from a set of Soy files into an output messages file.
   *
   * @param args Should contain command-line flags and the list of paths to the Soy files.
   * @throws IOException If there are problems reading the input files or writing the output file.
   */
  public static void main(String... args) throws IOException {
    new SoyMsgExtractor().runMain(args);
  }

  SoyMsgExtractor(PluginLoader loader, SoyInputCache cache) {
    super(loader, cache);
  }

  SoyMsgExtractor() {}

  @Override
  protected void compile(SoyFileSet.Builder sfsBuilder) throws IOException {
    SoyFileSet sfs = sfsBuilder.build();

    OutputFileOptions options = new OutputFileOptions();
    options.setSourceLocaleString(sourceLocaleString);
    if (targetLocaleString.length() > 0) {
      options.setTargetLocaleString(targetLocaleString);
    }
    sfs.extractAndWriteMsgs(
        new SoyMsgBundleHandler(messagePlugin), options, Files.asByteSink(outputFile));
  }
}
