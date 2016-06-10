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

package com.google.template.soy.jssrc.internal;


/** High-level (non-line-oriented) alternative to {@link JsCodeBuilder}. */
interface HighLevelJsCodeBuilder {

  /** Declares a JavaScript module with the given name, returning this builder for chaining. */
  HighLevelJsCodeBuilder declareModule(String module);

  /** Declares a JavaScript namespace with the given name, returning this builder for chaining. */
  HighLevelJsCodeBuilder declareNamespace(String namespace);

  /**
   * Declares a {@code goog.provide}'d symbol with the given name,
   * returning this builder for chaining.
   */
  HighLevelJsCodeBuilder provide(String symbol);

  /**
   * Declares a JavaScript variable with the given name, returning a builder object
   * to configure the variable's value.
   */
  DeclarationBuilder declareVariable(String name);

  /**
   * Opens a JavaScript {@code if} statement, returning a builder object
   * to manage the rest of the conditional block.
   */
  ConditionBuilder _if(String expr);

  interface DeclarationBuilder {
    /**
     * Binds the JavaScript variable to the given value, returning the original
     * {@link HighLevelJsCodeBuilder} for chaining.
     */
    HighLevelJsCodeBuilder withValue(String value);
  }

  interface ConditionBuilder {
    /**
     * Ends the body of the {@code if} block and begins the body of the {@code else} block,
     * returning this builder for chaining.
     */
    ConditionBuilder _else();

    /**
     * Ends the body of the {@code else} block, returning the original
     * {@link HighLevelJsCodeBuilder} for chaining.
     */
    HighLevelJsCodeBuilder endif();
  }
}
