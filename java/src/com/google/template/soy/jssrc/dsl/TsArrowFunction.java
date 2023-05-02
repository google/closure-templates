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
 * Represents a TS arrow function expression.
 *
 * <p>Example:
 *
 * <p><code>{@literal
 * (param: string): string => { ... }
 * }</code>
 */
@AutoValue
abstract class TsArrowFunction extends Expression implements Expression.InitialStatementsScope {

  static TsArrowFunction create(ParamDecls params, List<Statement> statements) {
    return new AutoValue_TsArrowFunction(params, null, ImmutableList.copyOf(statements));
  }

  static TsArrowFunction create(
      ParamDecls params, Expression returnType, List<Statement> statements) {
    return new AutoValue_TsArrowFunction(
        params, Preconditions.checkNotNull(returnType), ImmutableList.copyOf(statements));
  }

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
    ctx.append("(").appendOutputExpression(params()).append(")");
    if (returnType() != null) {
      ctx.noBreak().append(": ");
      ctx.appendOutputExpression(returnType());
    }
    ctx.noBreak().append(" => ");
    Expression singleExpression = getSingleExpression();
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
    Stream<? extends CodeChunk> s = bodyStmts().stream();
    if (returnType() != null) {
      s = Stream.concat(s, Stream.of(returnType()));
    }
    return s;
  }
}
