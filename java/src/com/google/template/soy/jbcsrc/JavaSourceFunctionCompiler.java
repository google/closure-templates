/*
 * Copyright 2020 Google Inc.
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
package com.google.template.soy.jbcsrc;

import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.plugin.internal.JavaPluginExecContext;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.shared.restricted.SoySourceFunctionMethod;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.List;
import javax.annotation.Nullable;

/** Compiles method and function calls. */
final class JavaSourceFunctionCompiler {
  private final SoyTypeRegistry typeRegistry;
  private final ErrorReporter errorReporter;

  JavaSourceFunctionCompiler(SoyTypeRegistry typeRegistry, ErrorReporter errorReporter) {
    this.typeRegistry = typeRegistry;
    this.errorReporter = errorReporter;
  }

  /**
   * Compile the given method call to a {@link SoyExpression}
   *
   * @param node The AST node being compiled
   * @param method The method object to invoke
   * @param args The compiled arguments, position 0 is the {@code receiver} of the method
   * @param parameters The parameters for accessing plugin instances and the {@link
   *     JbcSrcPluginContext}, if {@code null} then we are in constant context.
   * @return a SoyExpression with the result of the method call.
   */
  SoyExpression compile(
      MethodCallNode node,
      SoySourceFunctionMethod method,
      List<SoyExpression> args,
      @Nullable TemplateParameterLookup parameters) {
    return compile(JavaPluginExecContext.forMethodCallNode(node, method), args, parameters);
  }
  /**
   * Compile the given function call to a {@link SoyExpression}
   *
   * @param node The AST node being compiled
   * @param function The function object to invoke
   * @param args The compiled arguments, position 0 is the {@code receiver} of the method
   * @param parameters The parameters for accessing plugin instances and the {@link
   *     JbcSrcPluginContext}, if {@code null} then we are in constant context.
   * @return a SoyExpression with the result of the function call.
   */
  SoyExpression compile(
      FunctionNode node,
      SoyJavaSourceFunction function,
      List<SoyExpression> args,
      @Nullable TemplateParameterLookup parameters) {
    return compile(JavaPluginExecContext.forFunctionNode(node, function), args, parameters);
  }

  private SoyExpression compile(
      JavaPluginExecContext context,
      List<SoyExpression> args,
      @Nullable TemplateParameterLookup parameters) {
    return new JbcSrcValueFactory(
            context,
            // parameters is null when we are in a constant context.
            parameters == null
                ? new JbcSrcPluginContext() {
                  private Expression error() {
                    throw new UnsupportedOperationException(
                        "Cannot access contextual data from a pure context");
                  }

                  @Override
                  public Expression getBidiGlobalDir() {
                    return error();
                  }

                  @Override
                  public Expression getAllRequiredCssNamespaces(SoyExpression template) {
                    return error();
                  }

                  @Override
                  public Expression getULocale() {
                    return error();
                  }
                }
                : parameters.getPluginContext(),
            pluginName -> {
              if (parameters == null) {
                throw new UnsupportedOperationException("Pure functions cannot have instances");
              }
              return parameters.getRenderContext().getPluginInstance(pluginName);
            },
            errorReporter,
            typeRegistry)
        .computeForJavaSource(args);
  }
}
