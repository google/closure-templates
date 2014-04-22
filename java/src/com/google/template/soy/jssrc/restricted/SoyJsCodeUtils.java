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

package com.google.template.soy.jssrc.restricted;

import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.internal.targetexpr.ExprUtils;

import java.util.List;


/**
 * Convenience utilities for building code for the JS Source backend.
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 */
public class SoyJsCodeUtils {

  private SoyJsCodeUtils() {}


  /**
   * Generates a JS expression for the given operator and operands assuming that the JS expression
   * for the operator uses the same syntax format as the Soy operator.
   * @param op The operator.
   * @param operandJsExprs The operands.
   * @return The generated JS expression.
   */
  public static JsExpr genJsExprUsingSoySyntax(Operator op, List<JsExpr> operandJsExprs) {
    return genJsExprUsingSoySyntaxWithNewToken(op, operandJsExprs, null);
  }


  /**
   * Generates a JS expression for the given operator and operands assuming that the JS expression
   * for the operator uses the same syntax format as the Soy operator, with the exception that the
   * JS operator uses a different token (e.g. "!" instead of "not").
   * @param op The operator.
   * @param operandJsExprs The operands.
   * @param newToken The equivalent JS operator's token.
   * @return The generated JS expression.
   */
  public static JsExpr genJsExprUsingSoySyntaxWithNewToken(
      Operator op, List<JsExpr> operandJsExprs, String newToken) {
    return new JsExpr(ExprUtils.genExprWithNewToken(op, operandJsExprs, newToken),
        op.getPrecedence());
  }

}