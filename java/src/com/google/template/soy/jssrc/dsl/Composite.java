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
import com.google.template.soy.jssrc.restricted.JsExpr;
import java.util.stream.Stream;

/** Represents an expression preceded by one or more initial statements. */
@AutoValue
@Immutable
abstract class Composite extends Expression implements Expression.HasInitialStatements {

  @Override
  public abstract ImmutableList<Statement> initialStatements();

  abstract Expression value();

  static Composite create(ImmutableList<Statement> initialStatements, Expression value) {
    Preconditions.checkState(!initialStatements.isEmpty());
    return new AutoValue_Composite(initialStatements, value);
  }

  @Override
  public final boolean isCheap() {
    return value().isCheap();
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.concat(initialStatements().stream(), Stream.of(value()));
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.appendOutputExpression(value());
  }

  @Override
  public JsExpr singleExprOrName(FormatOptions formatOptions) {
    return value().singleExprOrName(formatOptions);
  }
}
