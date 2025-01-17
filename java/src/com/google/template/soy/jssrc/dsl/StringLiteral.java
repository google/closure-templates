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
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SourceLocation.ByteSpan;
import com.google.template.soy.base.internal.QuoteStyle;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Represents a string literal expression. */
@AutoValue
@Immutable
public abstract class StringLiteral extends Expression {

  static Expression create(String literalValue) {
    return builder(literalValue).build();
  }

  public abstract String value();

  abstract QuoteStyle quoteStyle();

  @Nullable
  public abstract SourceLocation.ByteSpan span();

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
    ctx.appendQuotedString(value(), quoteStyle(), span());
  }

  @Override
  public Optional<String> asStringLiteral() {
    return Optional.of(value());
  }

  public static Builder builder(String literalValue) {
    return new AutoValue_StringLiteral.Builder()
        .setValue(literalValue)
        .setQuoteStyle(QuoteStyle.SINGLE);
  }

  public abstract Builder toBuilder();

  /** A builder for a {@link Id}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setValue(String id);

    public abstract Builder setQuoteStyle(QuoteStyle style);

    public abstract Builder setSpan(ByteSpan span);

    public abstract StringLiteral build();
  }
}
