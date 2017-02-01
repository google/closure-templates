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
import static com.google.template.soy.jssrc.dsl.OutputContext.EXPRESSION;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.exprtree.Operator.Associativity;
import com.google.template.soy.jssrc.dsl.CodeChunk.RequiresCollector;

/** Represents a JavaScript function call. */
@AutoValue
abstract class Call extends Operation {
  abstract CodeChunk.WithValue receiver();

  abstract ImmutableList<CodeChunk.WithValue> args();

  static Call create(CodeChunk.WithValue receiver, ImmutableList<CodeChunk.WithValue> args) {
    return new AutoValue_Call(receiver, args);
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
    for (CodeChunk.WithValue arg : args()) {
      arg.collectRequires(collector);
    }
  }


  @Override
  public boolean isRepresentableAsSingleExpression() {
    if (!receiver().isRepresentableAsSingleExpression()) {
      return false;
    }
    for (CodeChunk.WithValue arg : args()) {
      if (!arg.isRepresentableAsSingleExpression()) {
        return false;
      }
    }
    return true;
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx, OutputContext outputContext) {
    formatOperand(receiver(), OperandPosition.LEFT, ctx);
    ctx.append('(');
    boolean first = true;
    for (WithValue arg : args()) {
      if (first) {
        first = false;
      } else {
        ctx.append(", ");
      }
      // The comma is the lowest-precedence JavaScript operator, so none of the args
      // need to be protected.
      arg.formatOutputExpr(ctx, EXPRESSION);
    }
    ctx.append(')');
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx, boolean moreToCome) {
    receiver().formatInitialStatements(ctx, true /* moreToCome */);
    for (int i = 0; i < args().size(); ++i) {
      args().get(i).formatInitialStatements(ctx, moreToCome || i < args().size() - 1);
    }
  }
}
