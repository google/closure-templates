/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.internal.targetexpr;

/**
 * Value class to represent an expression in the target source (JS, Python, etc.). Includes the text
 * of the expression as well as the precedence of the top-most operator.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public interface TargetExpr {

  /** Returns the expression text in the target language. */
  public String getText();

  /** Returns the precedence of the top-most operator, or Integer.MAX_VALUE. */
  public int getPrecedence();

}
