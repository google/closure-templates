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
import com.google.template.soy.jssrc.dsl.CodeChunk.RequiresCollector;
import com.google.template.soy.jssrc.restricted.JsExpr;
import javax.annotation.Nullable;

/**
 * Represents a {@code FieldDeclaration} statement. This emits one of `/** @type {number}
 * //this.bar;` or `/** @type {number} //this.bar = 3;`
 */
@AutoValue
@Immutable
public abstract class FieldDeclaration extends Expression {

  abstract String fieldName();

  abstract JsDoc jsDoc();

  @Nullable
  abstract Expression value();

  public static FieldDeclaration createWithoutValue(String fieldName, JsDoc jsDoc) {
    return new AutoValue_FieldDeclaration(
        /* initialStatements= */ ImmutableList.<Statement>of(), fieldName, jsDoc, null);
  }

  public static FieldDeclaration create(String fieldName, JsDoc jsDoc, Expression value) {
    return new AutoValue_FieldDeclaration(
        /* initialStatements= */ ImmutableList.<Statement>of(), fieldName, jsDoc, value);
  }

  @Override
  public JsExpr singleExprOrName() {
    FormattingContext ctx = new FormattingContext();
    ctx.appendOutputExpression(this);
    return new JsExpr(ctx.toString(), Integer.MAX_VALUE);
  }

  @Override
  public void collectRequires(RequiresCollector collector) {
    if (value() != null) {
      value().collectRequires(collector);
    }
    jsDoc().collectRequires(collector);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    // there are none
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.append(jsDoc());
    Expression assignment = Expression.id("this").dotAccess(fieldName());
    if (value() != null) {
      assignment = assignment.assign(value());
    }
    ctx.appendOutputExpression(assignment);
  }
}
