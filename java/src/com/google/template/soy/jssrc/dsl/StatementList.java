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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.stream.Stream;

/** Represents a sequence of statements. */
@AutoValue
public abstract class StatementList extends Statement {
  private static final StatementList EMPTY = new AutoValue_StatementList(ImmutableList.of());

  abstract ImmutableList<? extends Statement> statements();

  public static StatementList create(Iterable<? extends Statement> statements) {
    if (Iterables.isEmpty(statements)) {
      return EMPTY;
    }
    // unroll the statement list so it is flat
    // otherwise ctx.appendAll will add extra newlines and semi colons.
    // TODO(lukes): newlines and semicolons are handled in an extremely haphazard way...we should
    // come up with some kind of coherent strategy.  Leaf nodes like this should probably be
    // responsible for adding these things rather than FormattingContext
    ImmutableList.Builder<Statement> unrolled = ImmutableList.builder();
    for (Statement statement : statements) {
      if (statement instanceof StatementList) {
        unrolled.addAll(((StatementList) statement).statements());
      } else {
        unrolled.add(statement);
      }
    }
    return new AutoValue_StatementList(unrolled.build());
  }

  @Override
  public boolean isEmpty() {
    return statements().isEmpty();
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return statements().stream();
  }

  @Override
  void doFormatStatement(FormattingContext ctx) {
    for (Statement statement : statements()) {
      ctx.appendAll(statement);
    }
  }

  @Override
  public boolean isTerminal() {
    return statements().stream().anyMatch(Statement::isTerminal);
  }
}
