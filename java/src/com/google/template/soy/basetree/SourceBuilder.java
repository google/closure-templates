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

/** Interface for building (Soy) source code. */
public interface SourceBuilder {
  /**
   * Appends a snippet and breaks the line.
   *
   * @param snippet A snippet of code to append. If the string is multiline, first line will be
   *     printed inline followed by the rest of the lines being indented according to the current
   *     indent level. Empty lines always have 0-indent.
   */
  SourceBuilder appendLine(String snippet);

  /** Append new line. */
  SourceBuilder newLine();

  /** Increases current indent level. */
  SourceBuilder indent();

  /** Decreasers current indent level. */
  SourceBuilder dedent();

  String build();
}
