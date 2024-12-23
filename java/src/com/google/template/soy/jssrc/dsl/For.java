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
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import java.util.stream.Stream;

/** Represents a {@code for} statement. */
@AutoValue
@Immutable
abstract class For extends Statement {

  abstract Id localVar();

  abstract Expression initial();

  abstract Expression limit();

  abstract Expression increment();

  abstract Statement body();

  static For create(
      Id localVar, Expression initial, Expression limit, Expression increment, Statement body) {
    return new AutoValue_For(localVar, initial, limit, increment, body);
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.of(initial(), limit(), increment(), body());
  }

  @Override
  void doFormatStatement(FormattingContext ctx) {
    ctx.appendInitialStatements(initial())
        .appendInitialStatements(limit())
        .appendInitialStatements(increment());

    ctx.append("for (let ")
        .appendOutputExpression(localVar())
        .append(" = ")
        .appendOutputExpression(initial())
        .append("; ")
        .appendOutputExpression(localVar())
        .append(" < ")
        .appendOutputExpression(limit())
        .append("; ");

    if (Objects.equals(Expressions.getLeafText(increment()), "1")) {
      ctx.appendOutputExpression(localVar()).append("++");
    } else {
      ctx.appendOutputExpression(localVar()).append(" += ").appendOutputExpression(increment());
    }

    ctx.append(") ");

    try (FormattingContext ignored = ctx.enterBlock()) {
      ctx.appendAll(body());
    }
    ctx.endLine();
  }
}
