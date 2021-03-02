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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.SoyMsgBundleHandler;
import com.google.template.soy.msgs.SoyMsgPlugin;
import com.google.template.soy.shared.internal.MainEntryPointUtils;
import com.google.template.soy.xliffmsgplugin.XliffMsgPlugin;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.args4j.Option;

/**
 * Executable for compiling a set of Soy files into corresponding JS source files.
 *
 */
public final class SoyToJsSrcCompiler extends AbstractSoyCompiler {

  @Option(
    name = "--locales",
    usage =
        "[Required for generating localized JS] Comma-delimited list of locales for"
            + " which to generate localized JS. There will be one output JS file for each"
            + " combination of input Soy file and locale.",
    handler = SoyCmdLineParser.StringListOptionHandler.class
  )
  private List<String> locales = new ArrayList<>();

  @Option(
    name = "--messageFilePathFormat",
    usage =
        "[Required for generating localized JS] A format string that specifies how to"
            + " build the path to each translated messages file. The format string can"
            + " include literal characters as well as the placeholders {INPUT_PREFIX},"
            + " {LOCALE}, and {LOCALE_LOWER_CASE}. Note {LOCALE_LOWER_CASE} also turns dash"
            + " into underscore, e.g. pt-BR becomes pt_br. The format string must end with"
            + " an extension matching the message file format (case-insensitive)."
  )
  private String messageFilePathFormat = "";

  @Option(
    name = "--shouldGenerateGoogMsgDefs",
    usage =
        "When this option is used, all 'msg' blocks will be turned into goog.getMsg"
            + " definitions and corresponding usages. Must be used with either"
            + " --bidiGlobalDir, or --useGoogIsRtlForBidiGlobalDir, usually the latter."
            + " Also see --googMsgsAreExternal."
  )
  private boolean shouldGenerateGoogMsgDefs = false;

  @Option(
    name = "--googMsgsAreExternal",
    usage =
        "[Only applicable if --shouldGenerateGoogMsgDefs is true]"
            + " If this option is true, then we generate"
            + " \"var MSG_EXTERNAL_<soyGeneratedMsgId> = goog.getMsg(...);\"."
            + " If this option is false, then we generate"
            + " \"var MSG_UNNAMED_<uniquefier> = goog.getMsg(...);\"."
            + "  [Explanation of true value]"
            + " Set this option to true if your project is having Closure Templates do"
            + " message extraction (e.g. with SoyMsgExtractor) and then having the Closure"
            + " Compiler do translated message insertion."
            + "  [Explanation of false value]"
            + " Set this option to false if your project is having the Closure Compiler do"
            + " all of its localization, i.e. if you want the Closure Compiler to do both"
            + " message extraction and translated message insertion. A significant drawback"
            + " to this setup is that, if your templates are used from both JS and Java, you"
            + " will end up with two separate and possibly different sets of translations"
            + " for your messages."
  )
  private boolean googMsgsAreExternal = false;

  @Option(
    name = "--bidiGlobalDir",
    usage =
        "The bidi global directionality (ltr=1, rtl=-1). Only applicable if your Soy"
            + " code uses bidi functions/directives. Also note that this flag is usually not"
            + " necessary if a message file is provided, because by default the bidi global"
            + " directionality is simply inferred from the message file."
  )
  private int bidiGlobalDir = 0;

  @Option(
    name = "--useGoogIsRtlForBidiGlobalDir",
    usage =
        "[Only applicable if both --shouldGenerateGoogMsgDefs is true]"
            + " Whether to determine the bidi global direction at template runtime by"
            + " evaluating (goog.i18n.bidi.IS_RTL). Do not combine with --bidiGlobalDir."
  )
  private boolean useGoogIsRtlForBidiGlobalDir = false;

  @Option(
    name = "--messagePlugin",
    usage =
        "Specifies the full class name of a SoyMsgPlugin. If not specified, the default is"
            + " com.google.template.soy.xliffmsgplugin.XliffMsgPlugin. "
  )
  private SoyMsgPlugin messagePlugin = new XliffMsgPlugin();

  private final PerInputOutputFiles outputFiles =
      new PerInputOutputFiles("soy.js", PerInputOutputFiles.JS_JOINER);

  SoyToJsSrcCompiler(PluginLoader loader, SoyInputCache cache) {
    super(loader, cache);
  }

  SoyToJsSrcCompiler() {}

  /**
   * Compiles a set of Soy files into corresponding JS source files.
   *
   * @param args Should contain command-line flags and the list of paths to the Soy files.
   * @throws IOException If there are problems reading the input files or writing the output file.
   */
  public static void main(final String[] args) throws IOException {
    new SoyToJsSrcCompiler().runMain(args);
  }

  @Override
  protected void validateFlags() {
    outputFiles.validateFlags();
  }

  @Override
  Iterable<?> extraFlagsObjects() {
    return ImmutableList.of(outputFiles);
  }

  @Override
  protected void compile(SoyFileSet.Builder sfsBuilder) throws IOException {
    SoyFileSet sfs = sfsBuilder.build();

    // Create SoyJsSrcOptions.
    SoyJsSrcOptions jsSrcOptions = new SoyJsSrcOptions();
    jsSrcOptions.setShouldGenerateGoogMsgDefs(shouldGenerateGoogMsgDefs);
    jsSrcOptions.setGoogMsgsAreExternal(googMsgsAreExternal);
    jsSrcOptions.setBidiGlobalDir(bidiGlobalDir);
    jsSrcOptions.setUseGoogIsRtlForBidiGlobalDir(useGoogIsRtlForBidiGlobalDir);

    // Compile.
    boolean generateLocalizedJs = !locales.isEmpty();
    if (generateLocalizedJs) {
      for (String locale : locales) {
        String msgFilePath =
            MainEntryPointUtils.buildFilePath(
                messageFilePathFormat, locale, /*inputFilePath=*/ null);

        SoyMsgBundle msgBundle =
            new SoyMsgBundleHandler(messagePlugin).createFromFile(new File(msgFilePath));
        if (msgBundle.getLocaleString() == null) {
          // TODO: Remove this check (but make sure no projects depend on this behavior).
          // There was an error reading the message file. We continue processing only if the locale
          // begins with "en", because falling back to the Soy source will probably be fine.
          if (!locale.startsWith("en")) {
            throw new IOException("Error opening or reading message file " + msgFilePath);
          }
        }
        outputFiles.writeFiles(srcs, sfs.compileToJsSrcInternal(jsSrcOptions, msgBundle), locale);
      }
    } else {
      outputFiles.writeFiles(
          srcs, sfs.compileToJsSrcInternal(jsSrcOptions, /*msgBundle=*/ null), /*locale=*/ null);
    }
  }
}
