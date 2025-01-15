/*
 * Copyright 2018 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.jssrc.dsl.Statements.DecoratedStatement;
import java.util.List;

/**
 * Subclass of {@link CodeChunk} that compile to one or more JavaScript statements.
 *
 * <p>It should be the case that any Statement will start and end in the same lexical scope.
 */
@Immutable
public abstract class Statement extends CodeChunk {

  Statement() {}

  @Override
  final void doFormatInitialStatements(FormattingContext ctx) {
    doFormatStatement(ctx);
  }

  @Override
  public final Statement asStatement() {
    return this;
  }

  public Expression asExpr() {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " cannot be represented as an expression");
  }

  /** Appends this statement to the {@link FormattingContext}. */
  abstract void doFormatStatement(FormattingContext ctx);

  /**
   * Returns whether this statement interrupts control, causing subsequent statements to be
   * unreachable.
   */
  public boolean isTerminal() {
    return false;
  }

  /** Creates a new statement by appending special tokens after this statement. */
  public Statement append(List<SpecialToken> tokens) {
    return DecoratedStatement.create(this, tokens, ImmutableList.of());
  }

  /** Creates a new statement by prepending special tokens before this statement. */
  public Statement prepend(List<SpecialToken> tokens) {
    return DecoratedStatement.create(this, ImmutableList.copyOf(tokens), ImmutableList.of());
  }

  public final Statement prepend(SpecialToken... tokens) {
    return prepend(ImmutableList.copyOf(tokens));
  }
}
