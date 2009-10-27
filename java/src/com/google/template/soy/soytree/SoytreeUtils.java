/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.soytree;

import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.ParentExprHolderNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;

import javax.annotation.Nullable;


/**
 * Shared utilities for the 'soytree' package.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class SoytreeUtils {


  /**
   * Creates a SoySyntaxException with template name and file name filled in based on the given
   * Soy parse tree node. The Soy node must be part of a full parse tree.
   *
   * @param message The exception message, or null to use the messge from the cause. Parameters
   *     'message' and 'cause' may not both be null.
   * @param cause The cause of this exception, or null if not applicable. Parameters 'message' and
   *     'cause' may not both be null.
   * @param soyNode The Soy node to get the template name and file name from.
   * @return The new SoySyntaxException object.
   */
  @SuppressWarnings("ThrowableInstanceNeverThrown")  // this function returns the exception object
  public static SoySyntaxException createSoySyntaxExceptionWithMetaInfo(
      @Nullable String message, @Nullable Throwable cause, SoyNode soyNode) {

    SoySyntaxException sse;
    if (message != null && cause != null) {
      sse = new SoySyntaxException(message, cause);
    } else if (message != null) {
      sse = new SoySyntaxException(message);
    } else if (cause != null) {
      sse = new SoySyntaxException(cause);
    } else {
      throw new AssertionError();
    }

    TemplateNode template = soyNode.getNearestAncestor(TemplateNode.class);
    if (template != null) {
      sse.setTemplateName(template.getTemplateName());
    }
    SoyFileNode soyFile = soyNode.getNearestAncestor(SoyFileNode.class);
    if (soyFile != null) {
      sse.setFilePath(soyFile.getFilePath());
    }

    return sse;
  }


  /**
   * Given a Soy tree and a visitor for expression trees, traverses the Soy tree and executes the
   * visitor on all expressions held by nodes in the Soy tree.
   *
   * @param soyTree The Soy tree to traverse.
   * @param exprNodeVisitor The visitor to execute on all expressions.
   */
  public static void visitAllExprs(
      SoyFileSetNode soyTree, AbstractExprNodeVisitor<Void> exprNodeVisitor) {

    (new VisitAllExprsVisitor(exprNodeVisitor)).exec(soyTree);
  }


  /**
   * Private helper class for {@code visitAllExprs}.
   */
  private static class VisitAllExprsVisitor extends AbstractSoyNodeVisitor<Void> {

    private final AbstractExprNodeVisitor<Void> exprNodeVisitor;

    public VisitAllExprsVisitor(AbstractExprNodeVisitor<Void> exprNodeVisitor) {
      this.exprNodeVisitor = exprNodeVisitor;
    }

    @Override protected void visitInternal(SoyNode node) {
      // Nothing to do for nodes not handled elsewhere.
    }

    @Override protected void visitInternal(ParentSoyNode<? extends SoyNode> node) {
      visitChildren(node);
    }

    @Override protected void visitInternal(ExprHolderNode node) {
      visitExprs(node);
    }

    @Override protected void visitInternal(ParentExprHolderNode<? extends SoyNode> node) {
      visitChildren(node);
      visitExprs(node);
    }

    private void visitExprs(ExprHolderNode exprHolder) {
      for (ExprRootNode<? extends ExprNode> expr : exprHolder.getAllExprs()) {
        try {
          exprNodeVisitor.exec(expr);
        } catch (SoySyntaxException sse) {
          throw createSoySyntaxExceptionWithMetaInfo(sse.getMessage(), sse.getCause(), exprHolder);
        }
      }
    }
  }

}
