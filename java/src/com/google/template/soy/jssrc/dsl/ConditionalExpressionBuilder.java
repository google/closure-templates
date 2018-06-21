/*
 * Copyright 2017 Google Inc.
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

import static com.google.template.soy.jssrc.dsl.Statement.ifStatement;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.template.soy.jssrc.dsl.CodeChunk.Generator;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * Builds a single {@link Conditional conditional expression}.
 *
 * <p>In contrast with {@link ConditionalBuilder}, this class requires the whole conditional to
 * represent a value, and {@link #build(Generator)} returns a {@link Expression} representing that
 * value.
 */
public final class ConditionalExpressionBuilder {

  private final ImmutableList.Builder<IfThenPair<Expression>> conditions = ImmutableList.builder();
  private Expression trailingElse;

  ConditionalExpressionBuilder(Expression predicate, Expression consequent) {
    conditions.add(new IfThenPair<>(predicate, consequent));
  }

  public ConditionalExpressionBuilder addElseIf(Expression predicate, Expression consequent) {
    conditions.add(new IfThenPair<>(predicate, consequent));
    return this;
  }

  public ConditionalExpressionBuilder setElse(Expression trailingElse) {
    Preconditions.checkState(this.trailingElse == null);
    this.trailingElse = trailingElse;
    return this;
  }

  @Nullable
  private Expression tryCreateTernary(ImmutableList<IfThenPair<Expression>> pairs) {
    if (pairs.size() != 1 || trailingElse == null) {
      return null;
    }

    IfThenPair<Expression> ifThen = Iterables.getOnlyElement(pairs);
    Expression predicate = ifThen.predicate;
    Expression consequent = ifThen.consequent;
    // TODO(lukes): we could support nested ternaries with little additional difficulty
    if (predicate.initialStatements().containsAll(consequent.initialStatements())
        && predicate.initialStatements().containsAll(trailingElse.initialStatements())) {
      return Ternary.create(predicate, consequent, trailingElse);
    }
    return null;
  }

  /** Finishes building this conditional. */
  @CheckReturnValue
  public Expression build(CodeChunk.Generator codeGenerator) {
    ImmutableList<IfThenPair<Expression>> pairs = conditions.build();
    Expression ternary = tryCreateTernary(pairs);
    if (ternary != null) {
      return ternary;
    }
    // Otherwise we need to introduce a temporary and assign to it in each branch
    VariableDeclaration decl = codeGenerator.declarationBuilder().build();
    Expression var = decl.ref();
    ConditionalBuilder builder = null;
    for (IfThenPair<Expression> oldCondition : pairs) {
      Expression newConsequent = var.assign(oldCondition.consequent);
      if (builder == null) {
        builder = ifStatement(oldCondition.predicate, newConsequent.asStatement());
      } else {
        builder.addElseIf(oldCondition.predicate, newConsequent.asStatement());
      }
    }
    if (trailingElse != null) {
      builder.setElse(var.assign(trailingElse).asStatement());
    }
    return var.withInitialStatements(ImmutableList.of(decl, builder.build()));
  }
}
