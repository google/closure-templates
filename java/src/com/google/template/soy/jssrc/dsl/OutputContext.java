/*
 * Copyright 2017 Google Inc.
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

package com.google.template.soy.jssrc.dsl;

/**
 * Represents the grammatical context into which an expression is formatted.
 *
 * <p>Expressions can compose with each other to build larger expressions,
 * but they can also stand on their own as statements, in which case they should appear with
 * a trailing semicolon and newline.
 */
enum OutputContext {
  /**
   * The expression is being inserted into another expression.
   * Omit semicolons and newlines.
   */
  EXPRESSION,

  /**
   * The expression is being inserted into another expression,
   * but it is the last component of the entire unit of code that is being
   * {@link CodeChunk#getCode() formatted}. There is therefore no need to serialize
   * the name of variable that holds this expression's value, if any (since there is no
   * following code that could reference it).
   * TODO(brndn): this is relevant only for {@link Composite}.
   */
  TRAILING_EXPRESSION,

  /**
   * The expression will appear as its own statement.
   * Include a trailing semicolon and newline.
   */
  STATEMENT
}
