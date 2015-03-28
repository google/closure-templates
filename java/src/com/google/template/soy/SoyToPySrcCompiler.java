/*
 * Copyright 2015 Google Inc.
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
import com.google.inject.Injector;
import com.google.template.soy.MainClassUtils.Main;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.pysrc.SoyPySrcOptions;
import com.google.template.soy.shared.SoyGeneralOptions.CssHandlingScheme;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * Executable for compiling a set of Soy files into corresponding Python source files.
 *
 * <p>Note: The Python output and runtime libraries are targeted at Python v2.7. Support for Python
 * v3.1+ is also intended through the use of __future__ and version agnostic syntax, HOWEVER at the
 * moment testing support is only guaranteed for v2.7.
 *
 */
public final class SoyToPySrcCompiler {

  /** The string to prepend to the usage message. */
  private static final String USAGE_PREFIX =
      "Usage:\n"
      + "java com.google.template.soy.SoyToPySrcCompiler  \\\n"
      + "     [<flag1> <flag2> ...] --outputPathFormat <formatString>  \\\n"
      + "     --runtimePath <runtimeModulePath>  \\\n"
      + "     --srcs <soyFilePath>,... [--deps <soyFilePath>,...]\n";

  @Option(name = "--srcs",
          usage = "The list of source Soy files. Extra arguments are treated as srcs. Sources"
              + " are required from either this flag or as extra arguments.",
          handler = MainClassUtils.StringListOptionHandler.class)
  private List<String> srcs = new ArrayList<String>();

  @Option(name = "--runtimePath",
          required = true,
          usage = "[Required] The module path used to find the python runtime libraries. This"
              + " should be in dot notation format.")
  private String runtimePath = "";

  @Option(name = "--inputPrefix",
          usage = "If provided, this path prefix will be prepended to each input file path"
              + " listed on the command line. This is a literal string prefix, so you'll need"
              + " to include a trailing slash if necessary.")
  private String inputPrefix = "";

  @Option(name = "--deps",
          usage = "The list of dependency Soy files (if applicable). The compiler needs deps for"
              + " analysis/checking, but will not generate code for dep files.",
          handler = MainClassUtils.StringListOptionHandler.class)
  private List<String> deps = new ArrayList<String>();

  @Option(name = "--indirectDeps",
          usage = "Soy files required by deps, but which may not be used by srcs.",
          handler = MainClassUtils.StringListOptionHandler.class)
  private List<String> indirectDeps = new ArrayList<String>();

  @Option(name = "--outputPathFormat",
          required = true,
          usage = "[Required] A format string that specifies how to build the path to each"
                  + " output file. There will be one output Python file (UTF-8) for each input Soy"
                  + " file. The format string can include literal characters as well as the"
                  + " placeholders {INPUT_PREFIX}, {INPUT_DIRECTORY}, {INPUT_FILE_NAME}, and"
                  + " {INPUT_FILE_NAME_NO_EXT}. Additionally periods are not allowed in the"
                  + " outputted filename outside of the final py extension.")
  private String outputPathFormat = "";

  @Option(name = "--translationClass",
          usage = "The full class name of the python runtime translation class."
              + " The name should include the absolute module path and class name in dot notation"
              + " format (e.g. \"my.package.module.TranslatorClass\")."
              + " It is required for {msg} command.")
  private String translationClass = "";

  @Option(name = "--bidiIsRtlFn",
          usage = "The full name of a function used to determine if bidi is rtl for setting global"
                  + " directionality. The name should include the absolute module path and function"
                  + "name in dot notation format (e.g. \"my.app.bidi.is_rtl\"). Only applicable if"
                  + " your Soy code uses bidi functions/directives.")
  private String bidiIsRtlFn = "";

  @Option(name = "--syntaxVersion",
          usage = "User-declared syntax version for the Soy file bundle (e.g. 2.2, 2.3).")
  private String syntaxVersion = "";

