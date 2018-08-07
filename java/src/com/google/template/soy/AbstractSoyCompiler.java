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
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.ForOverride;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.conformance.ConformanceConfig;
import com.google.template.soy.conformance.ValidatedConformanceConfig;
import com.google.template.soy.error.SoyCompilationException;
import com.google.template.soy.logging.LoggingConfig;
import com.google.template.soy.logging.ValidatedLoggingConfig;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.CheckReturnValue;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;

/**
 * Base class for the Soy Compilers.
 *
 * <p>Defines common flags and performs shared initialization routines.
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

  @Option(
    name = "--srcs",
    usage =
        "The list of source Soy files. Extra arguments are treated as srcs. Sources"
            + " are required from either this flag or as extra arguments.",
    handler = SoyCmdLineParser.StringListOptionHandler.class
  )
  private List<String> srcs = new ArrayList<>();

  @Option(
    name = "--deps",
    usage =
        "The list of dependency Soy files (if applicable). The compiler needs deps for"
            + " analysis/checking, but will not generate code for dep files.",
    handler = SoyCmdLineParser.StringListOptionHandler.class
  )
  private List<String> deps = new ArrayList<>();

  @Option(
    name = "--indirectDeps",
    usage = "Soy files required by deps, but which may not be used by srcs.",
    handler = SoyCmdLineParser.StringListOptionHandler.class
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
    handler = SoyCmdLineParser.ModuleListOptionHandler.class
  )
  private List<Module> pluginModules = new ArrayList<>();

  @Option(
      name = "--pluginFunctions",
      usage = "Specifies the full class names of SoySourceFunction plugins (comma-delimited list).",
      handler = SoyCmdLineParser.SourceFunctionListOptionHandler.class)
  private List<SoySourceFunction> sourceFunctions = new ArrayList<>();

  @Option(
    name = "--protoFileDescriptors",
    usage =
        "Location of protocol buffer definitions in the form of a file descriptor set."
            + "The compiler needs defs for parameter type checking and generating direct "
            + "access support for proto types.",
    handler = SoyCmdLineParser.FileListOptionHandler.class
  )
  private final List<File> protoFileDescriptors = new ArrayList<>();

  @Option(
    name = "--conformanceConfig",
    usage = "Location of conformance config protos in binary proto format."
  )
  private File conformanceConfig = null;

  @Option(
    name = "--loggingConfig",
    usage = "Location of logging config protos in binary proto format. Optional."
  )
  private File loggingConfig = null;

  @Option(
    name = "--enableExperimentalFeatures",
    usage =
        "Enable experimental features that are not generally available. "
            + "These experimental features may change, break, or disappear at any time. "
            + "We make absolutely no guarantees about what may happen if you turn one of these "
            + "experiments on. Please proceed with caution at your own risk.",
    handler = SoyCmdLineParser.StringListOptionHandler.class
  )
  private final List<String> experimentalFeatures = new ArrayList<>();

  @Option(
    name = "--disableOptimizerForTestingUseOnly",
    usage =
        "Disable optimizer in Soy compiler. Optimzer tries to simplify the Soy AST and improves "
            + "the performance in general. "
            + "This flag should only be set in integration test environment."
  )
  private boolean disableOptimizer = false;

  /** The remaining arguments after parsing command-line flags. */
  @Argument private final List<String> arguments = new ArrayList<>();

  private final SoyCompilerFileReader soyCompilerFileReader;

  final ClassLoader pluginClassLoader;

  AbstractSoyCompiler(ClassLoader pluginClassLoader, SoyCompilerFileReader soyCompilerFileReader) {
    this.pluginClassLoader = pluginClassLoader;
    this.soyCompilerFileReader = soyCompilerFileReader;
  }

  AbstractSoyCompiler(ClassLoader pluginClassLoader) {
    this(pluginClassLoader, new FileSystemSoyFileReader());
  }

  AbstractSoyCompiler() {
    this(AbstractSoyCompiler.class.getClassLoader());
  }

  final void runMain(String... args) {
    int status = run(args, System.err);
    System.exit(status);
  }

  @VisibleForTesting
  @CheckReturnValue
  int run(final String[] args, PrintStream err) {
    try {
      doMain(args, err);
      return 0;
    } catch (SoyCompilationException compilationException) {
      err.println(compilationException.getMessage());
      return 1;
    } catch (CommandLineError e) {
      err.println(e.getMessage());
      return 1;
    } catch (Throwable e) {
      err.println(
          "INTERNAL SOY ERROR.\n"
              + "Please open an issue at "
              + "https://github.com/google/closure-templates/issues"
              + " with this stack trace and repro steps"
          );
      e.printStackTrace(err);
      return 1;
    }
  }

  private void doMain(String[] args, PrintStream err) throws IOException {
    SoyCmdLineParser cmdLineParser = new SoyCmdLineParser(this, pluginClassLoader);
    try {
      cmdLineParser.parseArgument(args);
    } catch (CmdLineException cle) {
      StringWriter sw = new StringWriter();
      cmdLineParser.setUsageWidth(100);
      cmdLineParser.printUsage(sw, /* resource bundle = */ null);
      exitWithError(String.format("%s\n\n%s\n%s", cle.getMessage(), usagePrefix, sw.toString()));
    }

    validateFlags();
    if (!arguments.isEmpty()) {
      exitWithError(
          "Found extra arguments passed on the command line. If these are sources, use --srcs=..."
              + " instead.");
    }
    if (srcs.isEmpty()) {
      exitWithError("Must provide list of source Soy files (--srcs).");
    }

    SoyFileSet.Builder sfsBuilder;
    if (!pluginModules.isEmpty()) {
      // Only create the Builder through an Injector if the user passed pluginModules.
      // Otherwise, we don't need to go through Guice at all.
      List<Module> modules = new ArrayList<>();
      modules.add(new SoyModule());
      modules.addAll(pluginModules);
      Injector injector = Guice.createInjector(modules);
      sfsBuilder = injector.getInstance(SoyFileSet.Builder.class);
    } else {
      sfsBuilder = SoyFileSet.builder();
    }
    ValidatedConformanceConfig conformanceConfig = parseConformanceConfig();
    sfsBuilder
        .addSourceFunctions(sourceFunctions)
        .setWarningSink(err)
        .setConformanceConfig(conformanceConfig)
        .setValidatedLoggingConfig(parseLoggingConfig())
        // Set experimental features that are not generally available.
        .setExperimentalFeatures(experimentalFeatures);

    for (File protoFileDescriptor : protoFileDescriptors) {
      try {
        sfsBuilder.addProtoDescriptorsFromFile(protoFileDescriptor);
      } catch (IOException ioe) {
        throw new CommandLineError(
            "Error parsing proto file descriptor from "
                + protoFileDescriptor
                + ": "
                + ioe.getMessage());
      }
    }
    addSoyFilesToBuilder(sfsBuilder, ImmutableSet.copyOf(srcs), deps, indirectDeps);
    if (globalsFile != null) {
      sfsBuilder.setCompileTimeGlobals(globalsFile);
    }
    // Disable optimizer if the flag is set to true.
    if (disableOptimizer) {
      sfsBuilder.disableOptimizer();
    }
    compile(sfsBuilder);
  }

  private ValidatedConformanceConfig parseConformanceConfig() {
    if (conformanceConfig != null) {
      try (InputStream stream = new FileInputStream(conformanceConfig)) {
        return ValidatedConformanceConfig.create(ConformanceConfig.parseFrom(stream));
      } catch (IllegalArgumentException e) {
        throw new CommandLineError(
            "Error parsing conformance proto: " + conformanceConfig + ": " + e.getMessage());
      } catch (InvalidProtocolBufferException e) {
        throw new CommandLineError(
            "Invalid conformance proto: " + conformanceConfig + ": " + e.getMessage());
      } catch (IOException e) {
        throw new CommandLineError(
            "Unable to read conformance proto: " + conformanceConfig + ": " + e.getMessage());
      }
    } else {
      return ValidatedConformanceConfig.EMPTY;
    }
  }

  private ValidatedLoggingConfig parseLoggingConfig() {
    if (loggingConfig != null) {
      try (InputStream stream = new FileInputStream(loggingConfig)) {
        return ValidatedLoggingConfig.create(LoggingConfig.parseFrom(stream));
      } catch (IllegalArgumentException e) {
        throw new CommandLineError(
            "Error parsing logging config proto: " + loggingConfig + ": " + e.getMessage());
      } catch (InvalidProtocolBufferException e) {
        throw new CommandLineError(
            "Invalid conformance proto: " + loggingConfig + ": " + e.getMessage());
      } catch (IOException e) {
        throw new CommandLineError(
            "Unable to read conformance proto: " + loggingConfig + ": " + e.getMessage());
      }
    } else {
      return ValidatedLoggingConfig.EMPTY;
    }
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
  static final RuntimeException exitWithError(String errorMsg) {
    throw new CommandLineError("Error: " + errorMsg);
  }

  /**
   * Helper to add srcs and deps Soy files to a SoyFileSet builder. Also does sanity checks.
   *
   * @param sfsBuilder The SoyFileSet builder to add to.
   * @param srcs The srcs from the --srcs flag. Exactly one of 'srcs' and 'args' must be nonempty.
   * @param deps The deps from the --deps flag, or empty list if not applicable.
   * @param indirectDeps The deps from the --indirectDeps flag, or empty list if not applicable.
   */
  private void addSoyFilesToBuilder(
      SoyFileSet.Builder sfsBuilder,
      Collection<String> srcs,
      Collection<String> deps,
      Collection<String> indirectDeps) {
    // TODO(lukes): make it an error for there to be duplicates within any collection or between
    // srcs and deps/indirect deps.  It is ok for a file to be both a dep and an indirect dep
    // Use set of all the files seen so far, so we don't add the same file multiple times (which is
    // an error in SoyFileSet).  Do it in this order, so that the if a file is both a src and a dep
    // we will treat it as a src.
    Set<String> soFar = new HashSet<>();
    addAllIfNotPresent(sfsBuilder, SoyFileKind.SRC, srcs, soFar);
    addAllIfNotPresent(sfsBuilder, SoyFileKind.DEP, deps, soFar);
    addAllIfNotPresent(sfsBuilder, SoyFileKind.INDIRECT_DEP, indirectDeps, soFar);
  }

  private void addAllIfNotPresent(
      SoyFileSet.Builder builder, SoyFileKind kind, Collection<String> files, Set<String> soFar) {
    for (String file : files) {
      if (soFar.add(file)) {
        builder.addWithKind(
            soyCompilerFileReader.read(file).asCharSource(StandardCharsets.UTF_8), kind, file);
      }
    }
  }
}
