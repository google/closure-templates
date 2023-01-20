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

import static com.google.common.collect.ImmutableList.toImmutableList;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

  /** Validates that the given string is a valid javascript identifier. */
  static void checkId(String id) {
    if (!ID.matcher(id).matches()) {
      throw new IllegalArgumentException(String.format("not a valid js identifier: %s", id));
    }
  }

  public static Expression concat(List<? extends CodeChunk> chunks) {
    if (chunks.size() == 1) {
      return (Expression) chunks.get(0);
    }
    if (chunks.stream().allMatch(RawText.class::isInstance)) {
      return RawText.create(
          chunks.stream()
              .map(RawText.class::cast)
              .map(RawText::value)
              .collect(Collectors.joining()));
    }
    if (chunks.stream().allMatch(StringLiteral.class::isInstance)) {
      return StringLiteral.create(
          chunks.stream()
              .map(StringLiteral.class::cast)
              .map(StringLiteral::literalValue)
              .collect(Collectors.joining()));
    }
    return Concatenation.create(
        chunks.stream()
            .map(
                c -> {
                  if (c instanceof Expression) {
                    return (Expression) c;
                  } else {
                    throw new ClassCastException(c.getClass().getName());
                  }
                })
            .collect(toImmutableList()));
  }
}
