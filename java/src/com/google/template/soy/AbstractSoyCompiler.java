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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.errorprone.annotations.ForOverride;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.template.soy.MainClassUtils.Main;
import com.google.template.soy.msgs.SoyMsgPlugin;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.CheckReturnValue;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/**
 * Base class for the Soy Compilers.
 *
 * <p>Defines common flags and performs shared initialization routines.
 *
 * <p>TODO(lukes): make all compilers subclass this type and move all MainClassUtils logic into this
 * class.
 */
abstract class AbstractSoyCompiler {
  /** The string to prepend to the usage message. */
  private final String usagePrefix =
      "Usage:\n"
          + "java "
          + getClass().getName()
          + " \\\n"
          + "     [<flag1> <flag2> ...] --jar <jarName>  \\\n"
          + "     --srcs <soyFilePath>,... [--deps <soyFilePath>,...]\n";

  private final Function<String, Void> exitWithErrorFn =
      new Function<String, Void>() {
        @Override
        public Void apply(String errorMsg) {
          exitWithError(errorMsg);
          return null;
        }
      };

  @Option(
    name = "--inputPrefix",
    usage =
        "If provided, this path prefix will be prepended to each input file path"
            + " listed on the command line. This is a literal string prefix, so you'll need"
            + " to include a trailing slash if necessary."
  )
  protected String inputPrefix = "";

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
    name = "--compileTimeGlobalsFile",
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
  private File globalsFile = null;

  @Option(
    name = "--pluginModules",
    usage =
        "Specifies the full class names of Guice modules for function plugins and"
            + " print directive plugins (comma-delimited list).",
    handler = MainClassUtils.ModuleListOptionHandler.class
  )
  private List<Module> pluginModules = new ArrayList<>();

  @Option(
    name = "--protoFileDescriptors",
    usage =
        "Location of protocol buffer definitions in the form of a file descriptor set."
            + "The compiler needs defs for parameter type checking and generating direct "
            + "access support for proto types.",
    handler = MainClassUtils.FileListOptionHandler.class
  )
  private static final List<File> protoFileDescriptors = new ArrayList<>();

  @Option(
    name = "--enableExperimentalFeatures",
    usage =
        "Enable experimental features that are not generally available. "
            + "These experimental features may change, break, or disappear at any time. "
            + "We make absolutely no guarantees about what may happen if you turn one of these "
            + "experiments on. Please proceed with caution at your own risk.",
    handler = MainClassUtils.StringListOptionHandler.class
  )
  private static final List<String> experimentalFeatures = new ArrayList<>();

  /** The remaining arguments after parsing command-line flags. */
  @Argument private final List<String> arguments = new ArrayList<>();

  private CmdLineParser cmdLineParser;

  final void runMain(String... args) {
    int status = run(args);
    System.exit(status);
  }

  @VisibleForTesting
  @CheckReturnValue
  int run(final String... args) {
    // TODO(lukes): inline this method once all mains have been migrated to this base class.
    return MainClassUtils.runInternal(
        new Main() {
          @Override
          public void main() throws IOException {
            doMain(args);
          }
        });
  }

  private void doMain(String[] args) throws IOException {
    this.cmdLineParser = MainClassUtils.parseFlags(this, args, usagePrefix);

    validateFlags();
    if (!arguments.isEmpty() && !acceptsSourcesAsArguments()) {
      exitWithError("Found old style sources passed on the command line, use --srcs=... instead");
    }

    List<Module> modules = new ArrayList<>();
    modules.addAll(pluginModules);
    modules.addAll(msgPluginModule().asSet());
    Injector injector = MainClassUtils.createInjector(modules);
    SoyFileSet.Builder sfsBuilder = injector.getInstance(SoyFileSet.Builder.class);
    if (!protoFileDescriptors.isEmpty()) {
      sfsBuilder.addProtoDescriptorsFromFiles(protoFileDescriptors);
    }
    MainClassUtils.addSoyFilesToBuilder(
        sfsBuilder, inputPrefix, srcs, arguments, deps, indirectDeps, exitWithErrorFn);
    if (globalsFile != null) {
      sfsBuilder.setCompileTimeGlobals(globalsFile);
    }
    // Set experimental features that are not generally available.
    sfsBuilder.setExperimentalFeatures(experimentalFeatures);
    compile(sfsBuilder, injector);
  }

  /**
   * Returns {@code true} if old style sources should be supported. {@code true} is the default.
   *
   * <p>Old style srcs are for when source files are passed as arguments directly, rather than
   * passed to the {@code --srcs} flag.
   *
   * <p>TODO(lukes): eliminate support for old-style srcs
   */
  @ForOverride
  boolean acceptsSourcesAsArguments() {
    return true;
  }

  /**
   * Returns an additional plugin module to support the {@link SoyMsgPlugin}. This is only neccesary
   * if the compiler needs to perform msg extraction.
   */
  @ForOverride
  Optional<Module> msgPluginModule() {
    return Optional.absent();
  }

  /**
   * Extension point for subtypes to perform additional logic to validate compiler specific flags.
   */
  @ForOverride
  void validateFlags() {}

  /**
   * Performs the actual compilation.
   *
   * @param sfsBuilder The builder, already populated with sources, globals (if set) and plugins.
   *     subclasses may set additional compilation options on the builder.
   * @param injector The injector
   * @throws IOException
   */
  @ForOverride
  void compile(SoyFileSet.Builder sfsBuilder, Injector injector) throws IOException {
    compile(sfsBuilder);
  }

  /**
   * Performs the actual compilation.
   *
   * @param sfsBuilder The builder, already populated with sources, globals (if set) and plugins.
   *     subclasses may set additional compilation options on the builder.
   * @throws IOException
   */
  @ForOverride
  void compile(SoyFileSet.Builder sfsBuilder) throws IOException {
    throw new AbstractMethodError("must override at least one overload of compile()");
  }

  /**
   * Prints an error message and the usage string, and then exits.
   *
   * @param errorMsg The error message to print.
   */
  final RuntimeException exitWithError(String errorMsg) {
    return MainClassUtils.exitWithError(errorMsg, cmdLineParser, usagePrefix);
  }
}
