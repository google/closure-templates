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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/** Builds a single {@link Conditional}. */
public final class ConditionalBuilder {

  private final ImmutableList.Builder<IfThenPair<Statement>> conditions = ImmutableList.builder();

  @Nullable private Statement trailingElse = null;

  ConditionalBuilder(Expression predicate, Statement consequent) {
    conditions.add(new IfThenPair<>(predicate, consequent));
  }

  /** Adds an {@code else if} clause with the given predicate and consequent to this conditional. */
  public ConditionalBuilder addElseIf(Expression predicate, Statement consequent) {
    conditions.add(new IfThenPair<>(predicate, consequent));
    return this;
  }

  /** Adds an {@code else} clause encapsulating the given chunk to this conditional. */
  public ConditionalBuilder setElse(Statement trailingElse) {
    Preconditions.checkState(this.trailingElse == null);
    this.trailingElse = trailingElse;
    return this;
  }

  /** Finishes building this conditional. */
  @CheckReturnValue
  public Statement build() {
    return Conditional.create(conditions.build(), trailingElse);
  }
}
