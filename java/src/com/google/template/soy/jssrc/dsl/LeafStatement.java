/*
 * Copyright 2017 Google Inc.
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

/**
 * Despite the name, {@link JsExpr} instances don't have to contain valid JavaScript expressions.
 * This class holds such instances.
 */
@AutoValue
@Immutable
abstract class LeafStatement extends CodeChunk {
  abstract String value();
  abstract ImmutableSet<GoogRequire> requires();

  static LeafStatement create(String value, Iterable<GoogRequire> requires) {
    return new AutoValue_LeafStatement(value, ImmutableSet.copyOf(requires));
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    if (value().length() == 0) {
      return;
    }
    ctx.append(value());
    if (!(value().endsWith(";") || value().endsWith("}"))) {
      ctx.append(';');
    }
  }

  @Override
  public void collectRequires(RequiresCollector collector) {
    for (GoogRequire require : requires()) {
      collector.add(require);
    }
  }
}
