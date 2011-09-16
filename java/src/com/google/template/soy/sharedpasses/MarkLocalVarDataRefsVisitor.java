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

package com.google.template.soy.sharedpasses;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.ExprUnion;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.LocalVarBlockNode;
import com.google.template.soy.soytree.SoyNode.LocalVarInlineNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;


/**
 * Visitor to determine and mark which DataRefNodes are actually local var data refs. Marking is
 * done by calling {@link DataRefNode#setIsLocalVarDataRef}.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> This visitor must be called on a SoyFileSetNode, SoyFileNode, or TemplateNode (i.e. template
 * or ancestor of a template).
 *
 * <p> Note: Expressions not in V1 syntax will not have expression trees, so this pass doesn't
 * handle them.
 *
 * @see UnmarkLocalVarDataRefsVisitor
 * @author Kai Huang
 */
public class MarkLocalVarDataRefsVisitor extends AbstractSoyNodeVisitor<Void> {


  /** Stack of frames containing sets of local vars currently defined (during pass). */
  private Deque<Set<String>> localVarFrames;

  /** The associated expr visitor instance. */
  private MarkLocalVarDataRefsInExprVisitor markLocalVarDataRefsInExprVisitor;


  @Override public Void exec(SoyNode node) {

    Preconditions.checkArgument(
        node instanceof SoyFileSetNode || node instanceof SoyFileNode ||
        node instanceof TemplateNode);

    return super.exec(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitTemplateNode(TemplateNode node) {

    localVarFrames = new ArrayDeque<Set<String>>();
    markLocalVarDataRefsInExprVisitor = new MarkLocalVarDataRefsInExprVisitor(localVarFrames);

    visitSoyNode(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {

    if (node instanceof ExprHolderNode) {
      visitExprHolderHelper((ExprHolderNode) node);
    }

    if (node instanceof LocalVarInlineNode) {
      localVarFrames.peek().add(((LocalVarInlineNode) node).getVarName());
    }

    if (node instanceof ParentSoyNode<?>) {

      if (node instanceof LocalVarBlockNode) {
        LocalVarBlockNode nodeAsLocalVarBlock = (LocalVarBlockNode) node;
        Set<String> newLocalVarFrame = Sets.newHashSet();
        newLocalVarFrame.add(nodeAsLocalVarBlock.getVarName());
        localVarFrames.push(newLocalVarFrame);
        visitChildren(nodeAsLocalVarBlock);
        localVarFrames.pop();

      } else if (node instanceof BlockNode) {
        localVarFrames.push(Sets.<String>newHashSet());
        visitChildren((BlockNode) node);
        localVarFrames.pop();

      } else {
        visitChildren((ParentSoyNode<?>) node);
      }
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Helpers.


  /**
   * Helper for visiting a node that holds one or more expressions.
   * @param exprHolder The node holding the expressions to be visited.
   */
  private void visitExprHolderHelper(ExprHolderNode exprHolder) {
    for (ExprUnion exprUnion : exprHolder.getAllExprUnions()) {
      if (exprUnion.getExpr() != null) {
        markLocalVarDataRefsInExprVisitor.exec(exprUnion.getExpr());
      }
    }
  }


  /**
   * Helper visitor to determine and mark which DataRefNodes within an expression are actually local
   * var data refs.
   */
  private static class MarkLocalVarDataRefsInExprVisitor extends AbstractExprNodeVisitor<Void> {

    /** The stack of frames containing sets of local vars currently defined. */
    private final Deque<Set<String>> localVarFrames;

    /**
     * @param localVarFrames The stack of frames containing sets of local vars currently defined.
     */
    public MarkLocalVarDataRefsInExprVisitor(Deque<Set<String>> localVarFrames) {
      this.localVarFrames = localVarFrames;
    }

    // ------ Implementations for specific nodes. ------

    @Override protected void visitDataRefNode(DataRefNode node) {

      // Determine whether the first key references a local variable.
      boolean firstKeyIsLocalVar;
      if (node.isIjDataRef()) {
        firstKeyIsLocalVar = false;
      } else {
        String firstKey = node.getFirstKey();
        firstKeyIsLocalVar = false;
        for (Set<String> localVarFrame : localVarFrames) {
          if (localVarFrame.contains(firstKey)) {
            firstKeyIsLocalVar = true;
            break;
          }
        }
      }

      // Mark whether the node references local var data.
      node.setIsLocalVarDataRef(firstKeyIsLocalVar);

      // Important: Must visit children since children may be expressions that contain data refs.
      visitChildren(node);
    }

    // ------ Fallback implementation. ------

    @Override protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildren((ParentExprNode) node);
      }
    }
  }

}