  @Option(name = "--cssHandlingScheme",
          usage = "The scheme to use for handling 'css' commands. Specifying 'literal' will"
                  + " cause command text to be inserted as literal text. Specifying 'reference'"
                  + " will cause command text to be evaluated as a data or global reference."
                  + " The 'goog' scheme is not supported in Python. This option has no effect if"
                  + " the Soy code does not contain 'css' commands.")
  private String cssHandlingScheme = "literal";

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

  @Option(name = "--pluginModules",
          usage = "Specifies the full class names of Guice modules for function plugins and"
                  + " print directive plugins (comma-delimited list).")
  private String pluginModules = "";

  /** The remaining arguments after parsing command-line flags. */
  @Argument
  private List<String> arguments = new ArrayList<String>();


  /**
   * Compiles a set of Soy files into corresponding Python source files.
   *
   * @param args Should contain command-line flags and the list of paths to the Soy files.
   * @throws IOException If there are problems reading the input files or writing the output file.
   * @throws SoySyntaxException If a syntax error is detected.
   */
  public static void main(final String[] args) throws IOException, SoySyntaxException {
    MainClassUtils.run(new Main() {
      @Override
      public CompilationResult main() throws IOException {
        return new SoyToPySrcCompiler().execMain(args);
      }
    });
  }


  private SoyToPySrcCompiler() {}

  private CompilationResult execMain(String[] args) throws IOException {

    final CmdLineParser cmdLineParser = MainClassUtils.parseFlags(this, args, USAGE_PREFIX);

    final Function<String, Void> exitWithErrorFn = new Function<String, Void>() {
      @Override public Void apply(String errorMsg) {
        MainClassUtils.exitWithError(errorMsg, cmdLineParser, USAGE_PREFIX);
        return null;
      }
    };

    if (runtimePath.length() == 0) {
      MainClassUtils.exitWithError(
          "Must provide the Python runtime library path.", cmdLineParser, USAGE_PREFIX);
    }

    if (outputPathFormat.length() == 0) {
      MainClassUtils.exitWithError(
          "Must provide the output path format.", cmdLineParser, USAGE_PREFIX);
    }

    Injector injector = MainClassUtils.createInjector(pluginModules);

    // Create SoyFileSet.
    SoyFileSet.Builder sfsBuilder = injector.getInstance(SoyFileSet.Builder.class);
    MainClassUtils.addSoyFilesToBuilder(sfsBuilder, inputPrefix, srcs, arguments, deps,
        indirectDeps, exitWithErrorFn);
    if (syntaxVersion.length() > 0) {
      SyntaxVersion parsedVersion = SyntaxVersion.forName(syntaxVersion);
      if (parsedVersion.num < SyntaxVersion.V2_2.num) {
        exitWithErrorFn.apply("Declared syntax version must be 2.2 or greater.");
      }
      sfsBuilder.setDeclaredSyntaxVersionName(syntaxVersion);
    }
    // Disallow external call entirely in Python.
    sfsBuilder.setAllowExternalCalls(false);
    // Require strict templates in Python.
    sfsBuilder.setStrictAutoescapingRequired(true);
    // Setup the CSS handling scheme.
    String cssHandlingSchemeUc = cssHandlingScheme.toUpperCase();
    if (cssHandlingSchemeUc.equals("GOOG")) {
      exitWithErrorFn.apply("CSS handling scheme 'GOOG' is not support in Python.");
    }
    sfsBuilder.setCssHandlingScheme(CssHandlingScheme.valueOf(cssHandlingSchemeUc));
    if (compileTimeGlobalsFile.length() > 0) {
      sfsBuilder.setCompileTimeGlobals(new File(compileTimeGlobalsFile));
    }
    SoyFileSet sfs = sfsBuilder.build();

    // Create SoyPySrcOptions.
    SoyPySrcOptions pySrcOptions = new SoyPySrcOptions(runtimePath, bidiIsRtlFn, translationClass);

    // Compile.
    return sfs.compileToPySrcFiles(outputPathFormat, inputPrefix, pySrcOptions);
  }
}
