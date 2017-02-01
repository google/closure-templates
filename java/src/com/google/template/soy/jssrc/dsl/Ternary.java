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
import com.google.template.soy.exprtree.Operator.Associativity;

/**
 * Represents a ternary expression. All of its constituent chunks are representable as single
 * expressions, so it is too.
 */
@AutoValue
abstract class Ternary extends Operation {
  abstract CodeChunk.WithValue predicate();

  abstract CodeChunk.WithValue consequent();

  abstract CodeChunk.WithValue alternate();

  static Ternary create(
      CodeChunk.WithValue predicate,
      CodeChunk.WithValue consequent,
      CodeChunk.WithValue alternate) {
    Preconditions.checkState(predicate.isRepresentableAsSingleExpression());
    Preconditions.checkState(consequent.isRepresentableAsSingleExpression());
    Preconditions.checkState(alternate.isRepresentableAsSingleExpression());
    return new AutoValue_Ternary(predicate, consequent, alternate);
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
  void doFormatInitialStatements(FormattingContext ctx, boolean moreToCome) {
    // Nothing to do
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx, OutputContext outputContext) {
    formatOperand(predicate(), OperandPosition.LEFT, ctx);
    ctx.append(" ? ");
    formatOperand(consequent(), OperandPosition.LEFT, ctx);
    ctx.append(" : ");
    formatOperand(alternate(), OperandPosition.RIGHT, ctx);
  }

  @Override
  public boolean isRepresentableAsSingleExpression() {
    return true;
  }

  @Override
  public void collectRequires(RequiresCollector collector) {
    predicate().collectRequires(collector);
    consequent().collectRequires(collector);
    alternate().collectRequires(collector);
  }
}
