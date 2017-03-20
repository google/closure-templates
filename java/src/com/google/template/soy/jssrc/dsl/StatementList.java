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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/** Represents a sequence of statements. */
@AutoValue
abstract class StatementList extends CodeChunk {
  abstract ImmutableList<? extends CodeChunk> statements();

  static StatementList of(ImmutableList<? extends CodeChunk> statements) {
    Preconditions.checkState(
        statements.size() > 1, "list of size %s makes no sense", statements.size());
    return new AutoValue_StatementList(statements);
  }

  @Override
  public void collectRequires(RequiresCollector collector) {
    for (CodeChunk statement : statements()) {
      statement.collectRequires(collector);
    }
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    for (CodeChunk statement : statements()) {
      ctx.appendAll(statement);
    }
  }
}
