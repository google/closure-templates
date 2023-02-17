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
import java.util.stream.Stream;

/** Represents a TS record type, for use with eg `new` statements. */
@AutoValue
public abstract class ArrayType extends AbstractType {

  public static ArrayType create(boolean readonly, Expression simpleType) {
    return new AutoValue_ArrayType(readonly, simpleType);
  }

  abstract boolean readonly();

  abstract Expression simpleType();

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    try (FormattingContext buffer = ctx.buffer()) {
      if (readonly()) {
        buffer.append("readonly ");
      }
      buffer.appendOutputExpression(simpleType()).append("[]");
    }
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.of(simpleType());
  }
}
