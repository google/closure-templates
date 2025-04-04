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
import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Represents an {@code if}-{@code else if}-{@code else} statement. */
@AutoValue
@Immutable
abstract class Conditional extends Statement {
  abstract ImmutableList<IfThenPair<Statement>> conditions();

  @Nullable
  abstract Statement trailingElse();

  static Conditional create(
      ImmutableList<IfThenPair<Statement>> conditions, @Nullable Statement trailingElse) {
    Preconditions.checkArgument(!conditions.isEmpty());
    return new AutoValue_Conditional(conditions, trailingElse);
  }

  @Override
  void doFormatStatement(FormattingContext ctx) {
    formatIfClause(ctx);
    int numRightBracesToClose = 0;
    Expression firstPredicate = conditions().get(0).predicate;
    for (IfThenPair<Statement> condition : conditions().subList(1, conditions().size())) {
      if (firstPredicate.hasEquivalentInitialStatements(condition.predicate)) {
        formatElseIfClauseWithNoDependencies(condition, ctx);
      } else {
        formatElseIfClauseWithDependencies(condition, ctx);
        ++numRightBracesToClose;
      }
    }
    formatElseClause(ctx);
    // Explicitly close the extra blocks opened by formatElseIfClauseWithDependencies.
    for (int i = 0; i < numRightBracesToClose; ++i) {
      ctx.closeBlock();
    }
    ctx.endLine();
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.concat(
        Stream.of(trailingElse()).filter(Objects::nonNull),
        conditions().stream().flatMap(child -> Stream.of(child.predicate, child.consequent)));
  }

  @Override
  public boolean isTerminal() {
    if (conditions().stream().anyMatch(p -> p.predicate.equals(Expressions.LITERAL_TRUE))) {
      // Support {if true}{/if}... debugging idiom in Soy.
      return true;
    }
    return trailingElse() != null
        && trailingElse().isTerminal()
        && conditions().stream().allMatch(p -> p.consequent.isTerminal());
  }

  private void formatIfClause(FormattingContext ctx) {
    IfThenPair<Statement> first = conditions().get(0);
    ctx.appendInitialStatements(first.predicate)
        .append("if (")
        .appendOutputExpression(first.predicate)
        .append(") ");
    ctx.appendAllIntoBlock(first.consequent);
  }

  /**
   * When the predicate of an {@code else if} clause is representable as a single expression, format
   * it directly as an {@code else if} clause.
   */
  private static void formatElseIfClauseWithNoDependencies(
      IfThenPair<?> condition, FormattingContext ctx) {
    ctx.append(" else if (").appendOutputExpression(condition.predicate).append(") ");
    ctx.appendAllIntoBlock(condition.consequent);
  }

  /**
   * When the predicate of an {@code else if} clause is not representable as a single expression, we
   * need to conditionally evaluate its initial statements. We do this by opening an {@code else}
   * clause, dumping its initial statements there, then opening a nested {@code if} statement where
   * the predicate expression and consequent chunk go. Since this opens two blocks while only
   * closing one, the caller ({@link CodeChunk#doFormatInitialStatements}) has to remember to close
   * the extra block at the end of formatting.
   */
  private static void formatElseIfClauseWithDependencies(
      IfThenPair<?> condition, FormattingContext ctx) {
    ctx.append(" else ");
    ctx.enterBlock();
    ctx.appendInitialStatements(condition.predicate)
        .append("if (")
        .appendOutputExpression(condition.predicate)
        .append(") ");
    ctx.appendAllIntoBlock(condition.consequent);

    // Leave the outer block open so that subsequent `else if` clauses are chained appropriately.
    // The block will be closed by Conditional#doFormatInitialStatements.
  }

  private void formatElseClause(FormattingContext ctx) {
    if (trailingElse() == null) {
      return;
    }
    ctx.append(" else ");
    ctx.appendAllIntoBlock(trailingElse());
    ctx.endLine();
  }
}
