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

import com.google.errorprone.annotations.Immutable;
import java.util.function.Consumer;

/**
 * Represents a TSX "{}" print node for inline JS within a fragment/element. For example, "{name}"
 * in "<>hi {name}".
 */
@Immutable
public class TsxPrintNode extends Statement {
  private final Expression expr;

  public static TsxPrintNode newLine() {
    return new TsxPrintNode(StringLiteral.create("\\n")) {
      @Override
      void doFormatInitialStatements(FormattingContext ctx) {
        ctx.append("{'\\n'}").endLine();
      }
    };
  }

  public static TsxPrintNode carriageReturn() {
    return new TsxPrintNode(StringLiteral.create("\\r")) {
      @Override
      void doFormatInitialStatements(FormattingContext ctx) {
        ctx.append("{'\\n'}").endLine();
      }
    };
  }

  public static TsxPrintNode create(Expression expr) {
    return new TsxPrintNode(expr);
  }

  private TsxPrintNode(Expression expr) {
    this.expr = expr;
  }

  Expression expr() {
    return expr;
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {

    ctx.append("{");

    // make this unbreakable for short print nodes w/ no initial statements. and short body?
    ctx.increaseIndent();
    ctx.appendOutputExpression(expr.asInlineExpr());
    ctx.append("}");
    ctx.decreaseIndent();

    // ctx.appendtoTsxElementMaybeOnNewLine(printNodeContents);

    if (expr instanceof StringLiteral
        && (expr.asStringLiteral().get().equals("'\\n'")
            || expr.asStringLiteral().get().equals("'\\r'"))) {
      ctx.endLine();
    }
  }

  @Override
  public void collectRequires(Consumer<GoogRequire> collector) {
    expr().collectRequires(collector);
  }

  /**
   * Special handling for command chars, since we don't want to break lines within these print
   * nodes, and need to circumvent double escaping on the strings.
   */
  public static class CommandChar extends TsxPrintNode {
    private final boolean endLineAfterChar;

    public static CommandChar create(String charContents) {
      return new CommandChar(charContents, /* endLineAfterChar= */ false);
    }

    public static CommandChar create(String charContents, boolean endLineAfterChar) {
      return new CommandChar(charContents, endLineAfterChar);
    }

    private CommandChar(String charContents, boolean endLineAfterChar) {
      super(StringLiteral.create(charContents));
      this.endLineAfterChar = endLineAfterChar;
    }

    @Override
    void doFormatInitialStatements(FormattingContext ctx) {
      ctx.append("{'" + expr().asStringLiteral().get() + "'}");

      if (endLineAfterChar) {
        ctx.endLine();
      }
    }
  }
}
