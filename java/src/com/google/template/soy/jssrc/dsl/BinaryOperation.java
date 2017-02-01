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
import com.google.common.base.Preconditions;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.Operator.Associativity;
import com.google.template.soy.jssrc.dsl.CodeChunk.RequiresCollector;

/** Represents a JavaScript binary operation. */
@AutoValue
abstract class BinaryOperation extends Operation {
  abstract String operator();

  abstract CodeChunk.WithValue arg1();

  abstract CodeChunk.WithValue arg2();

  static CodeChunk.WithValue create(
      Operator operator, CodeChunk.WithValue arg1, CodeChunk.WithValue arg2) {
    Preconditions.checkState(operator != Operator.AND, "use BinaryOperation::and");
    Preconditions.checkState(operator != Operator.OR, "use BinaryOperation::or");
    return create(
        operator.getTokenString(),
        operator.getPrecedence(),
        operator.getAssociativity(),
        arg1,
        arg2);
  }

  static BinaryOperation create(
      String operator,
      int precedence,
      Associativity associativity,
      CodeChunk.WithValue arg1,
      CodeChunk.WithValue arg2) {
    return new AutoValue_BinaryOperation(precedence, associativity, operator, arg1, arg2);
  }

  static CodeChunk.WithValue and(
      CodeChunk.WithValue lhs, CodeChunk.WithValue rhs, CodeChunk.Generator codeGenerator) {
    // If rhs is representable as a single expression, use the JS && operator directly.
    // It's already short-circuiting.
    if (rhs.isRepresentableAsSingleExpression()) {
      return create("&&", Operator.AND.getPrecedence(), Operator.AND.getAssociativity(), lhs, rhs);
    }
    // Otherwise, generate explicit short-circuiting code.
    // rhs should be evaluated only if lhs evaluates to true.
    CodeChunk.WithValue tmp = codeGenerator.declare(lhs);
    return codeGenerator.newChunk(tmp).if_(tmp, tmp.assign(rhs)).endif().buildAsValue();
  }

  static CodeChunk.WithValue or(
      CodeChunk.WithValue lhs, CodeChunk.WithValue rhs, CodeChunk.Generator codeGenerator) {
    // If rhs is representable as a single expression, use the JS || operator directly.
    // It's already short-circuiting.
    if (rhs.isRepresentableAsSingleExpression()) {
      return create("||", Operator.OR.getPrecedence(), Operator.OR.getAssociativity(), lhs, rhs);
    }
    // Otherwise, generate explicit short-circuiting code.
    // rhs should be evaluated only if lhs evaluates to false.
    CodeChunk.WithValue tmp = codeGenerator.declare(lhs);
    return codeGenerator.newChunk(tmp).if_(not(tmp), tmp.assign(rhs)).endif().buildAsValue();
  }

  @Override
  public boolean isRepresentableAsSingleExpression() {
    return arg1().isRepresentableAsSingleExpression() && arg2().isRepresentableAsSingleExpression();
  }

  @Override
  public void collectRequires(RequiresCollector collector) {
    arg1().collectRequires(collector);
    arg2().collectRequires(collector);
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx, OutputContext outputContext) {
    formatOperand(arg1(), OperandPosition.LEFT, ctx);
    ctx.append(' ').append(operator()).append(' ');
    formatOperand(arg2(), OperandPosition.RIGHT, ctx);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx, boolean moreToCome) {
    arg1().formatInitialStatements(ctx, true /* moreToCome */);
    arg2().formatInitialStatements(ctx, moreToCome);
  }
}
