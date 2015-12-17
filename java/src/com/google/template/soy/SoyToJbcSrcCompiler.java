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
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSink;
import com.google.common.io.Files;
import com.google.inject.Injector;
import com.google.template.soy.MainClassUtils.Main;
import com.google.template.soy.base.SoySyntaxException;

import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Executable for compiling a set of Soy files into corresponding Java class files in a jar.
 */
public final class SoyToJbcSrcCompiler {

  /** The string to prepend to the usage message. */
  private static final String USAGE_PREFIX =
      "Usage:\n"
          + "java com.google.template.soy.SoyToJbcSrcCompiler  \\\n"
          + "     [<flag1> <flag2> ...] --jar <jarName>  \\\n"
          + "     --srcs <soyFilePath>,... [--deps <soyFilePath>,...]\n";

  @Option(
    name = "--srcs",
    usage =
        "The list of source Soy files. Extra arguments are treated as srcs. Sources"
            + " are required from either this flag or as extra arguments.",
    handler = MainClassUtils.StringListOptionHandler.class
  )
  private List<String> srcs = new ArrayList<>();

  @Option(
    name = "--deps",
    usage =
        "The list of dependency Soy files (if applicable). The compiler needs deps for"
            + " analysis/checking, but will not generate code for dep files.",
    handler = MainClassUtils.StringListOptionHandler.class
  )
  private List<String> deps = new ArrayList<>();

  @Option(
    name = "--indirectDeps",
    usage = "Soy files required by deps, but which may not be used by srcs.",
    handler = MainClassUtils.StringListOptionHandler.class
  )
  private List<String> indirectDeps = new ArrayList<>();

  @Option(
    name = "--output",
    required = true,
    usage =
        "[Required] The file name of the JAR file to be written.  Each compiler"
            + " invocation will produce exactly one file"
  )
  private String output = "";

  @Option(
    name = "--outputSrcJar",
    required = false,
    usage =
        "[Optional] The file name of the JAR containing sources to be written.  Each compiler"
            + " invocation will produce exactly one such file.  This may be useful for enabling"
            + "IDE debugging scenarios"
  )
  private String outputSrcJar = "";

  @Option(
    name = "--globals_file",
    usage =
        "The path to a file containing the mappings for global names to be substituted"
            + " at compile time. Each line of the file should have the format"
            + " \"<global_name> = <primitive_data>\" where primitive_data is a valid Soy"
            + " expression literal for a primitive type (null, boolean, integer, float, or"
            + " string). Empty lines and lines beginning with \"//\" are ignored. The file"
            + " should be encoded in UTF-8. If you need to generate a file in this format"
            + " from Java, consider using the utility"
            + " SoyUtils.generateCompileTimeGlobalsFile()."
  )
  private String globalsFile = "";

  @Option(
    name = "--pluginModules",
    usage =
        "Specifies the full class names of Guice modules for function plugins and"
            + " print directive plugins (comma-delimited list)."
  )
  private String pluginModules = "";

  /**
   * Compiles a set of Soy files into corresponding Java class files.
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
            new SoyToJbcSrcCompiler().execMain(args);
          }
        });
  }

  private SoyToJbcSrcCompiler() {}

  private void execMain(String[] args) throws IOException {
    final CmdLineParser cmdLineParser = MainClassUtils.parseFlags(this, args, USAGE_PREFIX);

    final Function<String, Void> exitWithErrorFn =
        new Function<String, Void>() {
          @Override
          public Void apply(String errorMsg) {
            MainClassUtils.exitWithError(errorMsg, cmdLineParser, USAGE_PREFIX);
            return null;
          }
        };

    if (output.length() == 0) {
      MainClassUtils.exitWithError(
          "Must provide a JAR file name (--jar <file name>).", cmdLineParser, USAGE_PREFIX);
    }

    Injector injector = MainClassUtils.createInjectorForPlugins(pluginModules);

    // Create SoyFileSet.
    SoyFileSet.Builder sfsBuilder = injector.getInstance(SoyFileSet.Builder.class);
    MainClassUtils.addSoyFilesToBuilder(
        sfsBuilder,
        "", // we don't support an input prefix, all srcs should be fully qualified
        srcs,
        ImmutableSet.<String>of(), // old style srcs, not supported by this compiler
        deps,
        indirectDeps,
        exitWithErrorFn);
    // Disallow external call entirely in JbcSrc.  JbcSrc needs callee information to generate
    // correct escaping code.
    sfsBuilder.setAllowExternalCalls(false);
    if (globalsFile.length() > 0) {
      sfsBuilder.setCompileTimeGlobals(new File(globalsFile));
    }
    SoyFileSet sfs = sfsBuilder.build();
    Optional<ByteSink> srcJarSink = Optional.absent();
    if (!outputSrcJar.isEmpty()) {
      srcJarSink = Optional.of(Files.asByteSink(new File(outputSrcJar)));
    }
    sfs.compileToJar(Files.asByteSink(new File(output)), srcJarSink);
  }
}
