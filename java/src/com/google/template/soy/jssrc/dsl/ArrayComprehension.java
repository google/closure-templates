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
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.jssrc.restricted.JsExpr;
import javax.annotation.Nullable;

/** Represents a JavaScript array comprehension expression. */
@AutoValue
@Immutable
abstract class ArrayComprehension extends Expression {

  abstract Expression listExpr();

  abstract Expression itemTransformExpr();

  abstract Expression iterVarTranslation();

  @Nullable
  abstract Expression filterExpr();

  static ArrayComprehension create(
      Expression listExpr,
      Expression itemTransformExpr,
      Expression iterVarTranslation,
      @Nullable Expression filterExpr) {
    return new AutoValue_ArrayComprehension(
        listExpr.initialStatements(), listExpr, itemTransformExpr, iterVarTranslation, filterExpr);
  }

  @Override
  public void collectRequires(RequiresCollector collector) {
    iterVarTranslation().collectRequires(collector);
    listExpr().collectRequires(collector);
    itemTransformExpr().collectRequires(collector);

    if (filterExpr() != null) {
      filterExpr().collectRequires(collector);
    }
  }

  @Override
  public JsExpr singleExprOrName() {
    FormattingContext ctx = new FormattingContext();
    doFormatOutputExpr(ctx);
    return new JsExpr(ctx.toString(), Integer.MAX_VALUE);
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {

    // Appends "$listExpr
    listExpr().doFormatOutputExpr(ctx);
    ctx.endLine();
    ctx.increaseIndent(2);

    // If the comprehension has a filter, append:
    // ".filter(iterVar => { filterInitialStatements; return filterExpr;})"
    if (filterExpr() != null) {
      ctx.append(".filter(");
      ctx.endLine();
      ctx.increaseIndent(2);
      iterVarTranslation().doFormatOutputExpr(ctx);
      ctx.append(" => {");
      ctx.endLine();
      ctx.increaseIndent(2);
      ctx.appendInitialStatements(filterExpr());
      ctx.append("return ");
      filterExpr().doFormatOutputExpr(ctx);
      ctx.append(";})");
      ctx.endLine();
      ctx.decreaseIndent(4);
    }

    // Append ".map(iterVar => { itemTransformExprInitialStatements; return itemTransformExpr;})"
    ctx.append(".map(");
    ctx.endLine();
    ctx.increaseIndent(2);

    // Appends "$iterVar => {"
    iterVarTranslation().doFormatOutputExpr(ctx);
    ctx.append(" => {");
    ctx.endLine();
    ctx.increaseIndent(2);

    // Appends any initial statements for the item map expr (if it can't be done in one js expr).
    ctx.appendInitialStatements(itemTransformExpr());

    // Appends "return $itemTransformExpr;})"
    ctx.append("return ");
    itemTransformExpr().doFormatOutputExpr(ctx);
    ctx.append(";})");
    ctx.endLine();
    ctx.decreaseIndent(6);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    ctx.appendInitialStatements(listExpr());
  }
}
