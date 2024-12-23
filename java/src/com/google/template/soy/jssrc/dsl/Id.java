/*
 * Copyright 2024 Google Inc.
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
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.ForOverride;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.base.SourceLocation.ByteSpan;
import com.google.template.soy.jssrc.dsl.Precedence.Associativity;
import com.google.template.soy.jssrc.restricted.JsExpr;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** A JavaScript identifier. */
@AutoValue
@Immutable
public abstract class Id extends Expression implements OperatorInterface, CodeChunk.HasRequires {

  public static Id create(String id) {
    return builder(id).build();
  }

  public static Builder builder(String id) {
    return new AutoValue_Id.Builder().setId(id).setGoogRequires(ImmutableSet.of());
  }

  public abstract String id();

  @Nullable
  public abstract ByteSpan span();

  @Override
  public abstract ImmutableSet<GoogRequire> googRequires();

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.appendImputee(id(), span());
  }

  @Override
  public JsExpr singleExprOrName(FormatOptions formatOptions) {
    // throw new RuntimeException();
    return new JsExpr(id(), Integer.MAX_VALUE);
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.of();
  }

  @Override
  public Precedence precedence() {
    return Precedence.MAX;
  }

  @Override
  public Associativity associativity() {
    return Associativity.UNARY;
  }

  @Override
  public boolean isCheap() {
    return true;
  }

  public abstract Builder toBuilder();

  /** A builder for a {@link Id}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setId(String id);

    public abstract Builder setSpan(ByteSpan span);

    public abstract Builder setGoogRequires(ImmutableSet<GoogRequire> requires);

    @ForOverride
    abstract Id autoBuild();

    public Id build() {
      Id built = autoBuild();
      CodeChunks.checkId(built.id());
      return built;
    }
  }
}
