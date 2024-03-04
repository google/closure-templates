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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.template.soy.base.SourceLocation.ByteSpan;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Represents a named TS function declaration.
 *
 * <p>Example:
 *
 * <p><code>{@literal
 * function foo(param1: string, param2: number): number { < function body > }
 * }</code>
 */
@AutoValue
public abstract class NamedFunctionDeclaration extends Statement {

  abstract String name();

  abstract ParamDecls params();

  abstract Expression returnType();

  abstract Optional<JsDoc> jsDoc();

  @Nullable
  abstract ImmutableList<Statement> bodyStmts();

  abstract boolean isExported();

  abstract boolean isDeclaration();

  @Nullable
  abstract ByteSpan byteSpan();

  public static Builder builder(String name, ParamDecls params, Expression returnType) {
    return new AutoValue_NamedFunctionDeclaration.Builder()
        .setName(name)
        .setParams(params)
        .setReturnType(returnType)
        .setJsDoc(Optional.empty())
        .setBodyStmts(null)
        .setIsExported(false)
        .setIsDeclaration(false)
        .setByteSpan(null);
  }

  public static Builder builder(
      String name, ParamDecls params, Expression returnType, JsDoc jsDoc) {
    return new AutoValue_NamedFunctionDeclaration.Builder()
        .setName(name)
        .setParams(params)
        .setReturnType(returnType)
        .setJsDoc(Optional.ofNullable(jsDoc))
        .setBodyStmts(null)
        .setIsExported(false)
        .setIsDeclaration(false)
        .setByteSpan(null);
  }

  @Override
  void doFormatStatement(FormattingContext ctx) {
    if (jsDoc().isPresent()) {
      ctx.appendAll(jsDoc().get());
    }
    ctx.endLine();
    if (isExported()) {
      ctx.append("export ");
    }
    if (isDeclaration()) {
      ctx.append("declare ");
    }
    ctx.append("function ");
    ctx.appendImputee(name(), byteSpan());
    ctx.append("(");
    ctx.appendOutputExpression(params());
    ctx.append("): ").appendOutputExpression(returnType());
    if (bodyStmts() != null) {
      ctx.append(" ");
      ctx.enterBlock();
      for (Statement stmt : bodyStmts()) {
        ctx.appendAll(stmt);
        ctx.endLine();
      }
      ctx.close();
    } else {
      ctx.noBreak().append(";");
    }
    ctx.endLine();
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Streams.concat(
        bodyStmts() != null ? bodyStmts().stream() : Stream.of(),
        Stream.of(params(), returnType(), jsDoc().orElse(null)).filter(Objects::nonNull));
  }

  /** A builder for a {@link NamedFunctionDeclaration}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setName(String name);

    public abstract Builder setParams(ParamDecls params);

    public abstract Builder setReturnType(Expression returnType);

    public abstract Builder setJsDoc(Optional<JsDoc> jsDoc);

    public abstract Builder setBodyStmts(ImmutableList<Statement> bodyStmts);

    public abstract Builder setIsExported(boolean isExported);

    public abstract Builder setIsDeclaration(boolean isDeclaration);

    public abstract Builder setByteSpan(ByteSpan soySpan);

    public abstract NamedFunctionDeclaration build();
  }
}
