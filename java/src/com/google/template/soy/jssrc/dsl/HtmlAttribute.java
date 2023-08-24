/*
 * Copyright 2022 Google Inc.
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

import static com.google.template.soy.jssrc.dsl.Expressions.stringLiteral;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.jssrc.dsl.FormattingContext.LexicalState;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Represents an Html attribute. */
@AutoValue
@Immutable
public abstract class HtmlAttribute extends Expression {

  abstract String name();

  @Nullable
  abstract Expression value();

  public static HtmlAttribute create(String name, @Nullable Expression value) {
    if (value != null && !(value instanceof StringLiteral)) {
      value = TsxPrintNode.wrap(value);
    }
    return new AutoValue_HtmlAttribute(name, value);
  }

  public static HtmlAttribute create(String name, @Nullable String value) {
    return create(
        name,
        value != null
            ? stringLiteral(value, value.contains("'") ? QuoteStyle.DOUBLE : QuoteStyle.SINGLE)
            : null);
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.append(name());
    if (value() != null) {
      ctx.pushLexicalState(LexicalState.TSX_ATTR);
      ctx.append("=");
      ctx.appendOutputExpression(value());
      ctx.popLexicalState();
    }
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return value() != null ? Stream.of(value()) : Stream.empty();
  }

}
