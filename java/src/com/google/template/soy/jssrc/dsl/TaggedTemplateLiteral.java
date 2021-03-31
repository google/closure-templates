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

import static com.google.template.soy.exprtree.Operator.Associativity.LEFT;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.exprtree.Operator.Associativity;
import java.util.function.Consumer;

/** Represents a JavaScript tagged template literal. */
@AutoValue
@Immutable
public abstract class TaggedTemplateLiteral extends Operation {
  abstract Expression tag();

  abstract TemplateLiteral templateLiteral();

  public static TaggedTemplateLiteral create(Expression tag, TemplateLiteral templateLiteral) {
    ImmutableList<Statement> initialStatements =
        ImmutableList.<Statement>builder()
            .addAll(tag.initialStatements())
            .addAll(templateLiteral.initialStatements())
            .build();
    return new AutoValue_TaggedTemplateLiteral(initialStatements, tag, templateLiteral);
  }

  @Override
  int precedence() {
    // Tagged templates are function calls, and the precedence of a JavaScript function call is
    // higher than any Soy operator.
    return Integer.MAX_VALUE;
  }

  @Override
  Associativity associativity() {
    return LEFT;
  }

  @Override
  public void collectRequires(Consumer<GoogRequire> collector) {
    tag().collectRequires(collector);
    templateLiteral().collectRequires(collector);
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    formatOperand(tag(), OperandPosition.LEFT, ctx);
    templateLiteral().doFormatOutputExpr(ctx);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    ctx.appendInitialStatements(tag());
    ctx.appendInitialStatements(templateLiteral());
  }

  @Override
  boolean initialExpressionIsObjectLiteral() {
    return tag().initialExpressionIsObjectLiteral();
  }
}
