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
import com.google.inject.Module;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.xliffmsgplugin.XliffMsgPluginModule;
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
    name = "--outputPathFormat",
    required = true,
    usage =
        "[Required] A format string that specifies how to build the path to each"
            + " output file. If not generating localized JS, then there will be one output"
            + " JS file (UTF-8) for each input Soy file. If generating localized JS, then"
            + " there will be one output JS file for each combination of input Soy file and"
            + " locale. The format string can include literal characters as well as the"
            + " placeholders {INPUT_PREFIX}, {INPUT_DIRECTORY}, {INPUT_FILE_NAME},"
            + " {INPUT_FILE_NAME_NO_EXT}, {LOCALE}, {LOCALE_LOWER_CASE}. Note"
            + " {LOCALE_LOWER_CASE} also turns dash into underscore, e.g. pt-BR becomes"
            + " pt_br."
  )
  protected String outputPathFormat;

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
    name = "--syntaxVersion",
    usage = "User-declared syntax version for the Soy file bundle (e.g. 2.0, 2.3)."
  )
  private String syntaxVersion = "";

  @Option(
    name = "--shouldGenerateJsdoc",
    usage =
        "Whether we should generate JSDoc with type info for the Closure Compiler."
            + " Note the generated JSDoc does not have description text, only types for the"
            + " benefit of the Closure Compiler."
  )
  private boolean shouldGenerateJsdoc = false;

  @Option(
    name = "--shouldProvideRequireSoyNamespaces",
    usage =
        "When this option is used, each generated JS file will contain (a) one single"
            + " goog.provide statement for the corresponding Soy file's namespace and"
            + " (b) goog.require statements for the namespaces of the called templates."
  )
  private boolean shouldProvideRequireSoyNamespaces = false;

  @Option(
    name = "--shouldDeclareTopLevelNamespaces",
    usage =
        "[Only applicable when generating regular JS code to define namespaces (i.e."
            + " not generating goog.provide/goog.require).] When this option is set to"
            + " false, each generated JS file will not attempt to declare the top-level"
            + " name in its namespace, instead assuming the top-level name is already"
            + " declared in the global scope. E.g. for namespace aaa.bbb, the code will not"
            + " attempt to declare aaa, but will still define aaa.bbb if it's not already"
            + " defined."
  )
  private boolean shouldDeclareTopLevelNamespaces = true;

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
        "[Only applicable if both --shouldGenerateGoogMsgDefs and"
            +
            " --shouldProvideRequireSoyNamespaces"
            +
            " is true]"
            + " Whether to determine the bidi global direction at template runtime by"
            + " evaluating (goog.i18n.bidi.IS_RTL). Do not combine with --bidiGlobalDir."
  )
  private boolean useGoogIsRtlForBidiGlobalDir = false;

  @Option(
    name = "--messagePluginModule",
    usage =
        "Specifies the full class name of a Guice module that binds a SoyMsgPlugin."
            + " If not specified, the default is"
            + " com.google.template.soy.xliffmsgplugin.XliffMsgPluginModule, which binds"
            + " the XliffMsgPlugin."
  )
  private Module messagePluginModule = new XliffMsgPluginModule();

  SoyToJsSrcCompiler(ClassLoader loader) {
    super(loader);
  }

  SoyToJsSrcCompiler() {}

  /**
   * Compiles a set of Soy files into corresponding JS source files.
   *
   * @param args Should contain command-line flags and the list of paths to the Soy files.
   * @throws IOException If there are problems reading the input files or writing the output file.
   * @throws SoySyntaxException If a syntax error is detected.
   */
  public static void main(final String[] args) throws IOException {
    new SoyToJsSrcCompiler().runMain(args);
  }

  @Override
  void validateFlags() {
    if (outputPathFormat.isEmpty()) {
      exitWithError("Must provide the output path format.");
    }
  }

  @Override
  Optional<Module> msgPluginModule() {
    return Optional.of(messagePluginModule);
  }

  @Override
  void compile(SoyFileSet.Builder sfsBuilder) throws IOException {
    if (!syntaxVersion.isEmpty()) {
      sfsBuilder.setDeclaredSyntaxVersionName(syntaxVersion);
    }
    sfsBuilder.setAllowExternalCalls(allowExternalCalls);

    SoyFileSet sfs = sfsBuilder.build();

    // Create SoyJsSrcOptions.
    SoyJsSrcOptions jsSrcOptions = new SoyJsSrcOptions();
    jsSrcOptions.setShouldGenerateJsdoc(shouldGenerateJsdoc);
    jsSrcOptions.setShouldProvideRequireSoyNamespaces(shouldProvideRequireSoyNamespaces);
    jsSrcOptions.setShouldDeclareTopLevelNamespaces(shouldDeclareTopLevelNamespaces);
    jsSrcOptions.setShouldGenerateGoogMsgDefs(shouldGenerateGoogMsgDefs);
    jsSrcOptions.setGoogMsgsAreExternal(googMsgsAreExternal);
    jsSrcOptions.setBidiGlobalDir(bidiGlobalDir);
    jsSrcOptions.setUseGoogIsRtlForBidiGlobalDir(useGoogIsRtlForBidiGlobalDir);

    // Compile.
    boolean generateLocalizedJs = !locales.isEmpty();
    if (generateLocalizedJs) {
      sfs.compileToJsSrcFiles(
          outputPathFormat, inputPrefix, jsSrcOptions, locales, messageFilePathFormat);
    } else {
      sfs.compileToJsSrcFiles(outputPathFormat, inputPrefix, jsSrcOptions, locales, null);
    }
  }
}
