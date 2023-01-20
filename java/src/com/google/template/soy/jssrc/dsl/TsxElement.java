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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.jssrc.dsl.FormattingContext.LexicalState;
import java.util.List;
import java.util.stream.Stream;

/** Represents a tsx elemenet, e.g.: "<div>body</div>". */
@AutoValue
@Immutable
public abstract class TsxElement extends Expression {

  public abstract HtmlTag openTag();

  public abstract HtmlTag closeTag();

  public abstract ImmutableList<? extends CodeChunk> body();

  public static TsxElement create(
      HtmlTag openTag, HtmlTag closeTag, List<? extends CodeChunk> body) {
    checkState(openTag.tagName().equals(closeTag.tagName()));
    checkState(openTag.isOpen());
    checkState(closeTag.isClose());
    return new AutoValue_TsxElement(
        openTag,
        closeTag,
        body.stream().map(TsxFragmentElement::wrapChild).collect(toImmutableList()));
  }

  public TsxElement copyWithTagName(String newTagName) {
    return create(
        openTag().copyWithTagName(newTagName), closeTag().copyWithTagName(newTagName), body());
  }

  public TsxElement copyWithMoreBody(CodeChunk... bodyToAppend) {
    return create(
        openTag(),
        closeTag(),
        ImmutableList.<CodeChunk>builder().addAll(body()).add(bodyToAppend).build());
  }

  @Override
  public boolean isCheap() {
    return true;
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.concat(Stream.of(openTag(), closeTag()), body().stream());
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.pushLexicalState(LexicalState.TSX);
    ctx.appendAll(openTag());
    for (CodeChunk s : body()) {
      ctx.appendAll(s);
    }
    ctx.appendAll(closeTag());
    ctx.popLexicalState();
  }
}
