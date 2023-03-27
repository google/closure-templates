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
import java.util.stream.Stream;

/** Represents a JavaScript function call. */
@AutoValue
@Immutable
public abstract class Call extends Operation {
  public abstract Expression receiver();

  public abstract ImmutableList<Expression> args();

  public static Call create(Expression receiver, ImmutableList<Expression> args) {
    return new AutoValue_Call(receiver, args);
  }

  @Override
  Precedence precedence() {
    return Precedence.P17;
  }

  @Override
  Associativity associativity() {
    return LEFT;
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.concat(Stream.of(receiver()), args().stream());
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
      ctx.appendOutputExpression(arg);
    }
    ctx.append(')');
  }

  @Override
  boolean initialExpressionIsObjectLiteral() {
    return receiver().initialExpressionIsObjectLiteral();
  }
}
