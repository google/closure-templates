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

import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.basetree.SyntaxVersionBound;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.ExprUnion;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;


/**
 * Visitor for replacing hasData() function with boolean 'true'.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> Note: hasData() is a no-op as of March 2012.
 * <p> Note: hasData() is disallowed at syntax version 2.2.
 *
 */
public class ReplaceHasDataFunctionVisitor extends AbstractSoyNodeVisitor<Void> {


  private final SyntaxVersion declaredSyntaxVersion;

  public ReplaceHasDataFunctionVisitor(SyntaxVersion declaredSyntaxVersion) {
    this.declaredSyntaxVersion = declaredSyntaxVersion;
  }

  /**
   * Recursively identifies all Soy nodes that contain expressions, and recurse to
   * each expression.
   */
  @Override protected void visitSoyNode(SoyNode node) {

    if (node instanceof ExprHolderNode) {
      for (ExprUnion exprUnion : ((ExprHolderNode) node).getAllExprUnions()) {
        if (exprUnion.getExpr() == null) {
          continue;
        }
        (new ReplaceHasDataFunctionInExprVisitor((ExprHolderNode) node)).exec(exprUnion.getExpr());
      }
    }

    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }


  /**
   * Private helper class for ReplaceHasDataFunctionVisitor to visit expressions.
   * This class does the real work.
   */
  private class ReplaceHasDataFunctionInExprVisitor extends AbstractExprNodeVisitor<Void> {

    /** The SoyNode that holds the expression being visited. */
    private final ExprHolderNode holder;

    public ReplaceHasDataFunctionInExprVisitor(ExprHolderNode holder) {
      this.holder = holder;
    }

    @Override protected void visitFunctionNode(FunctionNode node) {
      if (node.getFunctionName().equals("hasData")) {
        // Function hasData() is only allowed for syntax version 2.1 and below.
        if (declaredSyntaxVersion.num < SyntaxVersion.V2_2.num) {
          node.getParent().replaceChild(node, new BooleanNode(true));
        }
        // Always set the bound, regardless of declaredSyntaxVersion, because we might later
        // determine a higher required syntax version for the Soy file containing this node, due to
        // features used in the Soy file.
        holder.maybeSetSyntaxVersionBound(new SyntaxVersionBound(
            SyntaxVersion.V2_2, "Function hasData() is unnecessary and no longer allowed."));
      }
    }

    @Override protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildrenAllowingConcurrentModification((ParentExprNode) node);
      }
    }
  }

}
