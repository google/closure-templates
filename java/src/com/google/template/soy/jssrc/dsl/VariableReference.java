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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.jssrc.restricted.JsExpr;
import java.util.stream.Stream;

/** Represents a reference to a previously declared variable. */
@AutoValue
@Immutable
abstract class VariableReference extends Expression
    implements Expression.HasInitialStatements, CodeChunk.HasRequires {

  abstract VariableDeclaration declaration();

  static VariableReference of(VariableDeclaration declaration) {
    return new AutoValue_VariableReference(declaration);
  }

  @Override
  public JsExpr singleExprOrName(FormatOptions options) {
    return new JsExpr(declaration().varName().id(), Integer.MAX_VALUE);
  }

  @Override
  public Expression assign(Expression rhs) {
    if (!declaration().isMutable()) {
      throw new IllegalStateException("Can't assign const variables");
    }
    return super.assign(rhs);
  }

  @Override
  public boolean isCheap() {
    return true;
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.appendOutputExpression(declaration().varName());
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.of();
  }

  @Override
  public ImmutableList<Statement> initialStatements() {
    return ImmutableList.of(declaration());
  }

  @Override
  public ImmutableSet<GoogRequire> googRequires() {
    return declaration().googRequires();
  }
}
