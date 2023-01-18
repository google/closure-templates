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
import java.util.function.Consumer;

/**
 * Raw text within a TsxElement ("<></>"). Does not contain command chars like {sp}, since these are
 * represented with TsxPrintNode.
 */
@AutoValue
@Immutable
public abstract class RawText extends Statement {
  abstract String value();

  public static RawText create(String value) {
    return new AutoValue_RawText(value);
  }

  @Override
  void doFormatStatement(FormattingContext ctx) {
    if (value().isEmpty()) {
      return;
    }
    ctx.append(value());
  }

  @Override
  public void collectRequires(Consumer<GoogRequire> collector) {}
}
