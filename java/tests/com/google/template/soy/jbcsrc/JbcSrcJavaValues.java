/*
 * Copyright 2018 Google Inc.
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

import com.google.common.base.Function;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.plugin.internal.JavaPluginExecContext;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.types.SoyTypeRegistryBuilder;
import java.util.List;

/**
 * Utilites that expose JbcSrcJavaValue-related classes for testing.
 *
 * <p>Note: this isn't in the restricted.testing package so that JbcSrcValueFactory can be package
 * private.
 */
public final class JbcSrcJavaValues {
  private JbcSrcJavaValues() {}

  public static SoyExpression computeForJavaSource(
      FunctionNode fnNode,
      JbcSrcPluginContext context,
      Function<String, Expression> pluginInstanceFn,
      List<SoyExpression> args) {
    return new JbcSrcValueFactory(
            JavaPluginExecContext.forFunctionNode(
                fnNode, (SoyJavaSourceFunction) fnNode.getSoyFunction()),
            context,
            pluginInstanceFn::apply,
            ErrorReporter.exploding(),
            SoyTypeRegistryBuilder.create())
        .computeForJavaSource(args);
  }
}
