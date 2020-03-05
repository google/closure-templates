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

/** Represents a {@code do {....} while(...);} loop. */
@AutoValue
@Immutable
public abstract class DoWhile extends Statement {

  public static Builder builder() {
    return new AutoValue_DoWhile.Builder();
  }

  abstract Statement body();

  abstract Expression condition();

  @Override
  public void collectRequires(Consumer<GoogRequire> collector) {
    condition().collectRequires(collector);
    body().collectRequires(collector);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    ctx.appendInitialStatements(condition());

    ctx.append("do ");
    try (FormattingContext ignored = ctx.enterBlock()) {
      ctx.appendAll(body());
    }
    ctx.append(" while (").appendOutputExpression(condition()).append(");");
    ctx.endLine();
  }

  /** A builder for a {@link DoWhile}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setCondition(Expression condition);

    public abstract Builder setBody(Statement condition);

    public abstract DoWhile build();
  }
}
