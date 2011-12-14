/*
 * Copyright 2009 Google Inc.
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

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.inject.Injector;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.javasrc.SoyJavaSrcOptions;
import com.google.template.soy.javasrc.SoyJavaSrcOptions.CodeStyle;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.SoyMsgBundleHandler;
import com.google.template.soy.shared.SoyGeneralOptions.CssHandlingScheme;
import com.google.template.soy.xliffmsgplugin.XliffMsgPluginModule;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.util.List;


/**
 * Experimental Soy to Java Src compiler.
 *
 * <p> Warning: The Java Src backend is experimental (repetitive, untested, undocumented).
 *
 */
public class SoyToJavaSrcCompilerExperimental {

  private SoyToJavaSrcCompilerExperimental() {}


  /** The string to prepend to the usage message. */
  private static final String USAGE_PREFIX =
      "Usage:\n" +
      "java com.google.template.soy.SoyToJavaSrcCompilerExperimental  \\\n" +
      "     [<flag1> <flag2> ...] --outputPath <outputPath>  \\\n" +
      "     <soyFile1> <soyFile2> ...\n";


  @Option(name = "--inputPrefix",
          usage = "If provided, this path prefix will be prepended to each input file path" +
                  " listed on the command line. This is a literal string prefix, so you'll need" +
                  " to include a trailing slash if necessary.")
  private String inputPrefix = "";

  @Option(name = "--outputPath",
          usage = "The path to the output file (if exists, will be overwritten).")
  private String outputPath = "";

  @Option(name = "--codeStyle",
          usage = "The code style to use when generating Java code ('stringbuilder' or 'concat').")
  private CodeStyle codeStyle = CodeStyle.STRINGBUILDER;

  @Option(name = "--messageFilePath",
          usage = "The path to the translated messages file. If not provided, messages from the" +
                  " Soy source are used.")
  private String messageFilePath = "";

  @Option(name = "--bidiGlobalDir",
          usage = "The bidi global directionality (ltr=1, rtl=-1). Only applicable if your Soy" +
                  " code uses bidi functions/directives. Also note that this flag is usually not" +
                  " necessary if a message file is provided, because by default the bidi global" +
                  " directionality is simply inferred from the message file.")
  private int bidiGlobalDir = 0;

  @Option(name = "--cssHandlingScheme",
          usage = "The scheme to use for handling 'css' commands. Specifying 'literal' will" +
                  " cause command text to be inserted as literal text. Specifying 'reference'" +
                  " will cause command text to be evaluated as a data or global reference. This" +
                  " option has no effect if the Soy code does not contain 'css' commands.")
  private String cssHandlingScheme = "literal";

  @Option(name = "--compileTimeGlobalsFile",
          usage = "The path to a file containing the mappings for global names to be substituted" +
                  " at compile time. Each line of the file should have the format" +
                  " \"<global_name> = <primitive_data>\" where primitive_data is a valid Soy" +
                  " expression literal for a primitive type (null, boolean, integer, float, or" +
                  " string). Empty lines and lines beginning with \"//\" are ignored. The file" +
                  " should be encoded in UTF-8. If you need to generate a file in this format" +
                  " from Java, consider using the utility" +
                  " SoyUtils.generateCompileTimeGlobalsFile().")
  private String compileTimeGlobalsFile = "";

  @Option(name = "--messagePluginModule",
          usage = "Specifies the full class name of a Guice module that binds a SoyMsgPlugin." +
                  " If not specified, the default is" +
                  " com.google.template.soy.xliffmsgplugin.XliffMsgPluginModule, which binds" +
                  " the XliffMsgPlugin.")
  private String messagePluginModule = XliffMsgPluginModule.class.getName();

  @Option(name = "--pluginModules",
          usage = "Specifies the full class names of Guice modules for function plugins and" +
                  " print directive plugins (comma-delimited list).")
  private String pluginModules = "";

  /** The remaining arguments after parsing command-line flags. */
  @Argument
  private List<String> arguments = Lists.newArrayList();


  public static void main(String[] args) throws IOException, SoySyntaxException {
    (new SoyToJavaSrcCompilerExperimental()).execMain(args);
  }


  private void execMain(String[] args) throws IOException, SoySyntaxException {

    CmdLineParser cmdLineParser = MainClassUtils.parseFlags(this, args, USAGE_PREFIX);
    if (arguments.size() == 0) {
      MainClassUtils.exitWithError("Must provide list of Soy files.", cmdLineParser, USAGE_PREFIX);
    }

    Injector injector = MainClassUtils.createInjector(messagePluginModule, pluginModules);

    // Create SoyJavaSrcOptions.
    SoyJavaSrcOptions javaSrcOptions = new SoyJavaSrcOptions();
    javaSrcOptions.setCodeStyle(codeStyle);
    javaSrcOptions.setBidiGlobalDir(bidiGlobalDir);

    // Create SoyFileSet.
    SoyFileSet.Builder sfsBuilder = injector.getInstance(SoyFileSet.Builder.class);
    for (String arg : arguments) {
      sfsBuilder.add(new File(inputPrefix + arg));
    }
    sfsBuilder.setCssHandlingScheme(CssHandlingScheme.valueOf(cssHandlingScheme.toUpperCase()));
    if (compileTimeGlobalsFile.length() > 0) {
      sfsBuilder.setCompileTimeGlobals(new File(compileTimeGlobalsFile));
    }
    SoyFileSet sfs = sfsBuilder.build();

    // Create SoyMsgBundle.
    SoyMsgBundle msgBundle = null;
    if (messageFilePath.length() > 0) {
      SoyMsgBundleHandler msgBundleHandler = injector.getInstance(SoyMsgBundleHandler.class);
      msgBundle = msgBundleHandler.createFromFile(new File(messageFilePath));
    }

    // Compile.
    String generatedCode = sfs.compileToJavaSrc(javaSrcOptions, msgBundle);

    // Output.
    if (outputPath.length() > 0) {
      Files.write(generatedCode, new File(outputPath), Charsets.UTF_8);
    } else {
      System.out.print(generatedCode);
    }
  }

}
