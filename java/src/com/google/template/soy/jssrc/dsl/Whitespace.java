/*
 * Copyright 2023 Google Inc.
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
import com.google.common.base.Splitter;
import com.google.errorprone.annotations.Immutable;

/** Preserved whitespace. */
@AutoValue
@Immutable
public abstract class Whitespace extends SpecialToken {

  public static final Whitespace BLANK_LINE = create("\n\n");

  abstract String content();

  public static Whitespace create(String content) {
    return new AutoValue_Whitespace(content);
  }

  @Override
  void doFormatToken(FormattingContext ctx) {
    Splitter.on('\n').splitToStream(content()).skip(2).forEach(line -> ctx.appendBlankLine());
  }
}
