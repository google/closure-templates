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

import static com.google.template.soy.jssrc.dsl.OutputContext.EXPRESSION;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.jssrc.dsl.CodeChunk.RequiresCollector;
import javax.annotation.Nullable;

/** Represents an {@code if}-{@code else if}-{@code else} statement. */
@AutoValue
abstract class Conditional extends CodeChunk {
  abstract ImmutableList<IfThenPair> conditions();
  @Nullable abstract CodeChunk trailingElse();

  static Conditional create(
      ImmutableList<IfThenPair> conditions, @Nullable CodeChunk trailingElse) {
    Preconditions.checkArgument(!conditions.isEmpty());
    return new AutoValue_Conditional(conditions, trailingElse);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx, boolean moreToCome) {
    formatIfClause(ctx);
    int numRightBracesToClose = 0;
    for (IfThenPair condition : conditions().subList(1, conditions().size())) {
      if (condition.predicate.isRepresentableAsSingleExpression()) {
        formatElseIfClauseWithNoDependencies(condition, ctx);
      } else {
        formatElseIfClauseWithDependencies(condition, ctx);
        ++numRightBracesToClose;
      }
    }
    formatElseClause(ctx);
    // Explicitly close the extra blocks opened by formatElseIfClauseWithDependencies.
    for (int i = 0; i < numRightBracesToClose; ++i) {
      ctx.close();
    }
    if (moreToCome) {
      ctx.endLine();
    }
  }
  @Override
  public void collectRequires(RequiresCollector collector) {
    for (IfThenPair child : conditions()) {
      child.predicate.collectRequires(collector);
      child.consequent.collectRequires(collector);
    }
    if (trailingElse() != null) {
      trailingElse().collectRequires(collector);
    }
  }
  private void formatIfClause(FormattingContext ctx) {
    IfThenPair first = conditions().get(0);
    first.predicate.formatInitialStatements(ctx, true /* moreToCome */);
    ctx.append("if (");
    first.predicate.formatOutputExpr(ctx, EXPRESSION);
    ctx.append(") ");
    try (FormattingContext ignored = ctx.enterBlock()) {
      first.consequent.formatAllStatements(ctx, false /* moreToCome */);
    }
  }

  /**
   * When the predicate of an {@code else if} clause is representable as a single expression,
   * format it directly as an {@code else if} clause.
   */
  private static void formatElseIfClauseWithNoDependencies(
      IfThenPair condition, FormattingContext ctx) {
    ctx.append(" else if (");
    condition.predicate.formatOutputExpr(ctx, EXPRESSION);
    ctx.append(") ");
    try (FormattingContext ignored = ctx.enterBlock()) {
      condition.consequent.formatAllStatements(ctx, false /* moreToCome */);
    }
  }

  /**
   * When the predicate of an {@code else if} clause is not representable as a single expression,
   * we need to conditionally evaluate its initial statements. We do this by opening
   * an {@code else} clause, dumping its initial statements there, then opening a nested
   * {@code if} statement where the predicate expression and consequent chunk go.
   * Since this opens two blocks while only closing one, the caller
   * ({@link CodeChunk#doFormatInitialStatements}) has to remember to close the extra block
   * at the end of formatting.
   */
  private static void formatElseIfClauseWithDependencies(
      IfThenPair condition, FormattingContext ctx) {
    ctx.append(" else ");
    try (FormattingContext ignored = ctx.enterBlock()) {
      condition.predicate.formatInitialStatements(ctx, true /* moreToCome */);
      ctx.append("if (");
      condition.predicate.formatOutputExpr(ctx, EXPRESSION);
      ctx.append(") ");
      // Most enterBlock callers use try-with-resources to automatically close the block,
      // but here, we need to leave the block open so that subsequent `else if` clauses
      // are chained appropriately. The block will be closed by
      // Conditional#doFormatInitialStatements.
      ctx.enterBlock();
      condition.consequent.formatAllStatements(ctx, false /* moreToCome */);
    }
  }

  private void formatElseClause(FormattingContext ctx) {
    if (trailingElse() == null) {
      return;
    }
    ctx.append(" else ");
    try (FormattingContext ignored = ctx.enterBlock()) {
      trailingElse().formatAllStatements(ctx, false /* moreToCome */);
    }
    ctx.endLine();
  }

  boolean everyBranchHasAValue() {
    for (IfThenPair condition : conditions()) {
      if (!(condition.consequent instanceof CodeChunk.WithValue)) {
        return false;
      }
    }
    return trailingElse() instanceof CodeChunk.WithValue;
  }
}
