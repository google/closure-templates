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

/** Represents a JavaScript member access ({@code .}) expression. */
@AutoValue
@Immutable
abstract class Dot extends Operation {

  abstract Expression receiver();

  abstract Expression key();

  static Dot create(Expression receiver, Expression key) {
    return new AutoValue_Dot(
        ImmutableList.<Statement>builder()
            .addAll(receiver.initialStatements())
            .addAll(key.initialStatements())
            .build(),
        receiver,
        key);
  }

  @Override
  int precedence() {
    return Integer.MAX_VALUE; // JS member access has higher precedence than any Soy operator
  }

  @Override
  Associativity associativity() {
    return LEFT;
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    ctx.appendInitialStatements(receiver()).appendInitialStatements(key());
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    formatOperand(receiver(), OperandPosition.LEFT, ctx);
    ctx.append('.');
    formatOperand(key(), OperandPosition.RIGHT, ctx);
  }

  @Override
  public void collectRequires(RequiresCollector collector) {
    receiver().collectRequires(collector);
    key().collectRequires(collector);
  }
}
