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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.stream.Stream;

/** Represents a JavaScript array literal expression. */
@AutoValue
@Immutable
public abstract class ArrayLiteral extends Expression {

  abstract ImmutableList<? extends Expression> elements();

  public static ArrayLiteral create(ImmutableList<? extends Expression> elements) {
    return new AutoValue_ArrayLiteral(elements);
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return elements().stream();
  }

  @Override
  public boolean isDefinitelyNotNull() {
    return true;
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.append('[');
    boolean first = true;
    for (Expression element : elements()) {
      if (first) {
        first = false;
      } else {
        ctx.append(", ");
      }
      ctx.appendOutputExpression(element);
    }
    ctx.append(']');
  }
}
