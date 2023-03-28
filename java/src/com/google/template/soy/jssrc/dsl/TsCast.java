/*
 * Copyright 2019 Google Inc.
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
import com.google.errorprone.annotations.Immutable;
import java.util.stream.Stream;

/** Represents a TypeScript type cast. */
@AutoValue
@Immutable
abstract class TsCast extends Expression {

  static TsCast create(Expression expr, Expression type) {
    return new AutoValue_TsCast(expr, type);
  }

  abstract Expression expr();

  abstract Expression type();

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.of(expr(), type());
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.appendOutputExpression(expr()).append(" as ").appendOutputExpression(type());
  }
}
