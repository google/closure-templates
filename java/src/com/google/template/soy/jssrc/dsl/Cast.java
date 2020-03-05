/*
 * Copyright 2019 Google Inc.
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
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.jssrc.restricted.JsExpr;
import java.util.function.Consumer;

/** Represents a JavaScript type cast. */
@AutoValue
@Immutable
abstract class Cast extends Expression {
  abstract Expression expr();

  abstract String typeExpr();

  abstract ImmutableSet<GoogRequire> googRequires();

  static Cast create(Expression expr, String typeExpr) {
    return new AutoValue_Cast(expr.initialStatements(), expr, typeExpr, ImmutableSet.of());
  }

  static Cast create(Expression expr, String typeExpr, ImmutableSet<GoogRequire> googRequires) {
    return new AutoValue_Cast(expr.initialStatements(), expr, typeExpr, googRequires);
  }

  @Override
  public JsExpr singleExprOrName() {
    FormattingContext ctx = new FormattingContext();
    doFormatOutputExpr(ctx);
    return new JsExpr(ctx.toString(), Integer.MAX_VALUE);
  }

  @Override
  public void collectRequires(Consumer<GoogRequire> collector) {
    for (GoogRequire require : googRequires()) {
      collector.accept(require);
    }
    expr().collectRequires(collector);
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.append("/** @type {" + typeExpr() + "} */ (").appendOutputExpression(expr()).append(')');
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    ctx.appendInitialStatements(expr());
  }

  @Override
  public boolean isCheap() {
    return expr().isCheap();
  }
}
