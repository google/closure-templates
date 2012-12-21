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

import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoytreeUtils;


/**
 * Visitor for replacing hasData() function with boolean 'true'.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> Note: hasData() is a no-op as of March 2012, but we don't report an error yet because there
 * are many existing usages.
 *
 * @author Kai Huang
 */
public class ReplaceHasDataFunctionVisitor {


  /**
   * Runs this pass on the given Soy tree.
   */
  public void exec(SoyFileSetNode soyTree) {
    SoytreeUtils.execOnAllV2Exprs(soyTree, new ReplaceHasDataFunctionInExprVisitor());
  }


  /**
   * Private helper class for ReplaceHasDataFunctionVisitor to visit expressions.
   * This class does the real work.
   */
  private static class ReplaceHasDataFunctionInExprVisitor extends AbstractExprNodeVisitor<Void> {

    @Override protected void visitFunctionNode(FunctionNode node) {
      if (node.getFunctionName().equals("hasData")) {
        node.getParent().replaceChild(node, new BooleanNode(true));
      }
    }

    @Override protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildrenAllowingConcurrentModification((ParentExprNode) node);
      }
    }
  }

}
