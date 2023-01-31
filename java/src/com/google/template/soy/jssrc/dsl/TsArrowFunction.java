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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.jssrc.restricted.JsExpr;
import java.util.List;
import java.util.Optional;
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
public class TsArrowFunction extends Expression implements Expression.InitialStatementsScope {

  private final ParamDecls params;
  private final Optional<Expression> returnType;
  private final ImmutableList<Statement> bodyStmts;

  /** Arrow function with implicit return type. */
  TsArrowFunction(ParamDecls params, List<Statement> bodyStmts) {
    this.params = params;
    this.returnType = Optional.empty();
    this.bodyStmts = ImmutableList.copyOf(bodyStmts);
  }

  /** Arrow function with explicit return type. */
  TsArrowFunction(ParamDecls params, Expression returnType, List<Statement> bodyStmts) {
    this.params = params;
    this.returnType = Optional.of(returnType);
    this.bodyStmts = ImmutableList.copyOf(bodyStmts);
  }

  /**
   * If the body is a single return statement of a single expression, return that expresion,
   * otherwise return null.
   */
  @Nullable
  private Expression getSingleExpression() {
    if (bodyStmts.size() != 1 || !(bodyStmts.get(0) instanceof Return)) {
      return null;
    }
    Expression exprBody = ((Return) bodyStmts.get(0)).value();
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
    try (FormattingContext buffer = ctx.buffer()) {
      buffer.append("(").appendAll(params).append(")");
      if (returnType.isPresent()) {
        buffer.append(": ");
        buffer.appendOutputExpression(returnType.get());
      }
      buffer.append(" => ");
    }
    Expression singleExpression = getSingleExpression();
    if (singleExpression != null) {
      ctx.appendOutputExpression(singleExpression);
    } else {
      ctx.enterBlock();
      for (CodeChunk stmt : bodyStmts) {
        ctx.appendAll(stmt);
        ctx.endLine();
      }
      ctx.close();
    }
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return bodyStmts.stream();
  }

  @Override
  public JsExpr singleExprOrName(FormatOptions formatOptions) {
    // UnsupportedOperationException, essentially.
    return new JsExpr("$$SOY_INTERNAL_ERROR_EXPR", Integer.MAX_VALUE);
  }
}
