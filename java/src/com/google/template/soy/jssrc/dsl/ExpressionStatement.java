/*
 * Copyright 2018 Google Inc.
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
import javax.annotation.Nullable;

/** Evaluates an expression as a statement. */
@AutoValue
@Immutable
abstract class ExpressionStatement extends Statement {

  static ExpressionStatement of(Expression expression) {
    return of(expression, /* jsDoc= */ null);
  }

  static ExpressionStatement of(Expression expression, JsDoc jsDoc) {
    return new AutoValue_ExpressionStatement(expression, jsDoc);
  }

  abstract Expression expr();

  @Nullable
  abstract JsDoc jsDoc();

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    ctx.appendInitialStatements(expr());
    if (jsDoc() != null) {
      ctx.append(jsDoc()).endLine();
    }
    ctx.appendOutputExpression(expr());
    ctx.append(";");
    ctx.endLine();
  }

  @Override
  public void collectRequires(RequiresCollector collector) {
    expr().collectRequires(collector);
  }
}
