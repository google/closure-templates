/*
 * Copyright 2011 Google Inc.
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

import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;


/**
 * Visitor for finding 'print' nodes that are actually 'remainder' nodes, and replacing them with
 * the appropriate expression.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> Important: This pass does not create any MsgPluralRemainderNodes. Instead, we simply
 * rewrite the PrintNodes to have the correct computation (subtract constant offset). This class is
 * to be used instead of InjectRemainderNodesVisitor.
 *
 * <p> {@link #exec} should be called on a full parse tree. There is no return value.
 *
 * @author Mohamed Eldawy
 * @author Kai Huang
 */
public class RewriteRemainderNodesVisitor extends AbstractSoyNodeVisitor<Void> {


  /** The MsgPluralNode most recently visited. */
  private MsgPluralNode currPluralNode;


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitPrintNode(PrintNode node) {

    ExprRootNode<?> exprRootNode = node.getExprUnion().getExpr();
    if (exprRootNode == null) {
      return;
    }

    // Check for the function node with the function "remainder()".
    if (exprRootNode.getChild(0) instanceof FunctionNode) {
      FunctionNode functionNode = (FunctionNode) exprRootNode.getChild(0);
      if (functionNode.getFunctionName().equals("remainder")) {

        if (currPluralNode == null) {
          // 'remainder' outside 'plural'. Bad!
          throw new SoySyntaxException(
              "The special function 'remainder' is for use in plural messages" +
              " (tag " + node.toSourceString() + ").");
        }
        // 'remainder' with no parameters or more than one parameter. Bad!
        if (functionNode.numChildren() != 1) {
          throw new SoySyntaxException(
              "The function 'remainder' has to have exactly one argument" +
              " (tag " + node.toSourceString() + ").");
        }
        // 'remainder' with a different expression than the enclosing 'plural'. Bad!
        if (! functionNode.getChild(0).toSourceString().equals(
                  currPluralNode.getExpr().toSourceString())) {
          throw new SoySyntaxException(
              "The parameter to 'remainder' has to be the same as the 'plural' variable" +
              " (tag " + node.toSourceString() + ").");
        }
        // 'remainder' with a 0 offset. Bad!
        if (currPluralNode.getOffset() == 0) {
          throw new SoySyntaxException(
              "In 'plural' block, use of 'remainder' function is unnecessary since offset = 0" +
              " (tag " + node.toSourceString() + ").");
        }
        // 'remainder' with 'phname' attribute. Bad!
        if (node.getUserSuppliedPlaceholderName() != null) {
          throw new SoySyntaxException(
              "Cannot use special function 'remainder' and attribute 'phname' together" +
              " (tag " + node.toSourceString() + ").");
        }

        // Now rewrite the PrintNode (reusing the old node id).
        String newExprText =
            "(" + currPluralNode.getExpr().toSourceString() + ") - " + currPluralNode.getOffset();
        PrintNode newPrintNode = new PrintNode(node.getId(), node.isImplicit(), newExprText, null);
        newPrintNode.addChildren(node.getChildren());
        node.getParent().replaceChild(node, newPrintNode);
      }
    }
  }


  @Override protected void visitMsgPluralNode(MsgPluralNode node) {
    currPluralNode = node;
    visitChildren(node);
    currPluralNode = null;
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildrenAllowingConcurrentModification((ParentSoyNode<?>) node);
    }
  }

}
