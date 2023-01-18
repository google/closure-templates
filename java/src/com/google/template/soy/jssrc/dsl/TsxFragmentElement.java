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

/** Represents a tsx fragment elemenet, e.g.: "<>body</>". */
@AutoValue
@Immutable
public abstract class TsxFragmentElement extends Expression {
  public static Expression create(List<Statement> body) {
    return new AutoValue_TsxFragmentElement(ImmutableList.copyOf(body));
  }

  abstract ImmutableList<Statement> body();

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
    ctx.appendAll(HtmlTag.createOpen("", ImmutableList.of()));
    ctx.endLine();
    for (Statement s : body()) {
      ctx.appendAll(s);
    }
    ctx.endLine();
    ctx.appendAll(HtmlTag.createClose("", ImmutableList.of()));
  }
}
