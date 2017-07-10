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

/** Represents a reference to a previously declared variable. */
@AutoValue
@Immutable
abstract class VariableReference extends CodeChunk.WithValue {

  abstract VariableDeclaration declaration();

  static VariableReference of(VariableDeclaration declaration) {
    return new AutoValue_VariableReference(ImmutableSet.<CodeChunk>of(declaration), declaration);
  }

  @Override
  public JsExpr singleExprOrName() {
    return new JsExpr(declaration().varName(), Integer.MAX_VALUE);
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.append(declaration().varName());
  }

  @Override
  public void collectRequires(RequiresCollector collector) {
    declaration().collectRequires(collector);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    ctx.appendAll(declaration());
  }
}
