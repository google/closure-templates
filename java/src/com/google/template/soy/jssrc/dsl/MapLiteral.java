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
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.jssrc.restricted.JsExpr;

/** Represents a JavaScript map literal expression. */
@AutoValue
@Immutable
abstract class MapLiteral extends CodeChunk.WithValue {

  abstract ImmutableList<? extends CodeChunk.WithValue> keys();

  abstract ImmutableList<? extends CodeChunk.WithValue> values();

  static MapLiteral create(
      ImmutableList<? extends CodeChunk.WithValue> keys,
      ImmutableList<? extends CodeChunk.WithValue> values) {
    Preconditions.checkArgument(keys.size() == values.size(), "Mismatch between keys and values.");
    ImmutableSet.Builder<CodeChunk> initialStatements = ImmutableSet.builder();
    for (CodeChunk.WithValue key : keys) {
      initialStatements.addAll(key.initialStatements());
    }
    for (CodeChunk.WithValue value : values) {
      initialStatements.addAll(value.initialStatements());
    }
    return new AutoValue_MapLiteral(initialStatements.build(), keys, values);
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
    for (CodeChunk.WithValue key : keys()) {
      ctx.appendInitialStatements(key);
    }
    for (CodeChunk.WithValue value : values()) {
      ctx.appendInitialStatements(value);
    }
  }

  @Override
  public void collectRequires(RequiresCollector collector) {
    for (CodeChunk.WithValue key : keys()) {
      key.collectRequires(collector);
    }
    for (CodeChunk.WithValue value : values()) {
      value.collectRequires(collector);
    }
  }
}
