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

import com.google.inject.Module;
import java.io.File;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

/** A command line parser for soy, based on args4j. */
final class SoyCmdLineParser extends CmdLineParser {
  static {
    CmdLineParser.registerHandler(Module.class, ModuleOptionHandler.class);
    // overwrite the built in boolean handler
    CmdLineParser.registerHandler(Boolean.class, BooleanOptionHandler.class);
    CmdLineParser.registerHandler(boolean.class, BooleanOptionHandler.class);
  }

  private final ClassLoader pluginLoader;

  SoyCmdLineParser(Object bean, ClassLoader loader) {
    super(bean);
    this.pluginLoader = loader;
  }

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
      return instantiatePluginModule(((SoyCmdLineParser) this.owner).pluginLoader, item);
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

  /**
   * OptionHandler for args4j that handles a comma-delimited list of strings referencing guice
   * module names.
   */
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
        setter.addValue(
            instantiatePluginModule(((SoyCmdLineParser) this.owner).pluginLoader, parameter));
      }
      return 1;
    }

    @Override
    public String getDefaultMetaVariable() {
      return "com.foo.bar.BazModule";
    }
  }

  /**
   * Private helper for createInjector().
   *
   * @param moduleName The name of the plugin module to instantiate.
   * @return A new instance of the specified plugin module.
   */
  private static Module instantiatePluginModule(ClassLoader loader, String moduleName) {
    try {
      return (Module) Class.forName(moduleName, true, loader).getConstructor().newInstance();

    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("Cannot instantiate plugin module \"" + moduleName + "\".", e);
    }
  }
}
