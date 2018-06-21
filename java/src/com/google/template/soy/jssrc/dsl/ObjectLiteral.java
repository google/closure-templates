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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.jssrc.restricted.JsExpr;

/** Represents a JavaScript object literal expression. */
@AutoValue
@Immutable
abstract class ObjectLiteral extends Expression {

  abstract ImmutableList<? extends Expression> keys();

  abstract ImmutableList<? extends Expression> values();

  static ObjectLiteral create(
      ImmutableList<? extends Expression> keys, ImmutableList<? extends Expression> values) {
    Preconditions.checkArgument(keys.size() == values.size(), "Mismatch between keys and values.");
    ImmutableList.Builder<Statement> initialStatements = ImmutableList.builder();
    for (Expression key : keys) {
      initialStatements.addAll(key.initialStatements());
    }
    for (Expression value : values) {
      initialStatements.addAll(value.initialStatements());
    }
    return new AutoValue_ObjectLiteral(initialStatements.build(), keys, values);
  }

  @Override
  public JsExpr singleExprOrName() {
    FormattingContext ctx = new FormattingContext();
    ctx.appendOutputExpression(this);
    return new JsExpr(ctx.toString(), Integer.MAX_VALUE);
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.append('{');
    for (int i = 0; i < keys().size(); i++) {
      if (i > 0) {
        ctx.append(", ");
      }
      ctx.appendOutputExpression(keys().get(i))
          .append(": ")
          .appendOutputExpression(values().get(i));
    }
    ctx.append('}');
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    for (Expression key : keys()) {
      ctx.appendInitialStatements(key);
    }
    for (Expression value : values()) {
      ctx.appendInitialStatements(value);
    }
  }

  @Override
  public void collectRequires(RequiresCollector collector) {
    for (Expression key : keys()) {
      key.collectRequires(collector);
    }
    for (Expression value : values()) {
      value.collectRequires(collector);
    }
  }
}
