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
public abstract class ParamDecl extends Expression {

  public abstract String name();

  @Nullable
  public abstract String alias();

  @Nullable
  public abstract Expression type();

  abstract boolean optional();

  @Nullable
  abstract Expression defaultValue();

  public static ParamDecl create(String name) {
    return builder(name).build();
  }

  public static ParamDecl create(String name, Expression type) {
    return builder(name).setType(type).build();
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.of(type(), defaultValue()).filter(Objects::nonNull);
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.append(name());
    if (optional()) {
      ctx.noBreak().append("?");
    }
    if (type() != null) {
      ctx.noBreak().append(": ").appendOutputExpression(type());
    }
  }

  public static Builder builder(String name) {
    return new AutoValue_ParamDecl.Builder().setName(name).setOptional(false);
  }

  /** Builder. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract ParamDecl.Builder setName(String name);

    public abstract ParamDecl.Builder setAlias(String alias);

    public abstract ParamDecl.Builder setType(Expression type);

    public abstract ParamDecl.Builder setOptional(boolean optional);

    public abstract ParamDecl.Builder setDefaultValue(Expression defaultValue);

    public abstract ParamDecl build();
  }
}
