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
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

/** Represents a JavaScript object literal expression. */
@AutoValue
@Immutable
public abstract class ObjectLiteral extends Expression {

  private static final String SPREAD_PREFIX = "_object_literal_spread_xxx_";
  private static final AtomicInteger SERIAL = new AtomicInteger();

  static String newSpread() {
    return SPREAD_PREFIX + SERIAL.incrementAndGet();
  }

  private static boolean isSpread(Expression e) {
    return (e instanceof Leaf && ((Leaf) e).value().getText().startsWith(SPREAD_PREFIX))
        || (e instanceof StringLiteral
            && ((StringLiteral) e).literalValue().startsWith(SPREAD_PREFIX));
  }

  abstract ImmutableMap<Expression, Expression> values();

  static ObjectLiteral create(Map<String, Expression> object) {
    return create(object, Expressions::id);
  }

  private static ObjectLiteral create(
      Map<String, Expression> object, Function<String, Expression> createKeyFn) {
    ImmutableMap.Builder<Expression, Expression> values = ImmutableMap.builder();
    for (Map.Entry<String, Expression> entry : object.entrySet()) {
      values.put(createKeyFn.apply(entry.getKey()), entry.getValue());
    }
    return new AutoValue_ObjectLiteral(values.buildOrThrow());
  }

  static ObjectLiteral createWithQuotedKeys(Map<String, Expression> object) {
    return create(object, Expressions::stringLiteral);
  }

  public ObjectLiteral append(String key, Expression expression) {
    return new AutoValue_ObjectLiteral(
        ImmutableMap.<Expression, Expression>builder()
            .putAll(values())
            .put(Expressions.id(key), expression)
            .buildOrThrow());
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
      if (isSpread(entry.getKey())) {
        try (FormattingContext buffer = ctx.buffer()) {
          buffer.append("...").appendOutputExpression(entry.getValue());
        }
      } else {
        try (FormattingContext buffer = ctx.buffer()) {
          buffer.appendOutputExpression(entry.getKey()).append(": ");
        }
        ctx.appendOutputExpression(entry.getValue());
      }
    }
    ctx.append('}');
  }

  @Override
  public boolean isDefinitelyNotNull() {
    return true;
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.concat(values().keySet().stream(), values().values().stream());
  }

  @Override
  boolean initialExpressionIsObjectLiteral() {
    return true;
  }
}
