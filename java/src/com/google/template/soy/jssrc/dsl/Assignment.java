/*
 * Copyright 2016 Google Inc.
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

import static com.google.template.soy.jssrc.dsl.OutputContext.EXPRESSION;

import com.google.auto.value.AutoValue;
import com.google.template.soy.jssrc.dsl.CodeChunk.RequiresCollector;
import com.google.template.soy.jssrc.restricted.JsExpr;

/** Represents an assignment to a variable. */
@AutoValue
abstract class Assignment extends CodeChunk.WithValue {
  abstract String varName();

  abstract CodeChunk.WithValue rhs();

  static Assignment create(String varName, CodeChunk.WithValue rhs) {
    return new AutoValue_Assignment(varName, rhs);
  }
  
  @Override
  public void collectRequires(RequiresCollector collector) {
    rhs().collectRequires(collector);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx, boolean moreToCome) {
    rhs().formatInitialStatements(ctx, true /* moreToCome */);
    ctx.append(varName()).append(" = ");
    rhs().formatOutputExpr(ctx, EXPRESSION);
    ctx.append(";").endLine();
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx, OutputContext outputContext) {
    ctx.append(varName());
  }

  @Override
  public boolean isRepresentableAsSingleExpression() {
    return true;
  }

  @Override
  public JsExpr singleExprOrName() {
    return new JsExpr(varName(), Integer.MAX_VALUE);
  }
}
