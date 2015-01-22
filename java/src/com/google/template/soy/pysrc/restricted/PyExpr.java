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

package com.google.template.soy.pysrc.restricted;

import com.google.template.soy.internal.targetexpr.TargetExpr;

/**
 * Value class to represent a Python expression. Includes the text of the expression as well as the
 * precedence of the top-most operator.
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 * <p> Since the type information is rather generic it could potentially be shared with the JsExpr,
 * but as JS doesn't currently have any uses, and Python types do differ in some aspects (such as
 * with numbers), it's kept separate.
 *
 * <p> NOTE: Some expressions could potentially return multiple types (such as a ternary if with a
 * String or number as potential results). If possible to avoid, the results will be improved, but
 * if not, this class can be used with no type assumed.
 *
 */
public class PyExpr extends TargetExpr {

  /**
   * @param text The Python expression text.
   * @param precedence The precedence of the top-most operator. Or Integer.MAX_VALUE.
   */
  public PyExpr(String text, int precedence) {
    super(text, precedence);
  }

  /**
   * Convert the given type to a Python String expression.
   *
   * @return A PyStringExpr representing this expression as a String.
   */
  public PyStringExpr toPyString() {
    return new PyStringExpr("str(" + getText() + ")", Integer.MAX_VALUE);
  }
}
