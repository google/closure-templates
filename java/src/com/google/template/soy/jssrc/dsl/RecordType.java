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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Consumer;

/** Represents a TS record type, for use with eg `new` statements. */
public class RecordType extends AbstractType {

  private final ImmutableList<Expression> keys;
  private final ImmutableList<Expression> types;
  private final ImmutableList<Boolean> optional;

  RecordType(List<Expression> keys, List<Expression> types, List<Boolean> optional) {
    Preconditions.checkArgument(keys.size() == types.size());
    Preconditions.checkArgument(keys.size() == optional.size());
    this.keys = ImmutableList.copyOf(keys);
    this.types = ImmutableList.copyOf(types);
    this.optional = ImmutableList.copyOf(optional);
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.append("{");
    for (int i = 0; i < keys.size(); i++) {
      ctx.appendOutputExpression(keys.get(i));
      if (optional.get(i)) {
        ctx.append("?");
      }
      ctx.append(": ");
      ctx.appendOutputExpression(types.get(i));
      if (i < keys.size() - 1) {
        ctx.append(", ");
      }
    }
    ctx.append("}");
  }

  @Override
  public void collectRequires(Consumer<GoogRequire> collector) {
    for (Expression memberType : types) {
      memberType.collectRequires(collector);
    }
  }
}
