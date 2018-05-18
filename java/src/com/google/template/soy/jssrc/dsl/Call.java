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

import static com.google.template.soy.exprtree.Operator.Associativity.LEFT;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.exprtree.Operator.Associativity;

/** Represents a JavaScript function call. */
@AutoValue
@Immutable
abstract class Call extends Operation {
  abstract Expression receiver();

  abstract ImmutableList<Expression> args();

  static Call create(Expression receiver, ImmutableList<Expression> args) {
    ImmutableList.Builder<Statement> builder = ImmutableList.builder();
    builder.addAll(receiver.initialStatements());
    for (Expression arg : args) {
      builder.addAll(arg.initialStatements());
    }
    return new AutoValue_Call(builder.build(), receiver, args);
  }

  @Override
  int precedence() {
    // The precedence of a JavaScript function call is higher than any Soy operator.
    return Integer.MAX_VALUE;
  }

  @Override
  Associativity associativity() {
    return LEFT;
  }
  
  @Override
  public void collectRequires(RequiresCollector collector) {
    receiver().collectRequires(collector);
    for (Expression arg : args()) {
      arg.collectRequires(collector);
    }
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    formatOperand(receiver(), OperandPosition.LEFT, ctx);
    ctx.append('(');
    boolean first = true;
    for (Expression arg : args()) {
      if (first) {
        first = false;
      } else {
        ctx.append(", ");
      }
      // The comma is the lowest-precedence JavaScript operator, so none of the args
      // need to be protected.
      arg.doFormatOutputExpr(ctx);
    }
    ctx.append(')');
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    ctx.appendInitialStatements(receiver());
    for (Expression arg : args()) {
      ctx.appendInitialStatements(arg);
    }
  }
}
