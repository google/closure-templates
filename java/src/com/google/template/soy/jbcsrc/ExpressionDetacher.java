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
 * A helper for generating detach operations in soy expressions.
 */
interface ExpressionDetacher {
  interface Factory {
    /**
     * Returns a new {@link ExpressionDetacher}.  Any given soy expression requires at most one
     * detacher.
     */
    ExpressionDetacher createExpressionDetacher();
  }

  /**
   * Returns a new {@link Expression} that has the same behavior as {@code expr} but adds a prologue
   * and epilogue to implement detach logic.
   */
  Expression makeDetachable(Expression expr);
  
  /**
   * Returns an expression for the SoyValue that is resolved by the given SoyValueProvider, 
   * potentially detaching if it is not {@link SoyValueProvider#status() resolvable}.
   * 
   * @param soyValueProvider an expression yielding a SoyValueProvider
   * @return an expression yielding a SoyValue returned by {@link SoyValueProvider#resolve()}.
   */
  Expression resolveSoyValueProvider(Expression soyValueProvider);
}
