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
import com.google.template.soy.exprtree.Operator.Associativity;
import com.google.template.soy.jssrc.dsl.CodeChunk.RequiresCollector;

/** Represents a JavaScript computed member access ({@code []}) expression. */
@AutoValue
abstract class Bracket extends Operation {

  abstract CodeChunk.WithValue receiver();

  abstract CodeChunk.WithValue key();

  static Bracket create(CodeChunk.WithValue receiver, CodeChunk.WithValue key) {
    return new AutoValue_Bracket(receiver, key);
  }

  @Override
  int precedence() {
    // JS computed member access has higher precedence than any Soy operator
    return Integer.MAX_VALUE;
  }

  @Override
  Associativity associativity() {
    return LEFT;
  }

  @Override
  public boolean isRepresentableAsSingleExpression() {
    return receiver().isRepresentableAsSingleExpression()
        && key().isRepresentableAsSingleExpression();
  }

  @Override
  public void collectRequires(RequiresCollector collector) {
    receiver().collectRequires(collector);
    key().collectRequires(collector);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx, boolean moreToCome) {
    receiver().formatInitialStatements(ctx, true /* moreToCome */);
    key().formatInitialStatements(ctx, moreToCome);
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx, OutputContext outputContext) {
    formatOperand(receiver(), OperandPosition.LEFT, ctx);
    ctx.append('[');
    // No need to protect the expression in the bracket with parens. it's unambiguous.
    key().formatOutputExpr(ctx, EXPRESSION);
    ctx.append(']');
  }
}
