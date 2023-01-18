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

import static com.google.template.soy.jssrc.dsl.Expression.id;
import static com.google.template.soy.jssrc.dsl.Expression.stringLiteral;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/** Represents an Html attribute. */
@AutoValue
@Immutable
public abstract class HtmlAttribute extends Statement {

  abstract ImmutableList<CodeChunk> children();

  public static HtmlAttribute create(List<CodeChunk> children) {
    return new AutoValue_HtmlAttribute(ImmutableList.copyOf(children));
  }

  public static HtmlAttribute create(String name, @Nullable String value) {
    ImmutableList.Builder<CodeChunk> attrs = ImmutableList.<CodeChunk>builder().add(id(name));
    if (value != null) {
      attrs.add(stringLiteral(value));
    }
    return new AutoValue_HtmlAttribute(attrs.build());
  }

  public static HtmlAttribute create(String name, @Nullable Expression value) {
    ImmutableList.Builder<CodeChunk> attrs = ImmutableList.<CodeChunk>builder().add(id(name));
    if (value != null) {
      attrs.add(TsxPrintNode.create(value));
    }
    return new AutoValue_HtmlAttribute(attrs.build());
  }

  @Override
  void doFormatStatement(FormattingContext ctx) {
    appendChild(ctx, children().get(0));
    if (children().size() > 1) {
      ctx.append("=");
      for (CodeChunk attribute : children().subList(1, children().size())) {
        appendChild(ctx, attribute);
      }
    }
  }

  private void appendChild(FormattingContext ctx, CodeChunk chunk) {
    if (chunk instanceof Expression) {
      ctx.appendOutputExpression((Expression) chunk);
    } else {
      ctx.append(chunk.getCode(ctx.getFormatOptions()));
    }
  }

  @Override
  public void collectRequires(Consumer<GoogRequire> collector) {
    children().forEach(c -> c.collectRequires(collector));
  }
}
