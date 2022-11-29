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
import java.util.List;
import java.util.function.Consumer;

/** Represents a TS function type. */
public class FunctionType extends AbstractType {

  private final Expression returnType;
  private final ImmutableList<ParamDecl> params;

  public FunctionType(Expression returnType, List<ParamDecl> params) {
    this.returnType = returnType;
    this.params = ImmutableList.copyOf(params);
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    try (FormattingContext buffer = ctx.buffer()) {
      buffer.append("(");
      boolean first = true;
      for (ParamDecl param : params) {
        if (first) {
          first = false;
        } else {
          buffer.append(", ");
        }
        buffer.append(param.name() + (param.isOptional() ? "?" : ""));
        buffer.append(": ");
        buffer.appendOutputExpression(param.type());
      }
      buffer.append(") => ");
      buffer.appendOutputExpression(returnType);
    }
  }

  @Override
  public void collectRequires(Consumer<GoogRequire> collector) {
    returnType.collectRequires(collector);
    for (ParamDecl param : params) {
      param.collectRequires(collector);
    }
  }
}
