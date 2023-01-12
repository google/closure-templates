/*
 * Copyright 2023 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.VeDefNode;
import com.google.template.soy.passes.CompilerFileSetPass.Result;
import com.google.template.soy.soytree.ConstNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;

/** Finds ve_def() calls and transforms them into VeDefNodes. */
@RunBefore({ResolveExpressionTypesPass.class})
final class VeDefPass implements CompilerFileSetPass {

  private static final SoyErrorKind VE_DEF_OUTSIDE_CONST =
      SoyErrorKind.of("Visual elements defined with ve_def() must be assigned to a constant.");

  private static final SoyErrorKind BAD_VE_DEF_ID =
      SoyErrorKind.of("The first argument to ve_def must be an integer literal.");

  private final ErrorReporter errorReporter;

  VeDefPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    for (SoyFileNode file : sourceFiles) {
      SoyTreeUtils.allNodesOfType(file, ConstNode.class).forEach(this::buildVeDefNode);
      SoyTreeUtils.allNodesOfType(file, FunctionNode.class)
          .filter(VeDefPass::isVeDef)
          .forEach(
              expr -> {
                errorReporter.report(expr.getSourceLocation(), VE_DEF_OUTSIDE_CONST);
                replaceWithEmptyVeDefNode(expr);
              });
    }
    return Result.CONTINUE;
  }

  private void buildVeDefNode(ConstNode constNode) {
    ExprNode expr = constNode.getExpr().getRoot();
    if (isVeDef(expr)) {
      FunctionNode func = (FunctionNode) expr;
      String veName = constNode.getVar().name();
      if (!(func.getChild(0) instanceof IntegerNode)) {
        errorReporter.report(func.getChild(0).getSourceLocation(), BAD_VE_DEF_ID);
        replaceWithEmptyVeDefNode(expr);
        return;
      }
      long veId = ((IntegerNode) func.getChild(0)).getValue();
      VeDefNode veDefNode = new VeDefNode(veName, veId, expr.getSourceLocation());
      func.removeChild(0);
      veDefNode.addChildren(func.getChildren());
      func.getParent().replaceChild(func, veDefNode);
    }
  }

  private static boolean isVeDef(ExprNode node) {
    if (!(node instanceof FunctionNode)) {
      return false;
    }
    FunctionNode functionNode = (FunctionNode) node;
    return functionNode.hasStaticName() && functionNode.getStaticFunctionName().equals("ve_def");
  }

  /**
   * Replaces invalid ve_def() with an empty VeDefNode so that subsequent, redundent errors are not
   * thrown.
   */
  private void replaceWithEmptyVeDefNode(ExprNode expr) {
    expr.getParent().replaceChild(expr, new VeDefNode("", 0, expr.getSourceLocation()));
  }
}
