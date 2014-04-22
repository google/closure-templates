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

import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.Operator.Associativity;
import com.google.template.soy.exprtree.Operator.Operand;
import com.google.template.soy.exprtree.Operator.Spacer;
import com.google.template.soy.exprtree.Operator.SyntaxElement;
import com.google.template.soy.exprtree.Operator.Token;

import java.util.List;

/**
 * Utilities for transformation expressions.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class ExprUtils {

  private ExprUtils() {}


  /**
   * Generates an expression for the given operator and operands assuming that the expression
   * for the operator uses the same syntax format as the Soy operator, with the exception that the
   * of a different token. Associativity, spacing, and precedence are maintained from the original
   * operator.
   *
   * Examples:
   * NOT, ["$a"], "!" -> "! $a"
   * AND, ["$a", "$b"], "&&" -> "$a && $b"
   * NOT, ["$a * $b"], "!"; -> "! ($a * $b)"
   *
   * @param op The operator.
   * @param operandExprs The operands.
   * @param newToken The language specific token equivalent to the operator's original token.
   * @return The generated expression with a new token.
   */
  public static String genExprWithNewToken(
      Operator op, List<? extends TargetExpr> operandExprs, String newToken) {

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
        TargetExpr operandExpr = operandExprs.get(operandIndex);
        // If left (right) associative, first (last) operand doesn't need protection if it's an
        // operator of equal precedence to this one.
        boolean needsProtection;
        if (i == (isLeftAssociative ? 0 : n - 1)) {
          needsProtection = operandExpr.getPrecedence() < opPrec;
        } else {
          needsProtection = operandExpr.getPrecedence() <= opPrec;
        }
        // Append the operand's subexpression to the expression we're building (if necessary,
        // protected using parentheses).
        String subexpr = needsProtection ? "(" + operandExpr.getText() + ")"
            : operandExpr.getText();
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

    return exprSb.toString();
  }

}
