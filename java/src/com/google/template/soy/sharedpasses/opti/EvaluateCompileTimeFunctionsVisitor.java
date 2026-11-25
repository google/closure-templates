/*
 * Copyright 2026 Google Inc.
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

package com.google.template.soy.sharedpasses.opti;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.internalutils.InternalValueUtils;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.shared.internal.SoyCompileTimeFunction;
import com.google.template.soy.shared.internal.SoyCompilerContext;
import com.google.template.soy.sharedpasses.render.RenderException;

/**
 * Expression visitor that evaluates {@link SoyCompileTimeFunction} calls and replaces them with
 * primitive literal nodes.
 */
public final class EvaluateCompileTimeFunctionsVisitor extends AbstractExprNodeVisitor<Void> {

  private final SoyCompilerContext compilerContext;

  public EvaluateCompileTimeFunctionsVisitor(SoyCompilerContext compilerContext) {
    this.compilerContext = compilerContext;
  }

  @Override
  protected void visitFunctionNode(FunctionNode node) {
    visitChildren(node);
    if (!node.isResolved() || !(node.getSoyFunction() instanceof SoyCompileTimeFunction fn)) {
      return;
    }
    ImmutableList.Builder<SoyValue> args = ImmutableList.builder();
    for (ExprNode param : node.getParams()) {
      if (!(param instanceof PrimitiveNode primitiveNode)) {
        return;
      }
      try {
        args.add(InternalValueUtils.convertPrimitiveExprToData(primitiveNode));
      } catch (IllegalArgumentException e) {
        return;
      }
    }
    SoyValue evalResult;
    try {
      evalResult = fn.eval(args.build(), compilerContext);
    } catch (RenderException e) {
      return;
    }
    if (evalResult instanceof PrimitiveData primitiveData) {
      PrimitiveNode newNode =
          InternalValueUtils.convertPrimitiveDataToExpr(primitiveData, node.getSourceLocation());
      if (newNode != null) {
        node.getParent().replaceChild(node, newNode);
      }
    }
  }

  @Override
  protected void visitExprNode(ExprNode node) {
    if (node instanceof ParentExprNode parent) {
      visitChildren(parent);
    }
  }
}
