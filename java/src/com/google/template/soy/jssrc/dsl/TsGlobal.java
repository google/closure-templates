/*
 * Copyright 2022 Google Inc.
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
import java.util.function.Consumer;

/** Represents a TS global declaration. */
@AutoValue
public abstract class TsGlobal extends Statement {

  abstract ImmutableList<Statement> bodyStmts();

  public static TsGlobal create(ImmutableList<Statement> bodyStmts) {
    return new AutoValue_TsGlobal(bodyStmts);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    ctx.append("declare global ");
    ctx.enterBlock();
    for (Statement stmt : bodyStmts()) {
      ctx.appendAll(stmt);
      ctx.endLine();
    }
    ctx.close();
  }

  @Override
  public void collectRequires(Consumer<GoogRequire> collector) {
    for (Statement stmt : bodyStmts()) {
      stmt.collectRequires(collector);
    }
  }
}
