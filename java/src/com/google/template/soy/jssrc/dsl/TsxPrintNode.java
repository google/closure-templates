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
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.jssrc.dsl.FormattingContext.LexicalState;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Represents a TSX "{}" print node for inline JS within a fragment/element or tagged template
 * literal. For example, "{name}" in "<>hi {name}".
 */
@AutoValue
@Immutable
public abstract class TsxPrintNode extends Expression {

  public static TsxPrintNode wrap(CodeChunk expr) {
    if (expr instanceof TsxPrintNode) {
      return (TsxPrintNode) expr;
    } else if (expr instanceof Expression) {
      return new AutoValue_TsxPrintNode(((Expression) expr).asInlineExpr());
    } else if (expr instanceof LineComment) {
      return new AutoValue_TsxPrintNode(RangeComment.create(((LineComment) expr).content(), true));
    } else if (expr instanceof RangeComment) {
      return new AutoValue_TsxPrintNode(expr);
    } else {
      throw new ClassCastException(expr.getClass().getName());
    }
  }

  /**
   * Wrap {@code s} in a print node if it is required in TSX context, i.e. it's a BACKTICK quoted
   * string that can't be rendered without a TSX interpolation.
   */
  public static Expression wrapIfNeeded(StringLiteral s) {
    if (s.quoteStyle() == QuoteStyle.BACKTICK) {
      return wrap(s);
    }
    return s;
  }

  public abstract CodeChunk expr();

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.of(expr());
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.append(ctx.getInterpolationOpenString());
    ctx.pushLexicalState(LexicalState.JS);
    ctx.increaseIndent();
    if (expr() instanceof Expression) {
      // If outputting an expression, omit any semicolon.
      ctx.appendOutputExpression((Expression) expr());
    } else {
      ctx.appendAll(expr());
    }
    ctx.popLexicalState();
    ctx.append(ctx.getInterpolationCloseString());
    ctx.decreaseIndent();
  }

  @Override
  public boolean isCheap() {
    return !(expr() instanceof Expression) || ((Expression) expr()).isCheap();
  }

  /**
   * Special handling for command chars, since we don't want to break lines within these print
   * nodes, and need to circumvent double escaping on the strings.
   */
  @AutoValue
  @Immutable
  public abstract static class CommandChar extends Expression {

    public static CommandChar create(String charContents) {
      return create(charContents, false);
    }

    public static CommandChar create(String charContents, boolean endLineAfterChar) {
      return new AutoValue_TsxPrintNode_CommandChar(
          Optional.of(StringLiteral.create(charContents)), endLineAfterChar);
    }

    public static CommandChar createNil() {
      return new AutoValue_TsxPrintNode_CommandChar(Optional.empty(), false);
    }

    abstract Optional<Expression> expr();

    abstract boolean endLineAfterChar();

    @Override
    Stream<? extends CodeChunk> childrenStream() {
      return expr().isPresent() ? Stream.of(expr().get()) : Stream.empty();
    }

    @Override
    void doFormatOutputExpr(FormattingContext ctx) {
      String open = ctx.getInterpolationOpenString();
      String close = ctx.getInterpolationCloseString();
      String value = "";
      if (expr().isPresent()) {
        value = "'" + ((StringLiteral) expr().get()).literalValue() + "'";
      }

      ctx.append(open + value + close);

      if (endLineAfterChar()) {
        ctx.endLine();
      }
    }
  }
}
