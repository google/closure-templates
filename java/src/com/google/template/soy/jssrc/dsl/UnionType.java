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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.jssrc.restricted.JsExpr;
import java.util.List;
import java.util.function.Consumer;

/** Represents a TS union type, for use with eg `new` statements. */
public class UnionType extends Expression {

  private final ImmutableList<Expression> members;

  UnionType(List<Expression> members) {
    this.members = ImmutableList.copyOf(members);
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    for (int i = 0; i < members.size(); i++) {
      ctx.appendOutputExpression(members.get(i));
      if (i < members.size() - 1) {
        ctx.append("|");
      }
    }
  }

  @Override
  public void collectRequires(Consumer<GoogRequire> collector) {}

  @Override
  public ImmutableList<Statement> initialStatements() {
    return ImmutableList.of();
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {}

  @Override
  public JsExpr singleExprOrName() {
    FormattingContext ctx = new FormattingContext();
    doFormatOutputExpr(ctx);
    return new JsExpr(ctx.toString(), Integer.MAX_VALUE);
  }
}
