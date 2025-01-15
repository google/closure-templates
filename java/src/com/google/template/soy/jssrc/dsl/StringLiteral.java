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
  public boolean isDefinitelyNotNull() {
    return true;
  }

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
    ctx.appendQuotedString(literalValue(), quoteStyle());
  }

  @Override
  public Optional<String> asStringLiteral() {
    return Optional.of(literalValue());
  }
}
