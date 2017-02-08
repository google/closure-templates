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


import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.template.soy.SoyFileSet.Builder;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.error.SoyCompilationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

/**
 * Utilities for classes with a {@code main()} method.
 *
 */
final class MainClassUtils {

  /**
   * Represents a top-level entry point into the Soy codebase. Used by {@link #run} to catch
   * unexpected exceptions and print errors.
   */
  interface Main {
    void main() throws IOException, SoyCompilationException;
  }

  private MainClassUtils() {}

  // NOTE: all the OptionHandler types need to be public with public constructors so args4j can use
  // them.

  /**
   * OptionHandler for args4j that handles a boolean.
   *
   * <p>The difference between this handler and the default boolean option handler supplied by
   * args4j is that the default one doesn't take any param, so can only be used to turn on boolean
   * flags, but never to turn them off. This implementation allows an optional param value
   * true/false/1/0 so that the user can turn on or off the flag.
   */
  public static final class BooleanOptionHandler extends OptionHandler<Boolean> {

    /** {@link OptionHandler#OptionHandler(CmdLineParser,OptionDef,Setter)} */
    public BooleanOptionHandler(
        CmdLineParser parser, OptionDef option, Setter<? super Boolean> setter) {
      super(parser, option, setter);
    }

    @Override
    public int parseArguments(Parameters params) throws CmdLineException {

      boolean value;
      boolean hasParam;
      try {
        String nextArg = params.getParameter(0);
        if (nextArg.equalsIgnoreCase("true") || nextArg.equals("1")) {
          value = true;
          hasParam = true;
        } else if (nextArg.equalsIgnoreCase("false") || nextArg.equals("0")) {
          value = false;
          hasParam = true;
        } else {
          // Next arg is not a param for this flag. No param means set flag to true.
          value = true;
          hasParam = false;
        }
      } catch (CmdLineException e) {
        // No additional args on command line. No param means set flag to true.
        value = true;
        hasParam = false;
      }

      setter.addValue(value);
      return hasParam ? 1 : 0;
    }

    @Override
    public String getDefaultMetaVariable() {
      return null;
    }
  }

  /** OptionHandler for args4j that handles a comma-delimited list. */
  abstract static class ListOptionHandler<T> extends OptionHandler<T> {

    /** {@link OptionHandler#OptionHandler(CmdLineParser,OptionDef,Setter)} */
    ListOptionHandler(CmdLineParser parser, OptionDef option, Setter<? super T> setter) {
      super(parser, option, setter);
    }

    /**
     * Parses one item from the list into the appropriate type.
     *
     * @param item One item from the list.
     * @return The object representation of the item.
     */
    abstract T parseItem(String item);

    @Override
    public int parseArguments(Parameters params) throws CmdLineException {
      String parameter = params.getParameter(0);
      // An empty string should be an empty list, not a list containing the empty item
      if (!parameter.isEmpty()) {
        for (String item : parameter.split(",")) {
          setter.addValue(parseItem(item));
        }
      }
      return 1;
    }

    @Override
    public String getDefaultMetaVariable() {
      return "ITEM,ITEM,...";
    }
  }

  /** OptionHandler for args4j that handles a comma-delimited list of strings. */
  public static final class StringListOptionHandler extends ListOptionHandler<String> {

    /** {@link ListOptionHandler#ListOptionHandler(CmdLineParser,OptionDef,Setter)} */
    public StringListOptionHandler(
        CmdLineParser parser, OptionDef option, Setter<? super String> setter) {
      super(parser, option, setter);
    }

    @Override
    String parseItem(String item) {
      return item;
    }
  }

  /** OptionHandler for args4j that handles a comma-delimited list of guice modules. */
  public static final class ModuleListOptionHandler extends ListOptionHandler<Module> {

    /** {@link ListOptionHandler#ListOptionHandler(CmdLineParser,OptionDef,Setter)} */
    public ModuleListOptionHandler(
        CmdLineParser parser, OptionDef option, Setter<? super Module> setter) {
      super(parser, option, setter);
    }

    @Override
    Module parseItem(String item) {
      return instantiatePluginModule(item);
    }
  }
  /** OptionHandler for args4j that handles a comma-delimited list of files. */
  public static final class FileListOptionHandler extends ListOptionHandler<File> {

    /** {@link ListOptionHandler#ListOptionHandler(CmdLineParser,OptionDef,Setter)} */
    public FileListOptionHandler(
        CmdLineParser parser, OptionDef option, Setter<? super File> setter) {
      super(parser, option, setter);
    }

    @Override
    File parseItem(String item) {
      return new File(item);
    }
  }
  /** OptionHandler for args4j that handles a comma-delimited list of strings. */
  public static final class ModuleOptionHandler extends OptionHandler<Module> {
    /** {@link ListOptionHandler#ListOptionHandler(CmdLineParser,OptionDef,Setter)} */
    public ModuleOptionHandler(
        CmdLineParser parser, OptionDef option, Setter<? super Module> setter) {
      super(parser, option, setter);
    }

    @Override
    public int parseArguments(Parameters params) throws CmdLineException {
      String parameter = params.getParameter(0);
      // An empty string should be null
      if (parameter.isEmpty()) {
        setter.addValue(null);
      } else {
        setter.addValue(instantiatePluginModule(parameter));
      }
      return 1;
    }

    @Override
    public String getDefaultMetaVariable() {
      return "com.foo.bar.BazModule";
    }
  }

