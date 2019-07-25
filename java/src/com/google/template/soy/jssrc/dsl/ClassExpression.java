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
import com.google.template.soy.jssrc.restricted.JsExpr;
import javax.annotation.Nullable;

/**
 * Represents an ES6 class. Currently only supports anonymous class expressions.
 *
 * <p>Example:
 *
 * <p><code>{@literal
 * class[ extends BaseClass] {
 *    ...
 * };
 * }</code>
 */
@AutoValue
public abstract class ClassExpression extends Expression {
  @Nullable
  abstract Expression baseClass();

  abstract ImmutableList<MethodDeclaration> methods();

  public static ClassExpression create(
      Expression baseClass, ImmutableList<MethodDeclaration> methods) {
    return new AutoValue_ClassExpression(
        ImmutableList.of() /* initialStatements */, baseClass, methods);
  }

  public static ClassExpression create(ImmutableList<MethodDeclaration> methods) {
    return new AutoValue_ClassExpression(ImmutableList.of() /* initialStatements */, null, methods);
  }

  @Override
  public JsExpr singleExprOrName() {
    FormattingContext ctx = new FormattingContext();
    ctx.appendOutputExpression(this);
    return new JsExpr(ctx.toString(), Integer.MAX_VALUE);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    // there are none
  }

  @Override
  public void collectRequires(RequiresCollector collector) {
    if (baseClass() != null) {
      baseClass().collectRequires(collector);
    }
    for (MethodDeclaration method : methods()) {
      method.collectRequires(collector);
    }
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.append("class");
    if (baseClass() != null) {
      ctx.append(" extends ");
      ctx.appendOutputExpression(baseClass());
    }
    ctx.append(" ");
    try (FormattingContext ignored = ctx.enterBlock()) {
      for (int i = 0; i < methods().size(); i++) {
        if (i > 0) {
          ctx.append('\n');
          ctx.endLine();
        }
        methods().get(i).doFormatOutputExpr(ctx);
      }
    }
  }

  /**
   * Represents a method declaration within an ES6 class (anonymous or named).
   *
   * <p>Example:
   *
   * <p><code>{@literal
   * foo(param1, param2) { < function body > }
   * }</code>
   */
  @AutoValue
  public abstract static class MethodDeclaration extends Expression {
    abstract String name();

    abstract JsDoc jsDoc();

    abstract Statement body();

    public static MethodDeclaration create(String name, JsDoc jsDoc, Statement body) {
      return new AutoValue_ClassExpression_MethodDeclaration(
          /* initialStatements= */ ImmutableList.of(), name, jsDoc, body);
    }

    @Override
    public JsExpr singleExprOrName() {
      FormattingContext ctx = new FormattingContext();
      ctx.appendOutputExpression(this);
      return new JsExpr(ctx.toString(), Integer.MAX_VALUE);
    }

    @Override
    void doFormatInitialStatements(FormattingContext ctx) {
      // there are none
    }

    @Override
    public void collectRequires(RequiresCollector collector) {
      body().collectRequires(collector);
      jsDoc().collectRequires(collector);
    }

    @Override
    void doFormatOutputExpr(FormattingContext ctx) {
      ctx.append(jsDoc());
      ctx.endLine();
      ctx.append(name() + "(");
      ctx.append(CodeChunkUtils.generateParamList(jsDoc()));
      ctx.append(") ");
      try (FormattingContext ignored = ctx.enterBlock()) {
        ctx.appendAll(body());
      }
    }
  }
}
