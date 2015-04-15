/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.jbcsrc;

import com.google.template.soy.data.SoyValueProvider;

/**
 * A mechanism to lookup Soy variables.
 */
interface VariableLookup {
  /**
   * Returns an expression for a given {@code @param} or {@code @inject} parameter.
   * 
   * <p>The expression will be for a {@link SoyValueProvider}.
   */
  Expression getParam(String paramName);

  /**
   * Returns an expression for a given {@code @param} or {@code @inject} parameter.
   * 
   * <p>The type of the expression will be based on the kind of variable being accessed.
   */
  Expression getLocal(String localName);

  /**
   * Returns an expression for a given {@code @param} or {@code @inject} parameter.
   * 
   * <p>The type of the expression will be based on the kind of variable being accessed.
   */
  Expression getLocal(SyntheticVarName varName);
}
