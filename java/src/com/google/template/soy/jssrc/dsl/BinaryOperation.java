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
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.Operator.Associativity;

/** Represents a JavaScript binary operation. */
@AutoValue
@Immutable
abstract class BinaryOperation extends Operation {
  abstract String operator();

  abstract Expression arg1();

  abstract Expression arg2();

  static Expression create(Operator operator, Expression arg1, Expression arg2) {
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
      Expression arg1,
      Expression arg2) {
    return new AutoValue_BinaryOperation(
        ImmutableList.<Statement>builder()
            .addAll(arg1.initialStatements())
            .addAll(arg2.initialStatements())
            .build(),
        precedence,
        associativity,
        operator,
        arg1,
        arg2);
  }

  static Expression and(Expression lhs, Expression rhs, CodeChunk.Generator codeGenerator) {
    // If rhs has no initial statements, use the JS && operator directly.
    // It's already short-circuiting.
    if (lhs.initialStatements().containsAll(rhs.initialStatements())) {
      return create("&&", Operator.AND.getPrecedence(), Operator.AND.getAssociativity(), lhs, rhs);
    }
    // Otherwise, generate explicit short-circuiting code.
    // rhs should be evaluated only if lhs evaluates to true.
    Expression tmp = codeGenerator.declarationBuilder().setRhs(lhs).build().ref();
    return Composite.create(
        ImmutableList.of(Statement.ifStatement(tmp, tmp.assign(rhs).asStatement()).build()), tmp);
  }

  static Expression or(Expression lhs, Expression rhs, CodeChunk.Generator codeGenerator) {
    // If rhs has no initial statements, use the JS || operator directly.
    // It's already short-circuiting.
    if (lhs.initialStatements().containsAll(rhs.initialStatements())) {
      return create("||", Operator.OR.getPrecedence(), Operator.OR.getAssociativity(), lhs, rhs);
    }
    // Otherwise, generate explicit short-circuiting code.
    // rhs should be evaluated only if lhs evaluates to false.
    Expression tmp = codeGenerator.declarationBuilder().setRhs(lhs).build().ref();
    return Composite.create(
        ImmutableList.of(Statement.ifStatement(not(tmp), tmp.assign(rhs).asStatement()).build()),
        tmp);
  }

  @Override
  public void collectRequires(RequiresCollector collector) {
    arg1().collectRequires(collector);
    arg2().collectRequires(collector);
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    formatOperand(arg1(), OperandPosition.LEFT, ctx);
    ctx.append(' ').append(operator()).append(' ');
    formatOperand(arg2(), OperandPosition.RIGHT, ctx);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    ctx.appendInitialStatements(arg1()).appendInitialStatements(arg2());
  }
}
