/*
 * Copyright 2020 Google Inc.
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

package com.google.template.soy.soytree;

public enum CommandChar {
  SPACE("{sp}", " "),
  NIL("{nil}", ""),
  CARRIAGE_RETURN("{\\r}", "\r"),
  NEWLINE("{\\n}", "\n"),
  TAB("{\\t}", "\t"),
  LEFT_BRACE("{lb}", "{"),
  RIGHT_BRACE("{rb}", "}"),
  // https://en.wikipedia.org/wiki/Non-breaking_space
  NBSP("{nbsp}", "\u00A0");

  private final String sourceString;
  private final String processedString;

  CommandChar(String sourceString, String processedString) {
    this.sourceString = sourceString;
    this.processedString = processedString;
  }

  /** The source string, escaped if necessary (e.g. "{\\t}" or "{sp}"). */
  public String sourceString() {
    return sourceString;
  }

  /** The processed command character (e.g. "\t" or "\u00A0" for nbsp). */
  public String processedString() {
    return processedString;
  }
}
