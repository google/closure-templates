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

  public static TsxPrintNode create(Expression expr) {
    return new AutoValue_TsxPrintNode(expr.asInlineExpr());
  }

  public abstract Expression expr();

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.of(expr());
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.append(ctx.getInterpolationOpenString());
    ctx.pushLexicalState(LexicalState.JS);
    ctx.increaseIndent();
    ctx.appendOutputExpression(expr());
    ctx.popLexicalState();
    ctx.append(ctx.getInterpolationCloseString());
    ctx.decreaseIndent();
  }

  @Override
  public boolean isCheap() {
    return expr().isCheap();
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
      if (!expr().isPresent()) {
        ctx.append(ctx.getInterpolationOpenString() + ctx.getInterpolationCloseString());
        return;
      }

      ctx.append(
          String.format(
              "%s'%s'%s",
              ctx.getInterpolationOpenString(),
              ((StringLiteral) expr().get()).literalValue(),
              ctx.getInterpolationCloseString()));

      if (endLineAfterChar()) {
        ctx.endLine();
      }
    }
  }
}
