/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.passes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.restricted.SoyFunction;

/**
 * Decorates {@link FunctionNode}s with corresponding {@link SoyFunction}s, so that later passes can
 * simply read the functions off the AST.
 */
@VisibleForTesting
public final class ResolveFunctionsVisitor extends AbstractExprNodeVisitor<Void> {

  private final ImmutableMap<String, ? extends SoyFunction> soyFunctionMap;

  public ResolveFunctionsVisitor(ImmutableMap<String, ? extends SoyFunction> soyFunctionMap) {
    this.soyFunctionMap = soyFunctionMap;
  }

  @Override
  protected void visitFunctionNode(FunctionNode node) {
    String functionName = node.getFunctionName();
    SoyFunction function;
    if ((function = soyFunctionMap.get(functionName)) != null) {
      node.setSoyFunction(function);
    } else if ((function = BuiltinFunction.forFunctionName(functionName)) != null) {
      node.setSoyFunction(function);
    }
    visitChildren(node);
  }

  @Override
  protected void visitExprNode(ExprNode node) {
    if (node instanceof ParentExprNode) {
      visitChildren((ParentExprNode) node);
    }
  }
}
