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
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.exprtree.Operator.Associativity;

/** Represents the JavaScript {@code new} operator. */
@AutoValue
@Immutable
abstract class New extends Operation {

  abstract CodeChunk.WithValue ctor();

  static New create(WithValue ctor) {
    return new AutoValue_New(ctor);
  }

  @Override
  int precedence() {
    return Integer.MAX_VALUE; // JS instantiation has higher precedence than any Soy operator
  }

  @Override
  Associativity associativity() {
    return LEFT; // it's unary, doesn't matter
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    ctx.appendInitialStatements(ctor());
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.append("new ");
    formatOperand(ctor(), OperandPosition.LEFT, ctx);
  }

  @Override
  public void collectRequires(RequiresCollector collector) {
    ctor().collectRequires(collector);
  }

  @Override
  public ImmutableSet<CodeChunk> initialStatements() {
    return ctor().initialStatements();
  }
}
