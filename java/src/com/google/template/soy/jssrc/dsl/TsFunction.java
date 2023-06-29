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
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Represents an TS function expression, either an anonymous function or an arrow function.
 *
 * <p>Examples:
 *
 * <p><code>{@literal
 * function (param: string): string { ... }
 * }</code>
 *
 * <p><code>{@literal
 * (param: string): string => { ... }
 * }</code>
 */
@AutoValue
abstract class TsFunction extends Expression implements Expression.InitialStatementsScope {

  public enum Format {
    ANONYMOUS,
    ARROW
  }

  static TsFunction anonymous(
      ParamDecls params, Expression returnType, List<Statement> statements) {
    return new AutoValue_TsFunction(
        Format.ANONYMOUS,
        params,
        Preconditions.checkNotNull(returnType),
        ImmutableList.copyOf(statements));
  }

  static TsFunction arrow(ParamDecls params, List<Statement> statements) {
    return new AutoValue_TsFunction(Format.ARROW, params, null, ImmutableList.copyOf(statements));
  }

  static TsFunction arrow(ParamDecls params, Expression returnType, List<Statement> statements) {
    return new AutoValue_TsFunction(
        Format.ARROW,
        params,
        Preconditions.checkNotNull(returnType),
        ImmutableList.copyOf(statements));
  }

  abstract Format format();

  abstract ParamDecls params();

  @Nullable
  abstract Expression returnType();

  abstract ImmutableList<Statement> bodyStmts();

  /**
   * If the body is a single return statement of a single expression, return that expresion,
   * otherwise return null.
   */
  @Nullable
  private Expression getSingleExpression() {
    if (bodyStmts().size() != 1 || !(bodyStmts().get(0) instanceof Return)) {
      return null;
    }
    Expression exprBody = ((Return) bodyStmts().get(0)).value();
    if (!exprBody.isRepresentableAsSingleExpression()) {
      return null;
    }
    if (exprBody.initialExpressionIsObjectLiteral()) {
      return Group.create(exprBody);
    }
    return exprBody;
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    boolean anon = format() == Format.ANONYMOUS;
    ctx.append(anon ? "function(" : "(").appendOutputExpression(params()).append(")");
    if (returnType() != null) {
      ctx.noBreak().append(": ");
      ctx.appendOutputExpression(returnType());
    }
    Expression singleExpression = null;
    if (!anon) {
      ctx.noBreak().append(" => ");
      singleExpression = getSingleExpression();
    }
    if (singleExpression != null) {
      ctx.appendOutputExpression(singleExpression);
    } else {
      ctx.enterBlock();
      for (CodeChunk stmt : bodyStmts()) {
        ctx.appendAll(stmt);
        ctx.endLine();
      }
      ctx.close();
    }
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    Stream<? extends CodeChunk> s = Stream.concat(bodyStmts().stream(), params().childrenStream());
    if (returnType() != null) {
      s = Stream.concat(s, Stream.of(returnType()));
    }
    return s;
  }
}
