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
import com.google.common.collect.ImmutableList;
import com.google.template.soy.jssrc.dsl.CodeChunk.RequiresCollector;
import com.google.template.soy.jssrc.restricted.JsExpr;

/** Represents a JavaScript array literal expression. */
@AutoValue
abstract class ArrayLiteral extends CodeChunk.WithValue {

  abstract ImmutableList<? extends CodeChunk.WithValue> elements();

  static ArrayLiteral create(ImmutableList<? extends CodeChunk.WithValue> elements) {
    return new AutoValue_ArrayLiteral(elements);
  }

  @Override
  public boolean isRepresentableAsSingleExpression() {
    for (CodeChunk.WithValue element : elements()) {
      if (!element.isRepresentableAsSingleExpression()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void collectRequires(RequiresCollector collector) {
    for (CodeChunk.WithValue element : elements()) {
      element.collectRequires(collector);
    }
  }

  @Override
  public JsExpr singleExprOrName() {
    FormattingContext ctx = new FormattingContext();
    formatOutputExpr(ctx, EXPRESSION);
    return new JsExpr(ctx.toString(), Integer.MAX_VALUE);
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx, OutputContext outputContext) {
    ctx.append('[');
    boolean first = true;
    for (CodeChunk.WithValue element : elements()) {
      if (first) {
        first = false;
      } else {
        ctx.append(", ");
      }
      element.formatOutputExpr(ctx, EXPRESSION);
    }
    ctx.append(']');
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx, boolean moreToCome) {
    for (int i = 0; i < elements().size(); ++i) {
      elements().get(i).formatInitialStatements(ctx, moreToCome || i < elements().size() - 1);
    }
  }
}
