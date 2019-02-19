/*
 * Copyright 2019 Google Inc.
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

package com.google.template.soy.basetree;

/** Produces Soy source text. */
public interface SourceBuilder {

  /**
   * Appends a multiline snippet.
   *
   * @param snippet A mulitline snippet of code to append. snippet will by broken into lines by
   *     `\n`. The first line will be printed inline followed by the rest of the lines being
   *     indented according to the current indent level. Empty lines always have 0-indent.
   */
  LineBuilder appendMultiline(String snippet);

  /** Returns the current line. */
  LineBuilder currentLine();

  /** Create and return a new line. */
  LineBuilder newLine();

  /** Create and return a new line if the current line is not empty. */
  LineBuilder maybeNewLine();

  /** Increases current indent level. */
  Indent indent();

  String build();

  /** Builder interface for a line of Soy source. */
  interface LineBuilder {
    /**
     * Appends a line fragment to the current line.
     *
     * @param lineFragment A snippet of code to append. All the `\n` will be ignored.
     */
    LineBuilder append(String lineFragment);
  }

  /** Represents indent in the context of the current builder. dedents via {@link #close}. */
  interface Indent extends AutoCloseable {
    /** Dedents this indent. */
    @Override
    void close();
  }
}
