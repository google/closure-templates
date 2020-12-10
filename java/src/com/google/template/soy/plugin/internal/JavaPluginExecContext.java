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

package com.google.template.soy.plugin.internal;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.shared.restricted.SoySourceFunctionMethod;
import com.google.template.soy.types.SoyType;

/**
 * Context needed by TOFU and JBCSRC for executing a {@link SoyJavaSourceFunction}. A
 * SoyJavaSourceFunction can be executed either in the context of a {@link FunctionNode} or a {@link
 * MethodCallNode}.
 *
 * @see com.google.template.soy.sharedpasses.render.TofuValueFactory
 * @see com.google.template.soy.jbcsrc.JbcSrcValueFactory
 */
public final class JavaPluginExecContext {

  /**
   * A method node referencing a @SoyMethodSignature that is implemented with a
   * SoyJavaSourceFunction will pass as arguments to the function 1. the method receiver followed by
   * 2. the method arguments.
   */
  public static JavaPluginExecContext forMethodCallNode(
      MethodCallNode methodNode, SoySourceFunctionMethod method) {
    return new JavaPluginExecContext(
        (SoyJavaSourceFunction) method.getImpl(),
        methodNode,
        methodNode.getMethodName().identifier(),
        ImmutableList.<SoyType>builder()
            .add(method.getBaseType())
            .addAll(method.getArgTypes())
            .build());
  }

  public static JavaPluginExecContext forFunctionNode(FunctionNode node, SoyJavaSourceFunction fn) {
    return new JavaPluginExecContext(
        fn, node, node.getStaticFunctionName(), node.getAllowedParamTypes());
  }

  private final SoyJavaSourceFunction sourceFunction;
  private final ExprNode node;
  private final String functionName;
  private final ImmutableList<SoyType> paramTypes;

  private JavaPluginExecContext(
      SoyJavaSourceFunction sourceFunction,
      ExprNode node,
      String functionName,
      ImmutableList<SoyType> paramTypes) {
    this.sourceFunction = sourceFunction;
    this.node = node;
    this.functionName = functionName;
    this.paramTypes = paramTypes;
  }

  public SoyJavaSourceFunction getSourceFunction() {
    return sourceFunction;
  }

  /** The param types accepted by the {@link SoyJavaSourceFunction}. */
  public ImmutableList<SoyType> getParamTypes() {
    return paramTypes;
  }

  public String getFunctionName() {
    return functionName;
  }

  public String toSourceString() {
    return node.toSourceString();
  }

  public SourceLocation getSourceLocation() {
    return node.getSourceLocation();
  }

  public SoyType getReturnType() {
    return node.getType();
  }

  public Node getNode() {
    return node;
  }
}
