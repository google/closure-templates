/*
 * Copyright 2012 Google Inc.
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

package com.google.template.soy.parsepasses;

import com.google.common.annotations.VisibleForTesting;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoytreeUtils;


/**
 * Visitor for rewriting the binary '?:' (null-coalescing) operator.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class RewriteNullCoalescingOpVisitor {


  /**
   * Runs this pass on the given Soy node's subtree.
   */
  public void exec(SoyNode node) {
    SoytreeUtils.execOnAllV2Exprs(node, new RewriteNullCoalescingOpInExprVisitor());
  }


  /**
   * Private helper class for RewriteNullCoalescingOpVisitor to visit expressions.
   * This class does the real work.
   */
  @VisibleForTesting
  static class RewriteNullCoalescingOpInExprVisitor extends AbstractExprNodeVisitor<Void> {


    @Override protected void visitNullCoalescingOpNode(NullCoalescingOpNode node) {

      visitChildrenAllowingConcurrentModification(node);

      ExprNode operand0a = node.getChild(0);
      ExprNode operand0b = operand0a.clone();
      ExprNode operand1 = node.getChild(1);

      FunctionNode isNonnullFnNode = new FunctionNode("isNonnull");
      isNonnullFnNode.addChild(operand0a);

      ConditionalOpNode condOpNode = new ConditionalOpNode();
      condOpNode.addChild(isNonnullFnNode);
      condOpNode.addChild(operand0b);
      condOpNode.addChild(operand1);

      node.getParent().replaceChild(node, condOpNode);
    }


    @Override protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildrenAllowingConcurrentModification((ParentExprNode) node);
      }
    }

  }

}
