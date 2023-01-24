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
import com.google.template.soy.jssrc.dsl.JsDoc.Param;
import java.util.ArrayList;
import java.util.List;
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

  /**
   * Outputs a stringified parameter list (e.g. `foo, bar, baz`) from JsDoc. Used e.g. in function
   * and method declarations.
   */
  static String generateParamList(JsDoc jsDoc, boolean addInlineTypeAnnotations) {
    ImmutableList<Param> params = jsDoc.params();
    List<String> functionParameters = new ArrayList<>();
    for (Param param : params) {
      if (param.annotationType().equals("param")) {
        if (addInlineTypeAnnotations) {
          functionParameters.add(String.format("/* %s */ %s", param.type(), param.paramTypeName()));
        } else {
          functionParameters.add(param.paramTypeName());
        }
      }
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < functionParameters.size(); i++) {
      sb.append(functionParameters.get(i));
      if (i + 1 < functionParameters.size()) {
        sb.append(", ");
      }
    }
    return sb.toString();
  }

  abstract JsDoc jsDoc();

  abstract Statement body();

  abstract boolean isArrowFunction();

  public static FunctionDeclaration create(JsDoc jsDoc, Statement body) {
    return new AutoValue_FunctionDeclaration(jsDoc, body, false);
  }

  public static FunctionDeclaration createArrowFunction(JsDoc jsDoc, Statement body) {
    return new AutoValue_FunctionDeclaration(jsDoc, body, true);
  }

  public static FunctionDeclaration createArrowFunction(JsDoc jsDoc, Expression body) {
    return createArrowFunction(jsDoc, Statements.returnValue(body));
  }

  public static FunctionDeclaration createArrowFunction(Expression body) {
    return createArrowFunction(JsDoc.getDefaultInstance(), Statements.returnValue(body));
  }

  public static FunctionDeclaration createArrowFunction(List<Statement> statements) {
    return createArrowFunction(JsDoc.getDefaultInstance(), Statements.of(statements));
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
    ctx.append(generateParamList(jsDoc(), /* addInlineTypeAnnotations= */ isArrowFunction()));
    if (paramsNeedParens) {
      ctx.append(")");
    }
    if (isArrowFunction()) {
      ctx.append(" => ");
    } else {
      ctx.append(" ");
    }
    if (isArrowFunction()) {
      if (body() instanceof Return) {
        Expression exprBody = ((Return) body()).value();
        if (exprBody.isRepresentableAsSingleExpression()) {
          if (exprBody.initialExpressionIsObjectLiteral()) {
            exprBody = Group.create(exprBody);
          }
          ctx.appendOutputExpression(exprBody);
          return;
        }
      }
      try (FormattingContext ignored = ctx.enterBlock()) {
        ctx.appendAll(body());
      }
    } else {
      try (FormattingContext ignored = ctx.enterBlock()) {
        ctx.appendAll(body());
      }
    }
  }
}
