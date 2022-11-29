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

import com.google.common.collect.ImmutableList;
import java.util.function.Consumer;

/** Represents a TS generic type, for use with eg `new` statements. */
public class GenericType extends AbstractType {

  private final Expression className;
  private final ImmutableList<Expression> generics;

  GenericType(Expression className, ImmutableList<Expression> generics) {
    this.className = className;
    this.generics = generics;
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    try (FormattingContext buffer = ctx.buffer()) {
      buffer.appendOutputExpression(className);
      buffer.append("<");
      for (int i = 0; i < generics.size(); i++) {
        buffer.appendOutputExpression(generics.get(i));
        if (i < generics.size() - 1) {
          buffer.append(", ");
        }
      }
      buffer.append(">");
    }
  }

  @Override
  public void collectRequires(Consumer<GoogRequire> collector) {
    className.collectRequires(collector);
    for (Expression generic : generics) {
      generic.collectRequires(collector);
    }
  }
}
