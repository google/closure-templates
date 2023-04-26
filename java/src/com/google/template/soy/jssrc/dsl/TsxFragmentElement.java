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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.internal.util.TreeStreams;
import com.google.template.soy.jssrc.dsl.Expressions.DecoratedExpression;
import com.google.template.soy.jssrc.dsl.FormattingContext.LexicalState;
import com.google.template.soy.jssrc.dsl.Statements.DecoratedStatement;
import com.google.template.soy.jssrc.dsl.TsxPrintNode.CommandChar;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Represents a tsx fragment elemenet, e.g.: "<>body</>". */
@AutoValue
@Immutable
public abstract class TsxFragmentElement extends Expression {

  /**
   * If {@code body} is a TSX element or fragment, returns {@code body}, otherwise returns {@code
   * body} wrapped inside a fragment.
   */
  public static Expression maybeWrap(CodeChunk body) {
    if (body instanceof TsxFragmentElement || body instanceof TsxElement) {
      return (Expression) body;
    }
    return create(ImmutableList.of(body));
  }

  public static Expression maybeWrap(List<? extends CodeChunk> children) {
    if (children.size() == 1) {
      CodeChunk onlyChild = children.get(0);
      if (onlyChild instanceof Call || onlyChild instanceof VariableReference) {
        // No need to wrap if all we are doing is forwarding to a simple expression that will itself
        // be a fragment.
        return (Expression) onlyChild;
      }
      return maybeWrap(onlyChild);
    }
    return create(children);
  }

  public static Expression create(List<? extends CodeChunk> body) {
    return new AutoValue_TsxFragmentElement(
        mergeLineComments(body.stream())
            .flatMap(TsxFragmentElement::wrapChild)
            .collect(toImmutableList()));
  }

  /**
   * Merge consecutive line comments into a single range comment to avoid creating consecutive range
   * comments from consecutive line comments.
   */
  static Stream<CodeChunk> mergeLineComments(Stream<? extends CodeChunk> s) {
    return TreeStreams.collateAndMerge(
        s,
        (prev, next) -> prev instanceof LineComment && next instanceof LineComment,
        comments ->
            RangeComment.create(
                "\n"
                    + comments.stream()
                        .map(LineComment.class::cast)
                        .map(LineComment::content)
                        .collect(Collectors.joining("\n"))
                    + "\n",
                true));
  }

  static Stream<CodeChunk> wrapChild(CodeChunk chunk) {
    if (chunk instanceof TsxElement
        || chunk instanceof TsxPrintNode
        || chunk instanceof HtmlTag
        || chunk instanceof CommandChar) {
      return Stream.of(chunk);
    } else if (chunk == Expressions.EMPTY) {
      return Stream.of();
    } else if (chunk instanceof StringLiteral) {
      return Stream.of(TsxPrintNode.wrapIfNeeded((StringLiteral) chunk));
    } else if (chunk instanceof Concatenation) {
      return Stream.of(((Concatenation) chunk).map1toN(TsxFragmentElement::wrapChild));
    } else if (chunk instanceof DecoratedStatement || chunk instanceof DecoratedExpression) {
      return mergeLineComments(chunk.childrenStream()).flatMap(TsxFragmentElement::wrapChild);
    } else if (chunk instanceof Statement) {
      return Stream.of(TsxPrintNode.wrap(((Statement) chunk).asExpr()));
    } else if (chunk instanceof Whitespace) {
      return Stream.of(chunk);
    } else {
      return Stream.of(TsxPrintNode.wrap(chunk));
    }
  }

  abstract ImmutableList<? extends CodeChunk> body();

  @Override
  public boolean isCheap() {
    return true;
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return body().stream();
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.pushLexicalState(LexicalState.TSX);
    if (body().isEmpty()) {
      try (FormattingContext buffer = ctx.buffer()) {
        buffer.appendAll(HtmlTag.FRAGMENT_OPEN);
        buffer.appendAll(HtmlTag.FRAGMENT_CLOSE);
      }
    } else {
      ctx.appendAll(HtmlTag.FRAGMENT_OPEN);
      ctx.endLine();
      for (CodeChunk s : body()) {
        ctx.appendAll(s);
      }
      ctx.endLine();
      ctx.appendAll(HtmlTag.FRAGMENT_CLOSE);
    }
    ctx.popLexicalState();
  }
}
