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
import java.util.stream.Stream;

/**
 * Represents an anonymous JavaScript function declaration.
 *
 * <p>Example:
 *
 * <p><code>{@literal
 * function(param1, param2) { < function body > }
 * }</code>
 */
@AutoValue
public abstract class FunctionDeclaration extends Expression
    implements Expression.InitialStatementsScope {

  abstract JsDoc jsDoc();

  abstract CodeChunk body();

  abstract boolean isArrowFunction();

  public static FunctionDeclaration create(JsDoc jsDoc, Statement body) {
    return new AutoValue_FunctionDeclaration(jsDoc, body, false);
  }

  public static FunctionDeclaration createArrowFunction(JsDoc jsDoc, Statement body) {
    return new AutoValue_FunctionDeclaration(jsDoc, body, true);
  }

  public static FunctionDeclaration createArrowFunction(JsDoc jsDoc, Expression body) {
    return new AutoValue_FunctionDeclaration(jsDoc, body, true);
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.of(body(), jsDoc());
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    if (!isArrowFunction()) {
      ctx.append("function");
    }
    boolean paramsNeedParens = !isArrowFunction() || jsDoc().params().size() != 1;
    if (paramsNeedParens) {
      ctx.append("(");
    }
    ctx.append(
        CodeChunkUtils.generateParamList(
            jsDoc(), /* addInlineTypeAnnotations= */ isArrowFunction()));
    if (paramsNeedParens) {
      ctx.append(") ");
    }
    if (isArrowFunction()) {
      ctx.append(" => ");
    }
    if (isArrowFunction() && body() instanceof Expression) {
      Expression exprBody = (Expression) body();
      if (exprBody.isRepresentableAsSingleExpression()) {
        // protect with parens to avoid parsing ambiguity
        // see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Functions/Arrow_functions#Returning_object_literals
        if (exprBody.initialExpressionIsObjectLiteral()) {
          exprBody = Group.create(exprBody);
        }
        // simplified arrow function body
        ctx.appendOutputExpression(exprBody);
      } else {
        try (FormattingContext ignored = ctx.enterBlock()) {
          ctx.appendAll(Return.create(exprBody));
        }
      }
    } else {
      try (FormattingContext ignored = ctx.enterBlock()) {
        ctx.appendAll(body());
      }
    }
  }
}
