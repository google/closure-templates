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

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.Immutable;
import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Represents a single "name : type" tsx function param.
 *
 * <p>TODO: make this support js too, so both backends can use it?
 */
@AutoValue
@Immutable
public abstract class ParamDecl extends CodeChunk {

  abstract String name();

  @Nullable
  abstract Expression type();

  abstract boolean isOptional();

  @Nullable
  abstract Expression defaultValue();

  public static ParamDecl create(String name) {
    return new AutoValue_ParamDecl(name, null, false, null);
  }

  public static ParamDecl create(String name, Expression type) {
    return new AutoValue_ParamDecl(name, type, false, null);
  }

  public static ParamDecl create(String name, Expression type, boolean isOptional) {
    return new AutoValue_ParamDecl(name, type, isOptional, null);
  }

  public static ParamDecl create(String name, Expression type, Expression defaultValue) {
    return new AutoValue_ParamDecl(name, type, true, Preconditions.checkNotNull(defaultValue));
  }

  public String nameDecl(FormatOptions formatOptions) {
    return name()
        + (defaultValue() != null
            ? " = " + defaultValue().singleExprOrName(formatOptions).getText()
            : "");
  }

  public String typeDecl(FormatOptions formatOptions) {
    return name()
        + (isOptional() ? "?" : "")
        + (type() != null ? ": " + type().singleExprOrName(formatOptions).getText() : "");
  }

  @Override
  public final Statement asStatement() {
    throw new UnsupportedOperationException();
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.of(type(), defaultValue()).filter(Objects::nonNull);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    ctx.append(typeDecl(ctx.getFormatOptions()));
  }
}