  /**
   * Parses command line flags written with args4j.
   *
   * @param objWithFlags An instance of a class containing args4j flag definitions.
   * @param args The args string to parse.
   * @param usagePrefix The string to prepend to the usage message (when reporting an error).
   * @return The CmdLineParser that was created and used to parse the args (can be used to print
   *     usage text for flags when reporting errors).
   */
  static CmdLineParser parseFlags(Object objWithFlags, String[] args, String usagePrefix) {
    CmdLineParser.registerHandler(Module.class, ModuleOptionHandler.class);
    // overwrite the built in boolean handler
    CmdLineParser.registerHandler(Boolean.class, BooleanOptionHandler.class);
    CmdLineParser.registerHandler(boolean.class, BooleanOptionHandler.class);

    CmdLineParser cmdLineParser = new CmdLineParser(objWithFlags);
    cmdLineParser.setUsageWidth(100);

    try {
      cmdLineParser.parseArgument(args);

    } catch (CmdLineException cle) {
      exitWithError(cle.getMessage(), cmdLineParser, usagePrefix);
    }

    return cmdLineParser;
  }

  @VisibleForTesting
  static int runInternal(Main method) {
    try {
      method.main();
      return 0;
    } catch (SoyCompilationException compilationException) {
      System.err.println(compilationException.getMessage());
      return 1;
    } catch (Exception e) {
      System.err.println(
          "INTERNAL SOY ERROR.\n"
              + "Please open an issue at "
              + "https://github.com/google/closure-templates/issues"
              + " with this stack trace and repro steps"
          );
      e.printStackTrace(System.err);
      return 1;
    }
  }

  /**
   * Prints an error message and the usage string, and then exits.
   *
   * @param errorMsg The error message to print.
   * @param cmdLineParser The CmdLineParser used to print usage text for flags.
   * @param usagePrefix The string to prepend to the usage message (when reporting an error).
   */
  static RuntimeException exitWithError(
      String errorMsg, CmdLineParser cmdLineParser, String usagePrefix) {

    System.err.println("\nError: " + errorMsg + "\n\n");
    System.err.println(usagePrefix);
    cmdLineParser.printUsage(System.err);

    System.exit(1);
    throw new AssertionError(); // dead code
  }

  /** Returns a Guice injector that includes the SoyModule, and the given modules. */
  static Injector createInjector(List<Module> modules) {
    modules = new ArrayList<>(modules); // make a copy that we know is mutable
    modules.add(new SoyModule());
    return Guice.createInjector(modules); // TODO(lukes): Stage.PRODUCTION?
  }

  /**
   * Private helper for createInjector().
   *
   * @param moduleName The name of the plugin module to instantiate.
   * @return A new instance of the specified plugin module.
   */
  private static Module instantiatePluginModule(String moduleName) {

    try {
      return (Module) Class.forName(moduleName).getConstructor().newInstance();

    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("Cannot instantiate plugin module \"" + moduleName + "\".", e);
    }
  }

  /**
   * Helper to add srcs and deps Soy files to a SoyFileSet builder. Also does sanity checks.
   *
   * @param sfsBuilder The SoyFileSet builder to add to.
   * @param inputPrefix The input path prefix to prepend to all the file paths.
   * @param srcs The srcs from the --srcs flag. Exactly one of 'srcs' and 'args' must be nonempty.
   * @param args The old-style srcs from the command line (that's how they were specified before we
   *     added the --srcs flag). Exactly one of 'srcs' and 'args' must be nonempty.
   * @param deps The deps from the --deps flag, or empty list if not applicable.
   * @param exitWithErrorFn A function that exits with an error message followed by a usage message.
   */
  static void addSoyFilesToBuilder(
      Builder sfsBuilder,
      String inputPrefix,
      Collection<String> srcs,
      Collection<String> args,
      Collection<String> deps,
      Collection<String> indirectDeps,
      Function<String, Void> exitWithErrorFn) {
    if (srcs.isEmpty() && args.isEmpty()) {
      exitWithErrorFn.apply("Must provide list of source Soy files (--srcs).");
    }
    if (!srcs.isEmpty() && !args.isEmpty()) {
      exitWithErrorFn.apply(
          "Found source Soy files from --srcs and from args (please use --srcs only).");
    }

    // Create Set versions of each of the arguments, and de-dupe. If something is included as
    // multiple file kinds, we'll keep the strongest one; a file in both srcs and deps will be a
    // src, and one in both deps and indirect_deps will be a dep.
    // TODO(gboyer): Maybe stop supporting old style (srcs from command line args) at some point.
    Set<String> srcsSet = ImmutableSet.<String>builder().addAll(srcs).addAll(args).build();
    Set<String> depsSet = Sets.difference(ImmutableSet.copyOf(deps), srcsSet);
    Set<String> indirectDepsSet =
        Sets.difference(ImmutableSet.copyOf(indirectDeps), Sets.union(srcsSet, depsSet));

    for (String src : srcsSet) {
      sfsBuilder.addWithKind(new File(inputPrefix + src), SoyFileKind.SRC);
    }
    for (String dep : depsSet) {
      sfsBuilder.addWithKind(new File(inputPrefix + dep), SoyFileKind.DEP);
    }
    for (String dep : indirectDepsSet) {
      sfsBuilder.addWithKind(new File(inputPrefix + dep), SoyFileKind.INDIRECT_DEP);
    }
  }
}
