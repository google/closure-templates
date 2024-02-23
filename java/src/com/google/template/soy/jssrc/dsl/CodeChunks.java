/*
 * Copyright 2016 Google Inc.
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

import com.google.common.collect.Iterables;
import com.google.template.soy.internal.util.TreeStreams;
import com.google.template.soy.javagencode.KytheHelper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Utility methods for working with CodeChunks. */
public final class CodeChunks {

  /**
   * See https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Grammar_and_types#Variables
   *
   * <p>This incorrectly allows keywords, but that's not a big problem because doing so would cause
   * JSCompiler to crash. This also incorrectly disallows Unicode in identifiers, but that's not a
   * big problem because the JS backend generally names identifiers after Soy identifiers, which
   * don't allow Unicode either.
   */
  private static final Pattern ID = Pattern.compile("[A-Za-z_$][\\w$]*");

  private CodeChunks() {}

  public static String getCode(CodeChunk chunk, FormatOptions formatOptions) {
    return getCode(chunk, new FormattingContext(formatOptions));
  }

  public static String getCode(
      CodeChunk chunk, FormatOptions formatOptions, @Nullable KytheHelper kytheHelper) {
    FormattingContext context = new FormattingContext(formatOptions);
    context.setKytheHelper(kytheHelper);
    return getCode(chunk, context);
  }

  private static String getCode(CodeChunk chunk, FormattingContext context) {
    context.appendInitialStatements(chunk);

    if (chunk instanceof Expression) {
      context.clearIndent(); // Don't add unnecessary space before expression.
      context.appendOutputExpression((Expression) chunk);
      context.append(';').endLine();
    }

    return context.toString();
  }

  /** Validates that the given string is a valid javascript identifier. */
  static void checkId(String id) {
    if (!ID.matcher(id).matches()) {
      throw new IllegalArgumentException(String.format("not a valid js identifier: %s", id));
    }
  }

  public static Expression concat(List<? extends CodeChunk> chunks) {
    if (chunks.isEmpty()) {
      return StringLiteral.create("");
    } else if (chunks.size() == 1) {
      return (Expression) chunks.get(0);
    }
    if (chunks.stream().allMatch(StringLiteral.class::isInstance)) {
      return StringLiteral.create(
          chunks.stream()
              .map(StringLiteral.class::cast)
              .map(StringLiteral::literalValue)
              .collect(Collectors.joining()),
          ((StringLiteral) chunks.get(0)).quoteStyle());
    }
    List<SpecialToken> special = new ArrayList<>();
    List<Expression> exprs = new ArrayList<>();
    for (CodeChunk chunk : chunks) {
      if (chunk instanceof SpecialToken) {
        special.add((SpecialToken) chunk);
      } else if (chunk instanceof Expression) {
        Expression expr = (Expression) chunk;
        if (!special.isEmpty()) {
          expr = expr.prepend(special);
          special.clear();
        }
        exprs.add(expr);
      } else {
        throw new ClassCastException(chunk.getClass().getName());
      }
    }
    if (!special.isEmpty()) {
      if (exprs.isEmpty()) {
        exprs.add(Expressions.EMPTY.append(special));
      } else {
        exprs.set(exprs.size() - 1, Iterables.getLast(exprs).append(special));
      }
    }
    return Concatenation.create(exprs);
  }

  public static Expression concatAsObjectLiteral(List<CodeChunk> chunks) {
    Map<String, Expression> map = new LinkedHashMap<>();
    flatten(chunks.stream())
        // Un-wrap any print nodes, since we are in a TS expression.
        .map(c -> c instanceof TsxPrintNode ? ((TsxPrintNode) c).expr() : c)
        .forEach(
            c -> {
              if (c instanceof HtmlAttribute) {
                HtmlAttribute htmlAttribute = (HtmlAttribute) c;
                map.put(
                    htmlAttribute.name(),
                    htmlAttribute.value() != null
                        ? htmlAttribute.value()
                        : Expressions.LITERAL_TRUE);
              } else {
                map.put(Expressions.objectLiteralSpreadKey(), (Expression) c);
              }
            });
    if (map.size() == 1 && ObjectLiteral.isSpreadKey(Iterables.getOnlyElement(map.keySet()))) {
      // Simplify `{...d}` to just `d`. Note that sometimes there is a duplicate spread in the
      // key and value, since eg a non-static html attribute transpiles to `...d`.
      Expression val = Iterables.getOnlyElement(map.values());
      return Expressions.isSpread(val) ? ((UnaryOperation) val).arg() : val;
    }
    return Expressions.objectLiteralWithQuotedKeys(map);
  }

  public static Stream<CodeChunk> flatten(Stream<CodeChunk> chunk) {
    return chunk.flatMap(c -> c instanceof Concatenation ? c.childrenStream() : Stream.of(c));
  }

  public static Stream<? extends CodeChunk> breadthFirst(CodeChunk root) {
    return TreeStreams.breadthFirstWithStream(root, CodeChunk::childrenStream);
  }

  public static Stream<? extends CodeChunk> breadthFirst(List<? extends CodeChunk> roots) {
    return roots.stream()
        .flatMap(root -> TreeStreams.breadthFirstWithStream(root, CodeChunk::childrenStream));
  }
}
