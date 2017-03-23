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

import static com.google.template.soy.exprtree.Operator.CONDITIONAL;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.exprtree.Operator.Associativity;

/**
 * Represents a ternary expression. Its consequent and alternate chunks are required to be
 * representable as single expressions, though its predicate can be more complex.
 */
@AutoValue
@Immutable
abstract class Ternary extends Operation {
  abstract CodeChunk.WithValue predicate();

  abstract CodeChunk.WithValue consequent();

  abstract CodeChunk.WithValue alternate();

  static Ternary create(
      CodeChunk.WithValue predicate,
      CodeChunk.WithValue consequent,
      CodeChunk.WithValue alternate) {
    Preconditions.checkArgument(
        predicate.initialStatements().containsAll(consequent.initialStatements()));
    Preconditions.checkArgument(
        predicate.initialStatements().containsAll(alternate.initialStatements()));
    return new AutoValue_Ternary(
        ImmutableSet.<CodeChunk>builder()
            .addAll(predicate.initialStatements())
            .addAll(consequent.initialStatements())
            .addAll(alternate.initialStatements())
            .build(),
        predicate,
        consequent,
        alternate);
  }

  @Override
  int precedence() {
    return CONDITIONAL.getPrecedence();
  }

  @Override
  Associativity associativity() {
    return CONDITIONAL.getAssociativity();
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    ctx.appendInitialStatements(predicate());
    // consequent and alternate cannot have initial statements
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    formatOperand(predicate(), OperandPosition.LEFT, ctx);
    ctx.append(" ? ");
    formatOperand(consequent(), OperandPosition.LEFT, ctx);
    ctx.append(" : ");
    formatOperand(alternate(), OperandPosition.RIGHT, ctx);
  }

  @Override
  public void collectRequires(RequiresCollector collector) {
    predicate().collectRequires(collector);
    consequent().collectRequires(collector);
    alternate().collectRequires(collector);
  }
}
