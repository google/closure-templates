/*
 * Copyright 2018 Google Inc.
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
import static com.google.template.soy.jssrc.dsl.TsxFragmentElement.mergeLineComments;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.jssrc.dsl.FormattingContext.LexicalState;
import com.google.template.soy.jssrc.dsl.TsxPrintNode.CommandChar;
import java.util.List;
import java.util.stream.Stream;

/** Represents a JavaScript template literal expression. */
@AutoValue
@Immutable
public abstract class TemplateLiteral extends Expression {

  public static TemplateLiteral create(List<? extends CodeChunk> body) {
    return new AutoValue_TemplateLiteral(
        mergeLineComments(body.stream())
            .map(TemplateLiteral::wrapChild)
            .collect(toImmutableList()));
  }

  private static Expression wrapChild(CodeChunk chunk) {
    if (chunk instanceof TsxPrintNode
        || chunk instanceof StringLiteral
        || chunk instanceof CommandChar) {
      return (Expression) chunk;
    } else if (chunk instanceof Concatenation) {
      return ((Concatenation) chunk).map1to1(TemplateLiteral::wrapChild);
    } else if (chunk instanceof Statement) {
      return TsxPrintNode.wrap(((Statement) chunk).asExpr());
    }
    return TsxPrintNode.wrap(chunk);
  }

  abstract ImmutableList<? extends CodeChunk> body();

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return body().stream();
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.pushLexicalState(LexicalState.TTL);

    ctx.append('`');
    for (CodeChunk s : body()) {
      ctx.appendAll(s);
    }
    ctx.append('`');

    ctx.popLexicalState();
  }
}
