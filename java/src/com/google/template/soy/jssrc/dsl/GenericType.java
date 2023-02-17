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
import java.util.List;
import java.util.stream.Stream;

/** Represents a TS generic type, for use with eg `new` statements. */
@AutoValue
public abstract class GenericType extends AbstractType {

  public static GenericType create(Expression className, List<Expression> generics) {
    return new AutoValue_GenericType(className, ImmutableList.copyOf(generics));
  }

  abstract Expression className();

  abstract ImmutableList<Expression> generics();

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    try (FormattingContext buffer = ctx.buffer()) {
      buffer.appendOutputExpression(className());
      buffer.append("<");
      for (int i = 0; i < generics().size(); i++) {
        buffer.appendOutputExpression(generics().get(i));
        if (i < generics().size() - 1) {
          buffer.append(", ");
        }
      }
      buffer.append(">");
    }
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.concat(Stream.of(className()), generics().stream());
  }
}
