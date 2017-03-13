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

package com.google.template.soy.jssrc.dsl;

import com.google.template.soy.exprtree.Operator.Associativity;
import com.google.template.soy.jssrc.restricted.JsExpr;

/** Base class for representing a JavaScript operation. */
abstract class Operation extends CodeChunk.WithValue {

  abstract int precedence();
  abstract Associativity associativity();

  @Override
  public final JsExpr singleExprOrName() {
    FormattingContext ctx = new FormattingContext();
    ctx.appendOutputExpression(this);
    return new JsExpr(ctx.toString(), precedence());
  }

  /**
   * Surrounds the operand with parens if required by its {@link #precedence}
   * or {@link #associativity}. For subclasses to call from {@link #doFormatOutputExpr}.
   *
   * @param operandPosition The position of the operand with respect to this operation.
   */
  final void formatOperand(
      CodeChunk.WithValue operand,
      OperandPosition operandPosition,
      FormattingContext ctx) {
    boolean protect = shouldProtect(operand, operandPosition);
    if (protect) {
      ctx.append('(');
    }
    operand.doFormatOutputExpr(ctx);
    if (protect) {
      ctx.append(')');
    }
  }

  /**
   * An operand needs to be protected with parens if
   * <ul>
   *   <li>its {@link #precedence} is lower than the operator's precedence, or
   *   <li>its precedence is the same as the operator's, it is
   *       {@link Associativity#LEFT left associative}, and it appears to the right
   *       of the operator, or
   *   <li>its precedence is the same as the operator's, it is
   *       {@link Associativity#RIGHT right associative}, and it appears to the left
   *       of the operator.
   * </ul>
   */
  private boolean shouldProtect(CodeChunk.WithValue operand, OperandPosition operandPosition) {
    if (operand instanceof Operation) {
      Operation operation = (Operation) operand;
      return operation.precedence() < this.precedence()
          || (operation.precedence() == this.precedence()
              && operandPosition.shouldParenthesize(operation.associativity()));
    } else if (operand instanceof Leaf) {
      // JsExprs have precedence info, but not associativity. So at least check the precedence.
      JsExpr expr = ((Leaf) operand).value();
      return expr.getPrecedence() < this.precedence();
    } else {
      return false;
    }
  }
}
