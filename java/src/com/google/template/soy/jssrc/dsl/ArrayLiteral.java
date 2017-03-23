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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.jssrc.restricted.JsExpr;

/** Represents a JavaScript array literal expression. */
@AutoValue
@Immutable
abstract class ArrayLiteral extends CodeChunk.WithValue {

  abstract ImmutableList<? extends CodeChunk.WithValue> elements();

  static ArrayLiteral create(ImmutableList<? extends CodeChunk.WithValue> elements) {
    ImmutableSet.Builder<CodeChunk> builder = ImmutableSet.builder();
    for (CodeChunk.WithValue element : elements) {
      builder.addAll(element.initialStatements());
    }
    return new AutoValue_ArrayLiteral(builder.build(), elements);
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
    doFormatOutputExpr(ctx);
    return new JsExpr(ctx.toString(), Integer.MAX_VALUE);
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.append('[');
    boolean first = true;
    for (CodeChunk.WithValue element : elements()) {
      if (first) {
        first = false;
      } else {
        ctx.append(", ");
      }
      element.doFormatOutputExpr(ctx);
    }
    ctx.append(']');
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    for (CodeChunk.WithValue element : elements()) {
      ctx.appendInitialStatements(element);
    }
  }
}
