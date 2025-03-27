/*
 * Copyright 2023 Google Inc.
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
public abstract class JsArrowFunction extends Expression
    implements Expression.InitialStatementsScope {

  abstract JsDoc jsDoc();

  abstract Statement body();

  public static JsArrowFunction create(JsDoc jsDoc, Statement body) {
    return new AutoValue_JsArrowFunction(jsDoc, body);
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.of(body(), jsDoc());
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    boolean paramsNeedParens = jsDoc().params().size() != 1;
    if (paramsNeedParens) {
      ctx.append("(");
    }
    ctx.append(FunctionDeclaration.generateParamList(jsDoc(), true));
    if (paramsNeedParens) {
      ctx.append(")");
    }
    ctx.append(" => ");
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
    ctx.appendAllIntoBlock(body());
  }
}
