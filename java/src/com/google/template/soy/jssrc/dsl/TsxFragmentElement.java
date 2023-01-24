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
import com.google.template.soy.jssrc.dsl.FormattingContext.LexicalState;
import com.google.template.soy.jssrc.dsl.TsxPrintNode.CommandChar;
import java.util.List;
import java.util.stream.Stream;

/** Represents a tsx fragment elemenet, e.g.: "<>body</>". */
@AutoValue
@Immutable
public abstract class TsxFragmentElement extends Expression {

  /**
   * If {@code body} is a TSX element or fragment, returns {@code body}, otherwise returns {@code
   * body} wrapped inside a fragment.
   */
  public static Expression wrap(CodeChunk body) {
    if (body instanceof TsxFragmentElement || body instanceof TsxElement) {
      return (Expression) body;
    }
    return create(ImmutableList.of(body));
  }

  public static Expression wrap(List<? extends CodeChunk> children) {
    if (children.size() == 1) {
      return wrap(children.get(0));
    }
    return create(children);
  }

  public static Expression create(List<? extends CodeChunk> body) {
    return new AutoValue_TsxFragmentElement(
        body.stream().map(TsxFragmentElement::wrapChild).collect(toImmutableList()));
  }

  static Expression wrapChild(CodeChunk chunk) {
    if (chunk instanceof TsxElement
        || chunk instanceof TsxPrintNode
        || chunk instanceof HtmlTag
        || chunk instanceof RawText
        || chunk instanceof CommandChar) {
      return (Expression) chunk;
    } else if (chunk instanceof Concatenation) {
      return ((Concatenation) chunk).map(TsxFragmentElement::wrapChild);
    } else if (chunk instanceof Statement) {
      return TsxPrintNode.wrap(((Statement) chunk).asExpr());
    }
    return TsxPrintNode.wrap((Expression) chunk);
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
    ctx.appendAll(HtmlTag.FRAGMENT_OPEN);
    ctx.endLine();
    for (CodeChunk s : body()) {
      ctx.appendAll(s);
    }
    ctx.endLine();
    ctx.appendAll(HtmlTag.FRAGMENT_CLOSE);
    ctx.popLexicalState();
  }
}
