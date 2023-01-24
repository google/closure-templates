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

/** Represents an {@code HtmlTagNode}. */
@AutoValue
@Immutable
public abstract class HtmlTag extends Expression {

  public static final HtmlTag FRAGMENT_OPEN = createOpen("", ImmutableList.of());
  public static final HtmlTag FRAGMENT_CLOSE = createClose("", ImmutableList.of());

  public static HtmlTag createOpen(String tagName, List<? extends CodeChunk> attributes) {
    return create(tagName, false, attributes.stream());
  }

  public static HtmlTag createOpen(String tagName, CodeChunk... attributes) {
    return create(tagName, false, Stream.of(attributes));
  }

  public static HtmlTag createClose(String tagName, List<? extends CodeChunk> attributes) {
    return create(tagName, true, attributes.stream());
  }

  public static HtmlTag createClose(String tagName, CodeChunk... attributes) {
    return create(tagName, true, Stream.of(attributes));
  }

  private static HtmlTag create(
      String tagName, boolean isClose, Stream<? extends CodeChunk> attributes) {
    return new AutoValue_HtmlTag(
        tagName, isClose, attributes.map(HtmlTag::wrapChild).collect(toImmutableList()));
  }

  private static Expression wrapChild(CodeChunk chunk) {
    if (chunk instanceof HtmlAttribute
        || chunk instanceof RawText
        || chunk instanceof CommandChar) {
      return (Expression) chunk;
    } else if (chunk instanceof Concatenation) {
      return ((Concatenation) chunk).map(HtmlTag::wrapChild);
    } else if (chunk instanceof Statement) {
      return TsxPrintNode.wrap(((Statement) chunk).asExpr());
    }
    return TsxPrintNode.wrap((Expression) chunk);
  }

  abstract String tagName();

  abstract boolean isClose();

  abstract ImmutableList<? extends CodeChunk> attributes();

  public HtmlTag copyWithTagName(String newTagName) {
    return new AutoValue_HtmlTag(newTagName, isClose(), attributes());
  }

  boolean isOpen() {
    return !isClose();
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    if (isClose()) {
      ctx.decreaseIndent();
    }
    ctx.append(isClose() ? "</" : "<");
    ctx.append(tagName());
    ctx.pushLexicalState(LexicalState.TSX);
    for (CodeChunk attribute : attributes()) {
      ctx.append(" ");
      ctx.appendAll(attribute);
    }
    ctx.popLexicalState();
    ctx.append(">");
    if (isOpen()) {
      ctx.increaseIndent();
    }
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return attributes().stream();
  }
}
