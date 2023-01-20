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
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.jssrc.dsl.FormattingContext.LexicalState;
import java.util.stream.Stream;

/**
 * Raw text within a TsxElement ("<></>"). Does not contain command chars like {sp}, since these are
 * represented with TsxPrintNode.
 */
@AutoValue
@Immutable
public abstract class RawText extends Expression {
  abstract String value();

  public static RawText create(String value) {
    return new AutoValue_RawText(value);
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    if (ctx.getCurrentLexicalState() == LexicalState.JS && !alreadyEscaped(value())) {
      String escaped =
          BaseUtils.escapeToWrappedSoyString(
              value(), ctx.getFormatOptions().htmlEscapeStrings(), QuoteStyle.SINGLE);
      ctx.append(escaped);
    } else {
      ctx.appendUnlessEmpty(value());
    }
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.empty();
  }

  private static boolean alreadyEscaped(String s) {
    return s.length() >= 2
        && ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\"")));
  }
}
