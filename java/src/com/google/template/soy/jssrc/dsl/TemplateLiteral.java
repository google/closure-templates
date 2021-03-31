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

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.jssrc.restricted.JsExpr;
import java.util.function.Consumer;

/** Represents a JavaScript template literal expression. */
@AutoValue
@Immutable
public abstract class TemplateLiteral extends Expression {

  /**
   * The string literal parts of a template literal. E.g. in `a${b}c`, the string parts are
   * ["a","c"].
   */
  abstract ImmutableList<String> stringParts();

  /**
   * The interpolated expressions of a template literal. E.g. in `a${b}c${d}e`, the interpolated
   * parts are [b, d]. If there are N string parts, there must be N-1 interpolated parts.
   */
  abstract ImmutableList<Expression> interpolatedParts();

  public static TemplateLiteral create(
      ImmutableList<String> stringParts, ImmutableList<Expression> interpolatedParts) {
    int numStrings = stringParts.size();
    int numInterps = interpolatedParts.size();
    int expectedNumInterps = stringParts.size() - 1;
    Preconditions.checkArgument(
        numInterps == expectedNumInterps,
        String.format(
            "Template literal has %d string parts, so %d interpolated parts were expected, but"
                + " there are %d interpolated parts.",
            numStrings, expectedNumInterps, numInterps));
    ImmutableList<Statement> initialStatements =
        interpolatedParts.stream()
            .flatMap(part -> part.initialStatements().stream())
            .collect(ImmutableList.toImmutableList());
    return new AutoValue_TemplateLiteral(initialStatements, stringParts, interpolatedParts);
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    for (Expression part : interpolatedParts()) {
      ctx.appendInitialStatements(part);
    }
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.append('`');
    for (int i = 0; i < stringParts().size(); i++) {
      ctx.append(escapeStringPart(stringParts().get(i)));
      if (i < stringParts().size() - 1) {
        ctx.append("${");
        interpolatedParts().get(i).doFormatOutputExpr(ctx);
        ctx.append("}");
      }
    }
    ctx.append('`');
  }

  @Override
  public JsExpr singleExprOrName() {
    FormattingContext ctx = new FormattingContext();
    ctx.appendOutputExpression(this);
    return new JsExpr(ctx.toString(), Integer.MAX_VALUE);
  }

  private static String escapeStringPart(String literal) {
    // Escape non-ASCII characters since browsers are inconsistent in how they interpret utf-8 in
    // JS source files.
    String escaped =
        BaseUtils.escapeToSoyString(literal, /* shouldEscapeToAscii= */ true, QuoteStyle.BACKTICK);

    // </script in a JavaScript string will end the current script tag in most browsers. Escape the
    // forward slash in the string to get around this issue.
    return escaped.replace("</script", "<\\/script");
  }

  @Override
  public void collectRequires(Consumer<GoogRequire> collector) {
    for (Expression part : interpolatedParts()) {
      part.collectRequires(collector);
    }
  }
}
