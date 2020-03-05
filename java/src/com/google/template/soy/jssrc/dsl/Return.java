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
import com.google.errorprone.annotations.Immutable;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/** Represents a JavaScript return statement. */
@AutoValue
@Immutable
abstract class Return extends Statement {
  private static final Return EMPTY_RETURN = new AutoValue_Return(null);

  @Nullable
  abstract Expression value();

  static Return create(Expression value) {
    return new AutoValue_Return(value);
  }

  /** Creates an empty (no return value) return statement. */
  static Return create() {
    return EMPTY_RETURN;
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    if (value() != null) {
      ctx.appendInitialStatements(value());
    }
    ctx.append("return");
    if (value() != null) {
      ctx.append(" ").appendOutputExpression(value());
    }
    ctx.append(";");
  }

  @Override
  public void collectRequires(Consumer<GoogRequire> collector) {
    if (value() != null) {
      value().collectRequires(collector);
    }
  }
}
