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
import com.google.template.soy.jssrc.dsl.CodeChunk.RequiresCollector;
import com.google.template.soy.jssrc.restricted.JsExpr;

/**
 * Represents a sequence of statements that also have a variable allocated to represent them
 * when referenced from other code chunks.
 * TODO(brndn): this is the only {@link CodeChunk} subclass that does not correspond directly
 * to a JavaScript grammatical production. Its behavior is somewhat confused. However, the DSL
 * relies on its behavior to allow {@link Generator#newChunk building new chunks} that don't
 * initially contain a value. Consider refactoring into a "Reference" class.
 */
@AutoValue
abstract class Composite extends CodeChunk.WithValue {
  abstract ImmutableList<CodeChunk> children();

  abstract String varName();

  static Composite create(ImmutableList<CodeChunk> children, String varName) {
    Preconditions.checkState(children.size() > 1);
    return new AutoValue_Composite(children, varName);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx, boolean moreToCome) {
    for (int i = 0; i < children().size(); ++i) {
      children().get(i).formatInitialStatements(ctx, moreToCome || i < children().size() - 1);
    }
  }
  @Override
  public void collectRequires(RequiresCollector collector) {
    for (CodeChunk child : children()) {
      child.collectRequires(collector);
    }
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx, OutputContext outputContext) {
    // Print the variable reference only if the composite is appearing in another expression.
    // The purpose of a composite is to provide a name for reference from other code chunks.
    // If this composite is being asked to appear as its own statement, print nothing.
    if (outputContext != OutputContext.EXPRESSION) {
      return;
    }
    ctx.append(varName());
  }

  @Override
  public boolean isRepresentableAsSingleExpression() {
    return false;
  }

  @Override
  public JsExpr singleExprOrName() {
    return new JsExpr(varName(), Integer.MAX_VALUE);
  }
}
