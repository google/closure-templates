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
import static com.google.template.soy.jssrc.dsl.Expressions.stringLiteral;
import static com.google.template.soy.jssrc.dsl.TsxFragmentElement.mergeLineComments;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.jssrc.dsl.Expressions.DecoratedExpression;
import com.google.template.soy.jssrc.dsl.FormattingContext.LexicalState;
import com.google.template.soy.jssrc.dsl.Statements.DecoratedStatement;
import com.google.template.soy.jssrc.dsl.TsxPrintNode.CommandChar;
import java.util.List;
import java.util.stream.Stream;

/** Represents an {@code HtmlTagNode}. */
@AutoValue
@Immutable
public abstract class HtmlTag extends Expression implements CodeChunk.HasRequires {

  /** Tag type. */
  public enum Type {
    OPEN,
    CLOSE,
    SELF_CLOSE
  }

  public static final HtmlTag FRAGMENT_OPEN = createOpen("");
  public static final HtmlTag FRAGMENT_CLOSE = createClose("");

  public static HtmlTag createOpen(String tagName, List<? extends CodeChunk> attributes) {
    return create(stringLiteral(tagName), Type.OPEN, attributes.stream());
  }

  public static HtmlTag createOpen(Expression tagName, List<? extends CodeChunk> attributes) {
    return create(tagName, Type.OPEN, attributes.stream());
  }

  public static HtmlTag createOpen(String tagName, CodeChunk... attributes) {
    return create(stringLiteral(tagName), Type.OPEN, Stream.of(attributes));
  }

  public static HtmlTag createOpen(Expression tagName, CodeChunk... attributes) {
    return create(tagName, Type.OPEN, Stream.of(attributes));
  }

  public static HtmlTag createClose(String tagName, CodeChunk... attributes) {
    return create(stringLiteral(tagName), Type.CLOSE, Stream.of(attributes));
  }

  public static HtmlTag createClose(Expression tagName, CodeChunk... attributes) {
    return create(tagName, Type.CLOSE, Stream.of(attributes));
  }

  public static HtmlTag create(
      String tagName, Type type, Iterable<? extends CodeChunk> attributes) {
    return create(stringLiteral(tagName), type, Streams.stream(attributes));
  }

  public static HtmlTag create(
      Expression tagName, Type type, Iterable<? extends CodeChunk> attributes) {
    return create(tagName, type, Streams.stream(attributes));
  }

  private static HtmlTag create(
      Expression tagName, Type type, Stream<? extends CodeChunk> attributes) {
    return new AutoValue_HtmlTag(
        tagName,
        type,
        mergeLineComments(attributes).flatMap(HtmlTag::wrapChild).collect(toImmutableList()));
  }

  public HtmlTag withExtraAttributes(Iterable<? extends CodeChunk> attributes) {
    return create(
        tagName(), type(), Streams.concat(attributes().stream(), Streams.stream(attributes)));
  }

  private static Stream<CodeChunk> wrapChild(CodeChunk chunk) {
    if (chunk instanceof HtmlAttribute || chunk instanceof CommandChar) {
      return Stream.of(chunk);
    } else if (chunk == Expressions.EMPTY) {
      return Stream.of();
    } else if (chunk instanceof StringLiteral) {
      return Stream.of(TsxPrintNode.wrapIfNeeded((StringLiteral) chunk));
    } else if (chunk instanceof Concatenation) {
      return Stream.of(((Concatenation) chunk).map1toN(HtmlTag::wrapChild));
    } else if (chunk instanceof DecoratedStatement || chunk instanceof DecoratedExpression) {
      return mergeLineComments(chunk.childrenStream()).flatMap(TsxFragmentElement::wrapChild);
    } else if (chunk instanceof Statement) {
      return Stream.of(TsxPrintNode.wrap(((Statement) chunk).asExpr()));
    }
    return Stream.of(TsxPrintNode.wrap(chunk));
  }

  abstract Expression tagName();

  abstract Type type();

  abstract ImmutableList<? extends CodeChunk> attributes();

  public HtmlTag copyWithTagName(String newTagName) {
    return create(newTagName, type(), attributes());
  }

  public HtmlTag copyWithTagName(Expression newTagName) {
    return create(newTagName, type(), attributes());
  }

  boolean isOpen() {
    return type() == Type.OPEN;
  }

  boolean isClose() {
    return type() == Type.CLOSE;
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    if (type() == Type.CLOSE) {
      ctx.decreaseIndentLenient();
    }
    FormattingContext buffer = attributes().isEmpty() ? ctx.buffer() : ctx;
    buffer.append(type() == Type.CLOSE ? "</" : "<");
    buffer.pushLexicalState(LexicalState.TSX);
    buffer.appendAll(tagName());
    for (CodeChunk attribute : attributes()) {
      buffer.append(" ");
      buffer.appendAll(attribute);
    }
    buffer.popLexicalState();
    buffer.append(type() == Type.SELF_CLOSE ? "/>" : ">");
    if (buffer != ctx) {
      buffer.close();
    }
    if (type() == Type.OPEN) {
      ctx.increaseIndent();
    }
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Streams.concat(Stream.of(tagName()), attributes().stream());
  }

  @Override
  public ImmutableSet<GoogRequire> googRequires() {
    return ImmutableSet.of(TsxFragmentElement.ELEMENT);
  }
}
