/*
 * Copyright 2018 Google Inc.
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
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.QuoteStyle;
import java.util.Optional;
import java.util.stream.Stream;

/** Represents a string literal expression. */
@AutoValue
@Immutable
public abstract class StringLiteral extends Expression {

  static Expression create(String literalValue) {
    return new AutoValue_StringLiteral(literalValue, QuoteStyle.SINGLE);
  }

  static Expression create(String literalValue, QuoteStyle quoteStyle) {
    return new AutoValue_StringLiteral(literalValue, quoteStyle);
  }

  public abstract String literalValue();

  abstract QuoteStyle quoteStyle();

  @Override
  public boolean isCheap() {
    return true;
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.empty();
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    if (ctx.stringNeedsQuotation()) {
      String quoted = quoteAndEscape(literalValue(), ctx.getFormatOptions());
      if (quoteStyle() == QuoteStyle.BACKTICK && quoted.contains("\n")) {
        ctx.appendWithoutBreaks(quoted);
      } else {
        ctx.append(quoted);
      }
    } else {
      ctx.append(literalValue());
    }
  }

  private String quoteAndEscape(String literal, FormatOptions formatOptions) {
    // Escape non-ASCII characters since browsers are inconsistent in how they interpret utf-8 in
    // JS source files.
    String escaped =
        BaseUtils.escapeToWrappedSoyString(
            literal, formatOptions.htmlEscapeStrings(), quoteStyle());

    // </script in a JavaScript string will end the current script tag in most browsers. Escape the
    // forward slash in the string to get around this issue.
    return escaped.replace("</script", "<\\/script");
  }

  @Override
  public Optional<String> asStringLiteral() {
    return Optional.of(literalValue());
  }
}
