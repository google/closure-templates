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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Injector;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.SoyMsgBundleHandler;
import com.google.template.soy.msgs.SoyMsgBundleHandler.OutputFileOptions;
import com.google.template.soy.xliffmsgplugin.XliffMsgPluginModule;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.util.List;


/**
 * Executable for extracting messages from a set of Soy files into an output messages file.
 *
 * <p> The command-line arguments should contain command-line flags and the list of paths to the
 * Soy files.
 *
 * @author Kai Huang
 */
public final class SoyMsgExtractor {


  /** The string to prepend to the usage message. */
  private static final String USAGE_PREFIX =
      "Usage:\n" +
      "java com.google.template.soy.SoyMsgExtractor  \\\n" +
      "     [<flag1> <flag2> ...] --outputFile <path>  \\\n" +
      "     --srcs <soyFilePath>,...\n";


  @Option(name = "--inputPrefix",
          usage = "If provided, this path prefix will be prepended to each input file path" +
                  " listed on the command line. This is a literal string prefix, so you'll need" +
                  " to include a trailing slash if necessary.")
  private String inputPrefix = "";

  @Option(name = "--srcs",
          usage = "[Required] The list of source Soy files.",
          handler = MainClassUtils.StringListOptionHandler.class)
  private List<String> srcs = Lists.newArrayList();

  @Option(name = "--allowExternalCalls",
          usage = "Whether to allow external calls. New projects should set this to false, and" +
                  " existing projects should remove existing external calls and then set this" +
                  " to false. It will save you a lot of headaches. Currently defaults to true" +
                  " for backward compatibility.",
          handler = MainClassUtils.BooleanOptionHandler.class)
  private boolean allowExternalCalls = true;

  @Option(name = "--outputFile",
          usage = "The path to the output file to write. If a file already" +
                  " exists at this location, it will be overwritten. The file extension must" +
                  " match the output format requested.")
  private String outputFile = "";

  @Option(name = "--outputPathFormat",
          usage = "A format string that specifies how to build the path to each" +
                  " output file. The format string can include literal characters as well as the" +
                  " placeholders {INPUT_PREFIX}, {INPUT_DIRECTORY}, {INPUT_FILE_NAME}," +
                  " {INPUT_FILE_NAME_NO_EXT}")
  private String outputPathFormat = "";

  @Option(name = "--sourceLocaleString",
          usage = "The locale string of the source language (default 'en').")
  private String sourceLocaleString = "en";

  @Option(name = "--targetLocaleString",
          usage = "The locale string of the target language (default empty). If empty, then the" +
                  " output messages file will not specify a target locale string. Note that this" +
                  " option may not be applicable for certain message plugins (in which case this" +
                  " value will be ignored by the message plugin).")
  private String targetLocaleString = "";

  @Option(name = "--messagePluginModule",
          usage = "Specifies the full class name of a Guice module that binds a SoyMsgPlugin." +
                  " If not specified, the default is" +
                  " com.google.template.soy.xliffmsgplugin.XliffMsgPluginModule, which binds" +
                  " the XliffMsgPlugin.")
  private String messagePluginModule = XliffMsgPluginModule.class.getName();

  /** The remaining arguments after parsing command-line flags. */
  @Argument
  private List<String> arguments = Lists.newArrayList();


  /**
   * Extracts messages from a set of Soy files into an output messages file.
   *
   * @param args Should contain command-line flags and the list of paths to the Soy files.
   * @throws IOException If there are problems reading the input files or writing the output file.
   * @throws SoySyntaxException If a syntax error is detected.
   */
  public static void main(String... args) throws IOException {
    (new SoyMsgExtractor()).execMain(args);
  }


  private SoyMsgExtractor() {}


  private void execMain(String[] args) throws IOException, SoySyntaxException {

    final CmdLineParser cmdLineParser = MainClassUtils.parseFlags(this, args, USAGE_PREFIX);

    final Function<String, Void> exitWithErrorFn = new Function<String, Void>() {
      @Override public Void apply(String errorMsg) {
        MainClassUtils.exitWithError(errorMsg, cmdLineParser, USAGE_PREFIX);
        return null;
      }
    };

    Injector injector = MainClassUtils.createInjector(messagePluginModule, null);

    SoyFileSet.Builder sfsBuilder = injector.getInstance(SoyFileSet.Builder.class);
    MainClassUtils.addSoyFilesToBuilder(
        sfsBuilder, inputPrefix, srcs, arguments, ImmutableList.<String>of(), exitWithErrorFn);
    sfsBuilder.setAllowExternalCalls(allowExternalCalls);

    File outputFile0;
    if (outputPathFormat.length() != 0) {
      if (outputFile.length() != 0) {
        exitWithErrorFn.apply("Must provide one of output file path or output path format.");
      }
      String outputFilePath = outputPathFormat;

      String inputFilePath = inputPrefix + (srcs.size() != 0 ? srcs.get(0) : arguments.get(0));
      // Compute directory and file name.
      int lastSlashIndex = inputFilePath.lastIndexOf(File.separatorChar);
      String directory = inputFilePath.substring(0, lastSlashIndex + 1);
      String fileName = inputFilePath.substring(lastSlashIndex + 1);

      // Compute file name without extension.
      int lastDotIndex = fileName.lastIndexOf('.');
      if (lastDotIndex == -1) {
        lastDotIndex = fileName.length();
      }
      String fileNameNoExt = fileName.substring(0, lastDotIndex);

      outputFilePath = outputFilePath.replace("{INPUT_PREFIX}", inputPrefix);
      outputFilePath = outputFilePath.replace("{INPUT_DIRECTORY}", directory);
      outputFilePath = outputFilePath.replace("{INPUT_FILE_NAME}", fileName);
      outputFilePath = outputFilePath.replace("{INPUT_FILE_NAME_NO_EXT}", fileNameNoExt);
      outputFile0 = new File(outputFilePath);
    } else if (outputFile.length() != 0) {
      outputFile0 = new File(outputFile);
    } else {
      exitWithErrorFn.apply("Must provide output file path or output path format.");
      return;
    }

    SoyFileSet sfs = sfsBuilder.build();

    SoyMsgBundle msgBundle = sfs.extractMsgs();

    SoyMsgBundleHandler msgBundleHandler = injector.getInstance(SoyMsgBundleHandler.class);
    OutputFileOptions options = new OutputFileOptions();
    options.setSourceLocaleString(sourceLocaleString);
    if (targetLocaleString.length() > 0) {
      options.setTargetLocaleString(targetLocaleString);
    }
    msgBundleHandler.writeToFile(msgBundle, options, outputFile0);
  }

}
