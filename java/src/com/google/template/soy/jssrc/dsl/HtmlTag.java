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
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.List;
import java.util.stream.Stream;

/** Represents an {@code HtmlTagNode}. */
@AutoValue
@Immutable
public abstract class HtmlTag extends Expression {

  public static final HtmlTag FRAGMENT_OPEN = createOpen("", ImmutableList.of());
  public static final HtmlTag FRAGMENT_CLOSE = createClose("", ImmutableList.of());

  abstract String tagName();

  abstract boolean isClose();

  abstract ImmutableList<? extends CodeChunk> attributes();

  public static HtmlTag createOpen(String tagName, List<? extends CodeChunk> attributes) {
    return new AutoValue_HtmlTag(tagName, false, ImmutableList.copyOf(attributes));
  }

  public static HtmlTag createOpen(String tagName, CodeChunk... attributes) {
    return new AutoValue_HtmlTag(tagName, false, ImmutableList.copyOf(attributes));
  }

  public static HtmlTag createClose(String tagName, List<? extends CodeChunk> attributes) {
    return new AutoValue_HtmlTag(tagName, true, ImmutableList.copyOf(attributes));
  }

  public static HtmlTag createClose(String tagName, CodeChunk... attributes) {
    return new AutoValue_HtmlTag(tagName, true, ImmutableList.copyOf(attributes));
  }

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
    for (CodeChunk attribute : attributes()) {
      ctx.append(" ");
      ctx.appendAll(attribute);
    }
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
