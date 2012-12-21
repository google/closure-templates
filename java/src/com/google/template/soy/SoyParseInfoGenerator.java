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
import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.base.SoySyntaxException;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;


/**
 * Executable for generating Java classes containing Soy parse info.
 *
 * <p> The command-line arguments should contain command-line flags and the list of paths to the
 * Soy files.
 *
 * @author Kai Huang
 */
public final class SoyParseInfoGenerator {


  /** The string to prepend to the usage message. */
  private static final String USAGE_PREFIX =
      "Usage:\n" +
      "java com.google.template.soy.SoyParseInfoGenerator  \\\n" +
      "     [<flag1> <flag2> ...] --outputDirectory <path>  \\\n" +
      "     --javaPackage <package> --javaClassNameSource <source>  \\\n" +
      "     --srcs <soyFilePath>,... [--deps <soyFilePath>,...]\n";


  @Option(name = "--inputPrefix",
          usage = "If provided, this path prefix will be prepended to each input file path" +
                  " listed on the command line. This is a literal string prefix, so you'll need" +
                  " to include a trailing slash if necessary.")
  private String inputPrefix = "";

  @Option(name = "--srcs",
          usage = "[Required] The list of source Soy files.",
          handler = MainClassUtils.StringListOptionHandler.class)
  private List<String> srcs = Lists.newArrayList();

  @Option(name = "--deps",
          usage = "The list of dependency Soy files (if applicable). The compiler needs deps for" +
                  " analysis/checking, but will not generate code for dep files.",
          handler = MainClassUtils.StringListOptionHandler.class)
  private List<String> deps = Lists.newArrayList();

  @Option(name = "--allowExternalCalls",
          usage = "Whether to allow external calls. New projects should set this to false, and" +
                  " existing projects should remove existing external calls and then set this" +
                  " to false. It will save you a lot of headaches. Currently defaults to true" +
                  " for backward compatibility.",
          handler = MainClassUtils.BooleanOptionHandler.class)
  private boolean allowExternalCalls = true;

  @Option(name = "--outputDirectory",
          required = true,
          usage = "[Required] The path to the output directory. If files with the same names" +
                  " already exist at this location, they will be overwritten.")
  private String outputDirectory = "";

  @Option(name = "--javaPackage",
          required = true,
          usage = "[Required] The Java package name to use for the generated classes.")
  private String javaPackage = "";

  @Option(name = "--javaClassNameSource",
          required = true,
          usage = "[Required] The source for the generated class names. Valid values are" +
                  " \"filename\", \"namespace\", and \"generic\". Option \"filename\" turns" +
                  " a Soy file name AaaBbb.soy or aaa_bbb.soy into AaaBbbSoyInfo. Option" +
                  " \"namespace\" turns a namespace aaa.bbb.cccDdd into CccDddSoyInfo (note" +
                  " it only uses the last part of the namespace). Option \"generic\" generates" +
                  " class names such as File1SoyInfo, File2SoyInfo.")
  private String javaClassNameSource = "";

  /** The remaining arguments after parsing command-line flags. */
  @Argument
  private List<String> arguments = Lists.newArrayList();


  /**
   * Generates Java classes containing Soy parse info.
   *
   * @param args Should contain command-line flags and the list of paths to the Soy files.
   * @throws IOException If there are problems reading the input files or writing the output file.
   * @throws SoySyntaxException If a syntax error is detected.
   */
  public static void main(String[] args) throws IOException {
    (new SoyParseInfoGenerator()).execMain(args);
  }


  private SoyParseInfoGenerator() {}


  private void execMain(String[] args) throws IOException, SoySyntaxException {

    final CmdLineParser cmdLineParser = MainClassUtils.parseFlags(this, args, USAGE_PREFIX);

    final Function<String, Void> exitWithErrorFn = new Function<String, Void>() {
      @Override public Void apply(String errorMsg) {
        MainClassUtils.exitWithError(errorMsg, cmdLineParser, USAGE_PREFIX);
        return null;
      }
    };

    if (outputDirectory.length() == 0) {
      MainClassUtils.exitWithError("Must provide output directory.", cmdLineParser, USAGE_PREFIX);
    }
    if (javaPackage.length() == 0) {
      MainClassUtils.exitWithError("Must provide Java package.", cmdLineParser, USAGE_PREFIX);
    }
    if (javaClassNameSource.length() == 0) {
      MainClassUtils.exitWithError(
          "Must provide Java class name source.", cmdLineParser, USAGE_PREFIX);
    }

    Injector injector = Guice.createInjector(new SoyModule());

    SoyFileSet.Builder sfsBuilder = injector.getInstance(SoyFileSet.Builder.class);
    // ImmutableSet.copyOf() removes any duplicate filenames that were provided.
    // This is so the builder doesn't get confused by multiple identical
    // definitions.
    // TODO: This does not seem like the right place to remove duplicates.
    MainClassUtils.addSoyFilesToBuilder(
        sfsBuilder, inputPrefix, ImmutableSet.copyOf(srcs), ImmutableSet.copyOf(arguments),
        ImmutableSet.copyOf(deps), exitWithErrorFn);
    sfsBuilder.setAllowExternalCalls(allowExternalCalls);
    SoyFileSet sfs = sfsBuilder.build();

    Map<String, String> generatedFiles =
        sfs.generateParseInfo(javaPackage, javaClassNameSource);

    for (Map.Entry<String, String> entry : generatedFiles.entrySet()) {
      File outputFile = new File(outputDirectory, entry.getKey());
      BaseUtils.ensureDirsExistInPath(outputFile.getPath());
      Files.write(entry.getValue(), outputFile, Charsets.UTF_8);
    }
  }

}
