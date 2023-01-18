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
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Represents a TSX "{}" print node for inline JS within a fragment/element or tagged template
 * literal. For example, "{name}" in "<>hi {name}".
 */
@Immutable
public class TsxPrintNode extends Statement {
  private final Optional<Expression> expr;

  public static TsxPrintNode create(Expression expr) {
    return new TsxPrintNode(Optional.of(expr));
  }

  private TsxPrintNode(Optional<Expression> expr) {
    this.expr = expr;
  }

  Optional<Expression> expr() {
    return expr;
  }

  @Override
  void doFormatStatement(FormattingContext ctx) {
    if (!expr.isPresent()) {
      ctx.append(ctx.getInterpolationOpenString() + "}");
      return;
    }

    ctx.append(ctx.getInterpolationOpenString());
    ctx.increaseIndent();
    ctx.appendOutputExpression(expr.get().asInlineExpr());
    ctx.append("}");
    ctx.decreaseIndent();
  }

  @Override
  public void collectRequires(Consumer<GoogRequire> collector) {
    if (expr.isPresent()) {
      expr.get().collectRequires(collector);
    }
  }

  /**
   * Special handling for command chars, since we don't want to break lines within these print
   * nodes, and need to circumvent double escaping on the strings.
   */
  public static class CommandChar extends TsxPrintNode {
    private final boolean endLineAfterChar;

    public static CommandChar create(String charContents) {
      return new CommandChar(Optional.of(charContents));
    }

    public static CommandChar create(String charContents, boolean endLineAfterChar) {
      return new CommandChar(Optional.of(charContents), endLineAfterChar);
    }

    public static CommandChar createNil() {
      return new CommandChar(Optional.empty());
    }

    private CommandChar(Optional<String> charContents, boolean endLineAfterChar) {
      super(charContents.map(c -> StringLiteral.create(c)));
      this.endLineAfterChar = endLineAfterChar;
    }

    private CommandChar(Optional<String> charContents) {
      this(charContents, /* endLineAfterChar= */ false);
    }

    @Override
    void doFormatStatement(FormattingContext ctx) {
      if (!expr().isPresent()) {
        ctx.append(ctx.getInterpolationOpenString() + "}");
        return;
      }

      ctx.append(
          String.format(
              "%s'%s'}", ctx.getInterpolationOpenString(), expr().get().asStringLiteral().get()));

      if (endLineAfterChar) {
        ctx.endLine();
      }
    }
  }
}
