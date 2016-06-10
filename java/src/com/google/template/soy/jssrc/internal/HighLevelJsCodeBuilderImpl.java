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

package com.google.template.soy.jssrc.internal;

import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.shared.internal.CodeBuilder;

/**
 * {@link HighLevelJsCodeBuilder} implementation that delegates to an underlying
 * lower-level {@link CodeBuilder}.
 */
final class HighLevelJsCodeBuilderImpl implements HighLevelJsCodeBuilder {

  private final CodeBuilder<JsExpr> jsCodeBuilder;

  HighLevelJsCodeBuilderImpl(CodeBuilder<JsExpr> jsCodeBuilder) {
    this.jsCodeBuilder = jsCodeBuilder;
  }

  @Override
  public HighLevelJsCodeBuilder declareModule(String module) {
    jsCodeBuilder.appendLine("goog.module('", module, "');\n");
    return this;
  }

  @Override
  public HighLevelJsCodeBuilder declareNamespace(String namespace) {
    jsCodeBuilder.appendLine("goog.provide('", namespace, "');");
    return this;
  }

  @Override
  public HighLevelJsCodeBuilder provide(String symbol) {
    jsCodeBuilder.appendLine("goog.provide('", symbol, "');");
    return this;
  }

  @Override
  public DeclarationBuilder declareVariable(String name) {
    return new DeclarationBuilderImpl(name);
  }

  @Override
  public ConditionBuilder _if(String expr) {
    return new ConditionBuilderImpl(expr);
  }

  private final class DeclarationBuilderImpl implements DeclarationBuilder {

    final String varName;

    DeclarationBuilderImpl(String varName) {
      this.varName = varName;
    }

    @Override
    public HighLevelJsCodeBuilder withValue(String value) {
      jsCodeBuilder.appendLine("var ", varName, " = ", value, ";");
      return HighLevelJsCodeBuilderImpl.this;
    }
  }

    private final class ConditionBuilderImpl implements ConditionBuilder {

    ConditionBuilderImpl(String expr) {
      jsCodeBuilder.appendLine("if (", expr, ") {").increaseIndent();
    }

    @Override
    public ConditionBuilder _else() {
      jsCodeBuilder.decreaseIndent();
      jsCodeBuilder.appendLine("} else {").increaseIndent();
      return this;
    }

    @Override
    public HighLevelJsCodeBuilder endif() {
      jsCodeBuilder.decreaseIndent();
      jsCodeBuilder.appendLine("}");
      return HighLevelJsCodeBuilderImpl.this;
    }
  }
}
