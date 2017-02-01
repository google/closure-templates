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

import static com.google.template.soy.jssrc.dsl.OutputContext.EXPRESSION;

import com.google.auto.value.AutoValue;
import com.google.template.soy.jssrc.dsl.CodeChunk.RequiresCollector;
import com.google.template.soy.jssrc.restricted.JsExpr;
import javax.annotation.Nullable;

/** Represents a variable declaration. */
@AutoValue
abstract class Declaration extends CodeChunk.WithValue {

  abstract String varName();

  abstract CodeChunk.WithValue rhs();

  @Nullable
  abstract String closureCompilerTypeExpression();

  static Declaration create(
      @Nullable String closureCompilerTypeExpression, String varName, CodeChunk.WithValue rhs) {
    return new AutoValue_Declaration(varName, rhs, closureCompilerTypeExpression);
  }

  /**
   * {@link CodeChunk#getCode} serializes both the chunk's initial statements and its output
   * expression. When a declaration is the only chunk being serialized, this leads to a redundant
   * trailing expression: <code>
   *   var $$tmp = blah;
   *   $$tmp
   * </code> Override the superclass implementation to omit the trailing expression.
   * TODO(brndn): this is the only override of the base method. We can probably eliminate it.
   */
  @Override
  String getCode(int startingIndent, OutputContext outputContext, boolean moreToCome) {
    FormattingContext ctx = new FormattingContext(startingIndent);
    formatInitialStatements(ctx, moreToCome);
    return ctx.toString();
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx, boolean moreToCome) {
    rhs().formatInitialStatements(ctx, moreToCome);

    if (!moreToCome) {
      // If there's no more to come, no point in declaring the variable.
      rhs().formatOutputExpr(ctx, EXPRESSION);
      ctx.append(';').endLine();
      return;
    }

    if (closureCompilerTypeExpression() != null) {
      ctx.append("/** @type {").append(closureCompilerTypeExpression()).append("} */").endLine();
    }
    ctx.append("var ").append(varName()).append(" = ");
    rhs().formatOutputExpr(ctx, EXPRESSION);
    ctx.append(";").endLine();
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx, OutputContext outputContext) {
      ctx.append(varName());
  }

  @Override
  public boolean isRepresentableAsSingleExpression() {
    return rhs().isRepresentableAsSingleExpression();
  }

  @Override
  public JsExpr singleExprOrName() {
    return new JsExpr(varName(), Integer.MAX_VALUE);
  }
  
  @Override
  public void collectRequires(RequiresCollector collector) {
    rhs().collectRequires(collector);
  }
}
