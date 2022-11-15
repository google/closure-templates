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
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Represents a named TS function declaration.
 *
 * <p>Example:
 *
 * <p><code>{@literal
 * function foo(param1: string, param2: number): number { < function body > }
 * }</code>
 */
@AutoValue
public abstract class NamedFunctionDeclaration extends Statement {

  abstract String name();

  abstract ParamDecls params();

  abstract Expression returnType();

  abstract Optional<JsDoc> jsDoc();

  abstract ImmutableList<Statement> bodyStmts();

  abstract boolean isExported();

  abstract boolean isDeclaration();

  public static NamedFunctionDeclaration create(
      String name,
      ParamDecls params,
      Expression returnType,
      JsDoc jsDoc,
      ImmutableList<Statement> bodyStmts,
      boolean isExported) {
    return new AutoValue_NamedFunctionDeclaration(
        name, params, returnType, Optional.of(jsDoc), bodyStmts, isExported, false);
  }

  public static NamedFunctionDeclaration create(
      String name,
      ParamDecls params,
      Expression returnType,
      ImmutableList<Statement> bodyStmts,
      boolean isExported) {
    return new AutoValue_NamedFunctionDeclaration(
        name, params, returnType, /* jsDoc= */ Optional.empty(), bodyStmts, isExported, false);
  }

  public static NamedFunctionDeclaration declaration(
      String name, ParamDecls params, Expression returnType, JsDoc jsDoc) {
    return new AutoValue_NamedFunctionDeclaration(
        name, params, returnType, Optional.of(jsDoc), ImmutableList.of(), true, true);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    if (jsDoc().isPresent()) {
      ctx.append(jsDoc().get().toString());
    }
    ctx.endLine();
    if (isExported()) {
      ctx.append("export ");
    }
    if (isDeclaration()) {
      ctx.append("declare ");
    }
    ctx.append("function " + name() + "(");
    ctx.append(params().getCode());
    ctx.append("): ").appendOutputExpression(returnType());
    if (!isDeclaration()) {
      ctx.append(" ");
      ctx.enterBlock();
      ctx.endLine();
      for (Statement stmt : bodyStmts()) {
        ctx.appendAll(stmt);
        ctx.endLine();
      }
      ctx.close();
    } else {
      ctx.append(";");
    }
    ctx.endLine();
  }

  @Override
  public void collectRequires(Consumer<GoogRequire> collector) {
    for (Statement stmt : bodyStmts()) {
      stmt.collectRequires(collector);
    }
    if (jsDoc().isPresent()) {
      jsDoc().get().collectRequires(collector);
    }
  }
}
