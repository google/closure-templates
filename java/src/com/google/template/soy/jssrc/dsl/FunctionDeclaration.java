/*
 * Copyright 2017 Google Inc.
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
import com.google.template.soy.jssrc.dsl.JsDoc.Param;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Represents an anonymous JavaScript function declaration.
 *
 * <p>Example:
 *
 * <p><code>{@literal
 * function(param1, param2) { < function body > }
 * }</code>
 */
@AutoValue
public abstract class FunctionDeclaration extends Expression
    implements Expression.InitialStatementsScope {

  /**
   * Outputs a stringified parameter list (e.g. `foo, bar, baz`) from JsDoc. Used e.g. in function
   * and method declarations.
   */
  static String generateParamList(JsDoc jsDoc, boolean addInlineTypeAnnotations) {
    ImmutableList<Param> params = jsDoc.params();
    List<String> functionParameters = new ArrayList<>();
    for (Param param : params) {
      if (param.annotationType().equals("param")) {
        if (addInlineTypeAnnotations) {
          // TODO(lukes): this is the wrong format for inline type annotations. comments need to
          // start with '/**'.
          functionParameters.add(String.format("/* %s */ %s", param.type(), param.paramTypeName()));
        } else if (param.isVarArgs()) {
          functionParameters.add("..." + param.paramTypeName());
        } else {
          functionParameters.add(param.paramTypeName());
        }
      }
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < functionParameters.size(); i++) {
      sb.append(functionParameters.get(i));
      if (i + 1 < functionParameters.size()) {
        sb.append(", ");
      }
    }
    return sb.toString();
  }

  abstract JsDoc jsDoc();

  abstract Statement body();

  public static FunctionDeclaration create(JsDoc jsDoc, Statement body) {
    return new AutoValue_FunctionDeclaration(jsDoc, body);
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.of(body(), jsDoc());
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.append("function(");
    ctx.append(generateParamList(jsDoc(), false));
    ctx.append(") ");
    if (body().equals(Statements.EMPTY)) {
      ctx.append("{}");
    } else {
      ctx.appendAllIntoBlock(body());
    }
  }
}
