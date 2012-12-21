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
import com.google.template.soy.exprtree.Operator.Associativity;
import com.google.template.soy.exprtree.Operator.Operand;
import com.google.template.soy.exprtree.Operator.Spacer;
import com.google.template.soy.exprtree.Operator.SyntaxElement;
import com.google.template.soy.exprtree.Operator.Token;

import java.util.List;


/**
 * Utilities for building code for the JS Source backend.
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 * @author Kai Huang
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

    int opPrec = op.getPrecedence();
    boolean isLeftAssociative = op.getAssociativity() == Associativity.LEFT;

    StringBuilder exprSb = new StringBuilder();

    // Iterate through the operator's syntax elements.
    List<SyntaxElement> syntax = op.getSyntax();
    for (int i = 0, n = syntax.size(); i < n; i++) {
      SyntaxElement syntaxEl = syntax.get(i);

      if (syntaxEl instanceof Operand) {
        // Retrieve the operand's subexpression.
        int operandIndex = ((Operand) syntaxEl).getIndex();
        JsExpr operandJsExpr = operandJsExprs.get(operandIndex);
        // If left (right) associative, first (last) operand doesn't need protection if it's an
        // operator of equal precedence to this one.
        boolean needsProtection;
        if (i == (isLeftAssociative ? 0 : n-1)) {
          needsProtection = operandJsExpr.getPrecedence() < opPrec;
        } else {
          needsProtection = operandJsExpr.getPrecedence() <= opPrec;
        }
        // Append the operand's subexpression to the expression we're building (if necessary,
        // protected using parentheses).
        String subexpr = needsProtection ? "(" + operandJsExpr.getText() + ")"
                                         : operandJsExpr.getText();
        exprSb.append(subexpr);

      } else if (syntaxEl instanceof Token) {
        // If a newToken is supplied, then use it, else use the token defined by Soy syntax.
        if (newToken != null) {
          exprSb.append(newToken);
        } else {
          exprSb.append(((Token) syntaxEl).getValue());
        }

      } else if (syntaxEl instanceof Spacer) {
        // Spacer is just one space.
        exprSb.append(' ');

      } else {
        throw new AssertionError();
      }
    }

    return new JsExpr(exprSb.toString(), opPrec);
  }

}
