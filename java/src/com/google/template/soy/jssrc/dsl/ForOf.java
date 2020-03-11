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
import java.util.function.Consumer;

/** Represents a {@code for in} statement. */
@AutoValue
@Immutable
abstract class ForOf extends Statement {

  abstract String localVar();

  abstract Expression collection();

  abstract Statement body();

  static ForOf create(String localVar, Expression collection, Statement body) {
    return new AutoValue_ForOf(localVar, collection, body);
  }

  @Override
  public void collectRequires(Consumer<GoogRequire> collector) {
    collection().collectRequires(collector);
    body().collectRequires(collector);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    ctx.appendInitialStatements(collection());

    ctx.append("for (const " + localVar())
        .append(" of ")
        .appendOutputExpression(collection())
        .append(") ");

    try (FormattingContext ignored = ctx.enterBlock()) {
      ctx.appendAll(body());
    }
  }
}
