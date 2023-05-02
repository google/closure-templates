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
import com.google.template.soy.exprtree.Operator.Associativity;
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
        operator.getAssociativity(),
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
}
