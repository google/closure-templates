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

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;


/**
 * Private utils for classes with a main() method.
 *
 * @author Kai Huang
 */
class MainClassUtils {

  private MainClassUtils() {}


  /**
   * OptionHandler for args4j that handles a comma-delimited list.
   */
  public abstract static class ListOptionHandler<T> extends OptionHandler<T> {

    /** {@inheritDoc} */
    public ListOptionHandler(CmdLineParser parser, OptionDef option, Setter<? super T> setter) {
      super(parser, option, setter);
    }

    /**
     * Parses one item from the list into the appropriate type.
     * @param item One item from the list.
     * @return The object representation of the item.
     */
    public abstract T parseItem(String item);

    @Override public int parseArguments(Parameters params) throws CmdLineException {
      for (String item : params.getParameter(0).split(",")) {
        setter.addValue(parseItem(item));
      }
      return 1;
    }

    @Override public String getDefaultMetaVariable() {
      return "ITEM,ITEM,...";
    }
  }


  /**
   * OptionHandler for args4j that handles a comma-delimited list of strings.
   */
  public static class StringListOptionHandler extends ListOptionHandler<String> {

    /** {@inheritDoc} */
    public StringListOptionHandler(
        CmdLineParser parser, OptionDef option, Setter<? super String> setter) {
      super(parser, option, setter);
    }

    @Override public String parseItem(String item) {
      return item;
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
  public static CmdLineParser parseFlags(Object objWithFlags, String[] args, String usagePrefix) {

    CmdLineParser cmdLineParser = new CmdLineParser(objWithFlags);
    cmdLineParser.setUsageWidth(100);

    try {
      cmdLineParser.parseArgument(args);

    } catch(CmdLineException cle) {
      exitWithError(cle.getMessage(), cmdLineParser, usagePrefix);
    }

    return cmdLineParser;
  }


  /**
   * Prints an error message and the usage string, and then exits.
   *
   * @param errorMsg The error message to print.
   * @param cmdLineParser The CmdLineParser used to print usage text for flags.
   * @param usagePrefix The string to prepend to the usage message (when reporting an error).
   */
  public static void exitWithError(
      String errorMsg, CmdLineParser cmdLineParser, String usagePrefix) {

    System.err.println("\nError: " + errorMsg + "\n\n");
    System.err.println(usagePrefix);
    cmdLineParser.printUsage(System.err);

    System.exit(1);
  }

}
