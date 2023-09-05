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

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.dsl.Precedence.Associativity;
import java.util.stream.Stream;

/** Represents a JavaScript binary operation. */
@AutoValue
@Immutable
abstract class BinaryOperation extends Operation {
  abstract String operator();

  abstract Expression arg1();

  abstract Expression arg2();

  static Expression create(Operator operator, Expression arg1, Expression arg2) {
    return create(
        Operation.getOperatorToken(operator),
        Precedence.forSoyOperator(operator),
        Precedence.getAssociativity(operator),
        arg1,
        arg2);
  }

  static BinaryOperation create(
      String operator,
      Precedence precedence,
      Associativity associativity,
      Expression arg1,
      Expression arg2) {
    return new AutoValue_BinaryOperation(precedence, associativity, operator, arg1, arg2);
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.of(arg1(), arg2());
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    formatOperand(arg1(), OperandPosition.LEFT, ctx);
    ctx.noBreak().append(' ').noBreak().append(operator()).append(' ');
    formatOperand(arg2(), OperandPosition.RIGHT, ctx);
  }

  @Override
  boolean initialExpressionIsObjectLiteral() {
    return arg1().initialExpressionIsObjectLiteral();
  }

  @Override
  protected boolean shouldProtect(Expression operand, OperandPosition operandPosition) {
    // Closure compiler fails on expressions like "a ?? b && c" with:
    //   Logical OR and logical AND require parentheses when used with '??'
    boolean specialRequired = false;
    if (operator().equals("??")) {
      // This traversal could probably be more selective, like not traversing into a grouping.
      specialRequired =
          CodeChunks.breadthFirst(operand)
              .anyMatch(
                  chunk -> {
                    if (chunk instanceof BinaryOperation) {
                      String thatOp = ((BinaryOperation) chunk).operator();
                      return thatOp.equals("&&") || thatOp.equals("||");
                    }
                    return false;
                  });
    } else if (operator().equals("||")) {
      // This traversal could probably be more selective, like not traversing into a grouping.
      specialRequired =
          CodeChunks.breadthFirst(operand)
              .anyMatch(
                  chunk -> {
                    if (chunk instanceof BinaryOperation) {
                      String thatOp = ((BinaryOperation) chunk).operator();
                      return thatOp.equals("??");
                    }
                    return false;
                  });
    }
    if (specialRequired) {
      return true;
    }
    return super.shouldProtect(operand, operandPosition);
  }
}
