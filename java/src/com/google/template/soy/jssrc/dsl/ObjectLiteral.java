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
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.jssrc.restricted.JsExpr;
import java.util.Map;
import java.util.function.Function;

/** Represents a JavaScript object literal expression. */
@AutoValue
@Immutable
abstract class ObjectLiteral extends Expression {

  abstract ImmutableMap<Expression, Expression> values();

  static ObjectLiteral create(Map<String, Expression> object) {
    return create(object, Expression::id);
  }

  static ObjectLiteral createWithQuotedKeys(Map<String, Expression> object) {
    return create(object, Expression::stringLiteral);
  }

  private static ObjectLiteral create(
      Map<String, Expression> object, Function<String, Expression> createKeyFn) {
    ImmutableList.Builder<Statement> initialStatements = ImmutableList.builder();
    ImmutableMap.Builder<Expression, Expression> values = ImmutableMap.builder();
    for (Map.Entry<String, Expression> entry : object.entrySet()) {
      initialStatements.addAll(entry.getValue().initialStatements());
      values.put(createKeyFn.apply(entry.getKey()), entry.getValue());
    }
    return new AutoValue_ObjectLiteral(initialStatements.build(), values.build());
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
    boolean first = true;
    for (Map.Entry<Expression, Expression> entry : values().entrySet()) {
      if (!first) {
        ctx.append(", ");
      }
      first = false;
      ctx.appendOutputExpression(entry.getKey())
          .append(": ")
          .appendOutputExpression(entry.getValue());
    }
    ctx.append('}');
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    for (Expression value : values().values()) {
      ctx.appendInitialStatements(value);
    }
  }

  @Override
  public void collectRequires(RequiresCollector collector) {
    for (Expression value : values().values()) {
      value.collectRequires(collector);
    }
  }

  @Override
  boolean initialExpressionIsObjectLiteral() {
    return true;
  }
}
