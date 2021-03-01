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

import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.errorprone.annotations.ForOverride;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.error.SoyCompilationException;
import com.google.template.soy.logging.AnnotatedLoggingConfig;
import com.google.template.soy.logging.ValidatedLoggingConfig;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
public abstract class AbstractSoyCompiler {
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
          "The list of source Soy files (if applicable). Extra arguments are treated as srcs."
              + " Sources are typically required and read from this flag or as extra arguments.",
      handler = SoyCmdLineParser.FileListOptionHandler.class)
  List<File> srcs = new ArrayList<>();

  @Option(
      name = "--generated_files",
      usage = "A map of generated files that map back to their short name",
      handler = SoyCmdLineParser.StringStringMapHandler.class)
  private Map<String, String> generatedFiles = new HashMap<>();

  @Option(
      name = "--depHeaders",
      usage =
          "The list of dependency Soy header files (if applicable). The compiler needs deps for"
              + " analysis/checking..",
      handler = SoyCmdLineParser.FileListOptionHandler.class)
  private List<File> depHeaders = new ArrayList<>();

  @Option(
      name = "--indirectDepHeaders",
      usage =
          "Soy file headers required by deps, but which may not be used by srcs.  "
              + "Used by the compiler for typechecking and call analysis.",
      handler = SoyCmdLineParser.FileListOptionHandler.class)
  private List<File> indirectDepHeaders = new ArrayList<>();

  @Option(
      name = "--compileTimeGlobalsFile",
      aliases = "--compileTimeGlobalsFiles",
      usage =
          "The path to a file containing the mappings for global names to be substituted"
              + " at compile time. Each line of the file should have the format"
              + " \"<global_name> = <primitive_data>\" where primitive_data is a valid Soy"
              + " expression literal for a primitive type (null, boolean, integer, float, or"
              + " string). Empty lines and lines beginning with \"//\" are ignored. The file"
              + " should be encoded in UTF-8. If you need to generate a file in this format"
              + " from Java, consider using the utility"
              + " SoyUtils.generateCompileTimeGlobalsFile().",
      handler = SoyCmdLineParser.FileListOptionHandler.class)
  private List<File> globalsFiles = new ArrayList<>();

  @Option(
      name = "--pluginModules",
      usage =
          "Specifies the full class names of Guice modules for function plugins and"
              + " print directive plugins (comma-delimited list).",
      handler = SoyCmdLineParser.ModuleListOptionHandler.class)
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
      handler = SoyCmdLineParser.FileListOptionHandler.class)
  private List<File> protoFileDescriptors = new ArrayList<>();

  @Option(
      name = "--loggingConfig",
      aliases = "--loggingConfigs",
      usage = "Location of logging config protos in binary proto format. Optional.",
      handler = SoyCmdLineParser.FileListOptionHandler.class)
  private List<File> loggingConfigs = new ArrayList<>();

  @Option(
      name = "--cssMetadata",
      aliases = "--cssMetadata",
      usage =
          "List of css metadata files used to check strict deps against css dependencies and css()"
              + " calls.",
      handler = SoyCmdLineParser.FileListOptionHandler.class)
  private List<File> cssMetadata = new ArrayList<>();

  @Option(
      name = "--check_css_list",
      usage =
          "Filename for list of files to exempt from checking css() calls for classes in CSS"
              + " files.")
  private File checkCssList = null;

  @Option(
      name = "--skip_css_reference_check",
      usage = "Whether to skip the go/css-conformance#check-css-references check.")
  private boolean skipCssReferenceCheck = false;

  @Option(
      name = "--enableExperimentalFeatures",
      usage =
          "Enable experimental features that are not generally available. "
              + "These experimental features may change, break, or disappear at any time. "
              + "We make absolutely no guarantees about what may happen if you turn one of these "
              + "experiments on. Please proceed with caution at your own risk.",
      handler = SoyCmdLineParser.StringListOptionHandler.class)
  private List<String> experimentalFeatures = new ArrayList<>();

  @Option(
      name = "--disableOptimizerForTestingUseOnly",
      usage =
          "Disable optimizer in Soy compiler. Optimzer tries to simplify the Soy AST and improves "
              + "the performance in general. "
              + "This flag should only be set in integration test environment.")
  private boolean disableOptimizer = false;

  /** The remaining arguments after parsing command-line flags. */
  @Argument private List<String> arguments = new ArrayList<>();

  protected final SoyCompilerFileReader soyCompilerFileReader;

  final PluginLoader pluginLoader;
  private final SoyInputCache cache;

  protected AbstractSoyCompiler(
      PluginLoader pluginLoader, SoyInputCache cache, SoyCompilerFileReader soyCompilerFileReader) {
    this.cache = cache;
    this.pluginLoader = pluginLoader;
    this.soyCompilerFileReader = soyCompilerFileReader;
  }

  protected AbstractSoyCompiler(
      PluginLoader pluginLoader, SoyCompilerFileReader soyCompilerFileReader) {
    this(pluginLoader, SoyInputCache.DEFAULT, soyCompilerFileReader);
  }

  protected AbstractSoyCompiler(PluginLoader pluginLoader, SoyInputCache cache) {
    this(pluginLoader, cache, FileSystemSoyFileReader.INSTANCE);
  }

  protected AbstractSoyCompiler() {
    this(new PluginLoader.Default(), SoyInputCache.DEFAULT);
  }

  final void runMain(String... args) {
    int status = run(args, System.err);
    System.exit(status);
  }

  @CheckReturnValue
  public int run(final String[] args, PrintStream err) {
    try {
      doMain(args, err);
      return 0;
    } catch (SoyCompilationException compilationException) {
      err.println(formatCompilationException(compilationException));
      return 1;
    } catch (CommandLineError e) {
      e.printStackTrace(err);
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
    Stopwatch timer = Stopwatch.createStarted();
    Stopwatch guiceTimer = Stopwatch.createUnstarted();
    SoyCmdLineParser cmdLineParser = new SoyCmdLineParser(pluginLoader);
    cmdLineParser.registerFlagsObject(this);
    for (Object flagsObject : extraFlagsObjects()) {
      cmdLineParser.registerFlagsObject(flagsObject);
    }
    try {
      cmdLineParser.parseArgument(args);
    } catch (CmdLineException cle) {
      StringWriter sw = new StringWriter();
      cmdLineParser.setUsageWidth(100);
      cmdLineParser.printUsage(sw, /* resource bundle = */ null);
      exitWithError(String.format("%s\n\n%s\n%s", cle.getMessage(), usagePrefix, sw));
    }

    validateFlags();
    if (!arguments.isEmpty()) {
      exitWithError(
          "Found unexpected extra arguments passed on the command line:\n  "
              + Joiner.on(" ").join(arguments));
    }
    if (requireSources() && srcs.isEmpty()) {
      exitWithError("Must provide list of source Soy files (--srcs).");
    }

    SoyFileSet.Builder sfsBuilder = new SoyFileSet.Builder(/*ignored=*/ true);

    if (!pluginModules.isEmpty()) {
      guiceTimer.start();
      // Only create the Builder through an Injector if the user passed pluginModules.
      // Otherwise, we don't need to go through Guice at all.
      List<Module> modules = new ArrayList<>();
      modules.addAll(pluginModules);
      Injector injector;
      try {
        injector = Guice.createInjector(modules);
      } catch (Throwable t) {
        throw new CommandLineError(
            "Failed to create Guice injector.  Is there a bug in one of the modules passed to "
                + "--pluginModules?",
            t);
      }
      Optional.ofNullable(injector.getExistingBinding(new Key<Set<SoyFunction>>() {}))
          .ifPresent(b -> sfsBuilder.addSoyFunctions(b.getProvider().get()));

      Optional.ofNullable(injector.getExistingBinding(new Key<Set<SoyPrintDirective>>() {}))
          .ifPresent(b -> sfsBuilder.addSoyPrintDirectives(b.getProvider().get()));
      guiceTimer.stop();
    }
    sfsBuilder
        .addSourceFunctions(sourceFunctions)
        .setWarningSink(err)
        .setValidatedLoggingConfig(parseLoggingConfig())
        // Set experimental features that are not generally available.
        .setExperimentalFeatures(experimentalFeatures)
        .addProtoDescriptors(parseProtos(protoFileDescriptors, cache, soyCompilerFileReader, err))
        .setCompileTimeGlobals(parseGlobals())
        .setSoyAstCache(cache.astCache());

    // add sources
    for (File src : srcs) {
      try {
        // TODO(b/162524005): model genfiles in SourceFilePath directly.
        SourceFilePath normalizedPath =
            SourceFilePath.create(generatedFiles.getOrDefault(src.getPath(), src.getPath()));
        sfsBuilder.add(cache.createFileSupplier(src, normalizedPath, soyCompilerFileReader));
      } catch (FileNotFoundException fnfe) {
        throw new CommandLineError(
            "File: " + src.getPath() + " passed to --srcs does not exist", fnfe);
      }
    }
    addCompilationUnitsToBuilder(sfsBuilder);
    // Disable optimizer if the flag is set to true.
    if (disableOptimizer) {
      sfsBuilder.disableOptimizer();
    }

    compile(sfsBuilder);
    timer.stop();
    // Unless the build is faster than 1 second, issue a warning if more than half of the build is
    // constructing the guice injector.  This often happens just because the modules install too
    // much and also due to general overhead of constructing the injector.
    if (timer.elapsed().compareTo(Duration.ofSeconds(1)) > 0
        && guiceTimer.elapsed().compareTo(timer.elapsed().dividedBy(2)) > 0) {
      err.println(
          "WARNING: This compile took "
              + timer
              + " but more than 50% of that ("
              + guiceTimer
              + ") was creating a guice injector for plugins.  "
              + "Please migrate to passing plugins via the --pluginFunctions flag to improve "
              + "compiler performance."
          );
    }
  }

  @VisibleForTesting
  static List<FileDescriptor> parseProtos(
      List<File> protoFileDescriptors,
      SoyInputCache cache,
      SoyCompilerFileReader reader,
      PrintStream err) {
    SetMultimap<String, CacheLoaders.CachedDescriptorSet> protoFileToDescriptor =
        MultimapBuilder.linkedHashKeys().linkedHashSetValues().build();
    List<CacheLoaders.CachedDescriptorSet> cachedDescriptors =
        new ArrayList<>(protoFileDescriptors.size());
    for (File protoFileDescriptor : protoFileDescriptors) {
      try {
        CacheLoaders.CachedDescriptorSet cachedDescriptor =
            cache.read(protoFileDescriptor, CacheLoaders.CACHED_DESCRIPTOR_SET_LOADER, reader);
        for (String protoFileName : cachedDescriptor.getProtoFileNames()) {
          protoFileToDescriptor.put(protoFileName, cachedDescriptor);
        }
        cachedDescriptors.add(cachedDescriptor);
      } catch (IOException ioe) {
        throw new CommandLineError(
            "Error parsing proto file descriptor from "
                + protoFileDescriptor
                + ": "
                + ioe.getMessage());
      }
    }
    for (Map.Entry<String, Set<CacheLoaders.CachedDescriptorSet>> entry :
        Multimaps.asMap(protoFileToDescriptor).entrySet()) {
      if (entry.getValue().size() > 1) {
        err.println(
            "WARNING: "
                + entry.getKey()
                + " has a descriptor defined in each of these files: "
                + entry.getValue().stream()
                    .map(c -> c.getFile().getPath())
                    .sorted()
                    .collect(joining(", "))
                + ". Do your proto_library rules have overlapping sources?");
      }
    }
    List<FileDescriptor> descriptors = new ArrayList<>(protoFileToDescriptor.size());
    for (CacheLoaders.CachedDescriptorSet cachedDescriptor : cachedDescriptors) {
      try {
        descriptors.addAll(cachedDescriptor.getFileDescriptors(protoFileToDescriptor, cache));
      } catch (DescriptorValidationException e) {
        throw new CommandLineError(
            "Error parsing proto file descriptor from "
                + cachedDescriptor.getFile()
                + ": "
                + e.getMessage());
      }
    }
    return descriptors;
  }

  private void addCompilationUnitsToBuilder(SoyFileSet.Builder sfsBuilder) {
    // it isn't unusual for a file to be listed in both deps and indirect deps.  just ignore
    // duplicates
    Set<File> soFar = new HashSet<>();
    for (File depHeader : depHeaders) {
      addCompilationUnitToBuilder(sfsBuilder, depHeader, SoyFileKind.DEP, soFar);
    }
    for (File indirectDep : indirectDepHeaders) {
      addCompilationUnitToBuilder(sfsBuilder, indirectDep, SoyFileKind.INDIRECT_DEP, soFar);
    }
  }

  private void addCompilationUnitToBuilder(
      SoyFileSet.Builder sfsBuilder, File depFile, SoyFileKind depKind, Set<File> soFar) {
    if (soFar.add(depFile)) {
      try {
        sfsBuilder.addCompilationUnit(
            depKind,
            SourceFilePath.create(depFile.getPath()),
            cache.read(depFile, CacheLoaders.COMPILATION_UNIT_LOADER, soyCompilerFileReader));
      } catch (IOException e) {
        throw new CommandLineError(
            "Unable to read header file: " + depFile + ": " + e.getMessage());
      }
    }
  }

  protected Map<String, String> getGeneratedFiles() {
    return generatedFiles;
  }

  private ValidatedLoggingConfig parseLoggingConfig() {
    AnnotatedLoggingConfig.Builder configBuilder = AnnotatedLoggingConfig.newBuilder();
    for (File loggingConfig : loggingConfigs) {
      try {
        configBuilder.mergeFrom(
            cache.read(loggingConfig, CacheLoaders.LOGGING_CONFIG_LOADER, soyCompilerFileReader));
      } catch (IllegalArgumentException e) {
        throw new CommandLineError(
            "Error parsing logging config proto: " + loggingConfig + ": " + e.getMessage());
      } catch (InvalidProtocolBufferException e) {
        throw new CommandLineError(
            "Invalid logging config proto: " + loggingConfig + ": " + e.getMessage());
      } catch (IOException e) {
        throw new CommandLineError(
            "Unable to read logging config proto: " + loggingConfig + ": " + e.getMessage());
      }
    }
    return ValidatedLoggingConfig.create(configBuilder.build());
  }

  private Map<String, PrimitiveData> parseGlobals() {
    Map<String, PrimitiveData> globals = new HashMap<>();
    Map<String, File> globalsToFilePath = new HashMap<>();
    for (File globalsFile : globalsFiles) {
      try {
        ImmutableMap<String, PrimitiveData> parsedGlobals =
            cache.read(globalsFile, CacheLoaders.GLOBALS_LOADER, soyCompilerFileReader);
        for (Map.Entry<String, PrimitiveData> entry : parsedGlobals.entrySet()) {
          PrimitiveData oldValue = globals.put(entry.getKey(), entry.getValue());
          if (oldValue != null && !entry.getValue().equals(oldValue)) {
            throw new CommandLineError(
                String.format(
                    "Found 2 values for the global '%s': '%s' was provided in %s and '%s' was "
                        + "provided in %s",
                    entry.getKey(),
                    oldValue,
                    globalsToFilePath.get(entry.getKey()),
                    entry.getValue(),
                    globalsFile));
          }
          globalsToFilePath.put(entry.getKey(), globalsFile);
        }
      } catch (IOException e) {
        throw new CommandLineError(
            "Unable to soy globals file: " + globalsFile + ": " + e.getMessage());
      }
    }
    return globals;
  }

  /**
   * Extension point for subtypes to perform additional logic to validate compiler specific flags.
   */
  @ForOverride
  protected void validateFlags() {}

  /** Extension point for subclasses to disable soy sources being required. */
  @ForOverride
  boolean requireSources() {
    return true;
  }

  /** Extension point for subtypes to register extra objects containing flag definitions. */
  @ForOverride
  Iterable<?> extraFlagsObjects() {
    return ImmutableList.of();
  }

  /** Extension point to allow subclasses to format the errors from a compilation exception. */
  @ForOverride
  String formatCompilationException(SoyCompilationException sce) {
    return sce.getMessage();
  }

  /**
   * Performs the actual compilation.
   *
   * @param sfsBuilder The builder, already populated with sources, globals (if set) and plugins.
   *     subclasses may set additional compilation options on the builder.
   */
  @ForOverride
  protected abstract void compile(SoyFileSet.Builder sfsBuilder) throws IOException;

  /**
   * Prints an error message and the usage string, and then exits.
   *
   * @param errorMsg The error message to print.
   */
  protected static final RuntimeException exitWithError(String errorMsg) {
    throw new CommandLineError(errorMsg);
  }
}
