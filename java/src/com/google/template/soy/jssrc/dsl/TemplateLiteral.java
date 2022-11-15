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
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.jssrc.restricted.JsExpr;
import java.util.function.Consumer;

/** Represents a JavaScript template literal expression. */
@AutoValue
@Immutable
public abstract class TemplateLiteral extends Expression {

  abstract ImmutableList<Statement> body();

  public static TemplateLiteral create(ImmutableList<Statement> body) {
    return new AutoValue_TemplateLiteral(/* initialStatements= */ ImmutableList.of(), body);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.pushInterpolationKind(FormattingContext.InterpolationKind.TTL);

    ctx.append('`');
    for (Statement s : body()) {
      ctx.appendAll(s);
    }
    ctx.append('`');

    ctx.popInterpolationKind();
  }

  @Override
  public JsExpr singleExprOrName() {
    FormattingContext ctx = new FormattingContext();
    ctx.appendOutputExpression(this);
    return new JsExpr(ctx.toString(), Integer.MAX_VALUE);
  }

  @Override
  public void collectRequires(Consumer<GoogRequire> collector) {
    for (Statement s : body()) {
      s.collectRequires(collector);
    }
  }
}
