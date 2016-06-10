/*
 * Copyright 2016 Google Inc.
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


import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.inject.Injector;
import com.google.template.soy.MainClassUtils.Main;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.xliffmsgplugin.XliffMsgPluginModule;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * Executable for compiling a set of Soy files into corresponding Incremental DOM source files. This
 * generates JavaScript functions for templates with function calls describing the template content.
 * In order to use the generated code, you will need to provide the Incremental DOM library at
 * runtime.
 *
 * @see <a href="http://google.github.io/incremental-dom">docs</a>
 * @see <a href="https://github.com/google/incremental-dom">Github page</a>
 */
@Beta
public final class SoyToIncrementalDomSrcCompiler {

  /** The string to prepend to the usage message. */
  private static final String USAGE_PREFIX = "Usage:\n"
      + "java com.google.template.soy.SoyToIncrementalDomSrcCompiler  \\\n"
      + "     [<flag1> <flag2> ...] --outputPathFormat <formatString>  \\\n"
      + "     --srcs <soyFilePath>,... [--deps <soyFilePath>,...]\n";

  @Option(name = "--inputPrefix",
          usage = "If provided, this path prefix will be prepended to each input file path"
              + " listed on the command line. This is a literal string prefix, so you'll need"
              + " to include a trailing slash if necessary.")
  private String inputPrefix = "";

  @Option(name = "--srcs",
          usage = "[Required] The list of source Soy files.",
          handler = MainClassUtils.StringListOptionHandler.class)
  private List<String> srcs = new ArrayList<>();

  @Option(name = "--deps",
          usage = "The list of dependency Soy files (if applicable). The compiler needs deps for"
              + " analysis/checking, but will not generate code for dep files.",
          handler = MainClassUtils.StringListOptionHandler.class)
  private List<String> deps = new ArrayList<>();

  @Option(name = "--indirectDeps",
          usage = "Soy files required by deps, but which may not be used by srcs.",
          handler = MainClassUtils.StringListOptionHandler.class)
  private List<String> indirectDeps = new ArrayList<>();

  @Option(name = "--outputPathFormat",
          required = true,
          usage = "[Required] A format string that specifies how to build the path to each"
              + " output file. If not generating localized JS, then there will be one output"
              + " JS file (UTF-8) for each input Soy file. If generating localized JS, then"
              + " there will be one output JS file for each combination of input Soy file and"
              + " locale. The format string can include literal characters as well as the"
              + " placeholders {INPUT_PREFIX}, {INPUT_DIRECTORY}, {INPUT_FILE_NAME},"
              + " {INPUT_FILE_NAME_NO_EXT}, {LOCALE}, {LOCALE_LOWER_CASE}. Note"
              + " {LOCALE_LOWER_CASE} also turns dash into underscore, e.g. pt-BR becomes"
              + " pt_br.")
  private String outputPathFormat = "";

  @Option(name = "--compileTimeGlobalsFile",
          usage = "The path to a file containing the mappings for global names to be substituted"
              + " at compile time. Each line of the file should have the format"
              + " \"<global_name> = <primitive_data>\" where primitive_data is a valid Soy"
              + " expression literal for a primitive type (null, boolean, integer, float, or"
              + " string). Empty lines and lines beginning with \"//\" are ignored. The file"
              + " should be encoded in UTF-8. If you need to generate a file in this format"
              + " from Java, consider using the utility"
              + " SoyUtils.generateCompileTimeGlobalsFile().")
  private String compileTimeGlobalsFile = "";

  @Option(name = "--messagePluginModule",
          usage = "Specifies the full class name of a Guice module that binds a SoyMsgPlugin."
              + " If not specified, the default is"
              + " com.google.template.soy.xliffmsgplugin.XliffMsgPluginModule, which binds"
              + " the XliffMsgPlugin.")
  private String messagePluginModule = XliffMsgPluginModule.class.getName();

  @Option(name = "--pluginModules",
          usage = "Specifies the full class names of Guice modules for function plugins and"
              + " print directive plugins (comma-delimited list).")
  private String pluginModules = "";

  /** The remaining arguments after parsing command-line flags. */
  @Argument
  private List<String> arguments = new ArrayList<>();


   /**
   * Compiles a set of Soy files into corresponding Incremental DOM source files.
   *
   * @param args Should contain command-line flags and the list of paths to the Soy files.
   * @throws IOException If there are problems reading the input files or writing the output file.
   * @throws SoySyntaxException If a syntax error is detected.
   */
  public static void main(final String[] args) throws IOException, SoySyntaxException {
    MainClassUtils.run(
        new Main() {
          @Override
          public void main() throws IOException {
            new SoyToIncrementalDomSrcCompiler().execMain(args);
          }
        });
  }

  private SoyToIncrementalDomSrcCompiler() {}

  private void execMain(String[] args) throws IOException {
    final CmdLineParser cmdLineParser = MainClassUtils.parseFlags(this, args, USAGE_PREFIX);

    final Function<String, Void> exitWithErrorFn = new Function<String, Void>() {
      @Override public Void apply(String errorMsg) {
        MainClassUtils.exitWithError(errorMsg, cmdLineParser, USAGE_PREFIX);
        return null;
      }
    };

    if (outputPathFormat.isEmpty()) {
      exitWithErrorFn.apply("Must provide the output path format.");
    }

    Injector injector = MainClassUtils.createInjector(messagePluginModule, pluginModules);

    // Create SoyFileSet.
    SoyFileSet.Builder sfsBuilder = injector.getInstance(SoyFileSet.Builder.class);
    MainClassUtils.addSoyFilesToBuilder(
        sfsBuilder, inputPrefix, srcs, arguments, deps, indirectDeps, exitWithErrorFn);
    sfsBuilder.setAllowExternalCalls(false);
    if (!compileTimeGlobalsFile.isEmpty()) {
      sfsBuilder.setCompileTimeGlobals(new File(compileTimeGlobalsFile));
    }
    SoyFileSet sfs = sfsBuilder.build();

    // Create SoyJsSrcOptions.
    SoyJsSrcOptions jsSrcOptions = new SoyJsSrcOptions();
    jsSrcOptions.setShouldAllowDeprecatedSyntax(false);
    jsSrcOptions.setShouldProvideRequireSoyNamespaces(false);
    jsSrcOptions.setShouldProvideRequireJsFunctions(false);
    jsSrcOptions.setShouldProvideBothSoyNamespacesAndJsFunctions(false);
    jsSrcOptions.setShouldDeclareTopLevelNamespaces(false);
    jsSrcOptions.setShouldGenerateJsdoc(true);
    // Only goog.module generation supported
    jsSrcOptions.setShouldGenerateGoogModules(true);
    jsSrcOptions.setShouldGenerateGoogMsgDefs(true);
    jsSrcOptions.setGoogMsgsAreExternal(true);
    jsSrcOptions.setBidiGlobalDir(0);
    jsSrcOptions.setUseGoogIsRtlForBidiGlobalDir(true);

    sfs.compileToIncrementalDomSrcFiles(outputPathFormat, jsSrcOptions);
  }
}
