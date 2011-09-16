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

import com.google.template.soy.base.IdGenerator;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
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

    associateMetaInfoWithException(soyNode, sse);

    return sse;
  }


  private static void associateMetaInfoWithException(SoyNode soyNode, SoySyntaxException sse) {
    TemplateNode template = soyNode.getNearestAncestor(TemplateNode.class);
    if (sse.getSourceLocation() == SourceLocation.UNKNOWN) {
      sse.setSourceLocation(soyNode.getLocation());
    }
    if (sse.getTemplateName() == null && template != null) {
      sse.setTemplateName(template.getTemplateNameForUserMsgs());
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Utils for executing an ExprNode visitor on all expressions in a Soy tree.


  /**
   * Given a Soy node and a visitor for expression trees, traverses the subtree of the node and
   * executes the visitor on all expressions held by nodes in the subtree.
   *
   * <p> Only processes expressions in V2 syntax. Ignores all expressions in V1 syntax.
   *
   * @param <R> The ExprNode visitor's return type.
   * @param node The root of the subtree to visit all expressions in.
   * @param exprNodeVisitor The visitor to execute on all expressions.
   */
  public static <R> void execOnAllV2Exprs(
      SoyNode node, AbstractExprNodeVisitor<R> exprNodeVisitor) {

    execOnAllV2ExprsShortcircuitably(node, exprNodeVisitor, null);
  }


  /**
   * Given a Soy node and a visitor for expression trees, traverses the subtree of the node and
   * executes the visitor on all expressions held by nodes in the subtree.
   *
   * <p> Only processes expressions in V2 syntax. Ignores all expressions in V1 syntax.
   *
   * <p> Same as {@code visitAllExprs} except that this pass can be shortcircuited by providing a
   * {@link Shortcircuiter}.
   *
   * @param <R> The ExprNode visitor's return type.
   * @param node The root of the subtree to visit all expressions in.
   * @param exprNodeVisitor The visitor to execute on all expressions.
   * @param shortcircuiter The Shortcircuiter to tell us when to shortcircuit the pass.
   * @see Shortcircuiter
   */
  public static <R> void execOnAllV2ExprsShortcircuitably(
      SoyNode node, AbstractExprNodeVisitor<R> exprNodeVisitor, Shortcircuiter<R> shortcircuiter) {

    (new VisitAllV2ExprsVisitor<R>(exprNodeVisitor, shortcircuiter)).exec(node);
  }


  /**
   * Helper interface for {@code visitAllExprsShortcircuitably}.
   *
   * @param <R> The ExprNode visitor's return type.
   */
  public static interface Shortcircuiter<R> {

    /**
     * Called at various points during a pass initiated by visitAllExprsShortcircuitably.
     * This method should return whether or not to shortcircuit the pass (at the current point in
     * the pass).
     *
     * @param exprNodeVisitor The expression visitor being used by visitAllExprsShortcircuitably.
     * @return Whether to shortcircuit the pass (at the current point in the pass).
     */
    public boolean shouldShortcircuit(AbstractExprNodeVisitor<R> exprNodeVisitor);
  }


  /**
   * Private helper class for {@code visitAllExprs} and {@code visitAllExprsShortcircuitably}.
   *
   * @param <R> The ExprNode visitor's return type.
   */
  private static class VisitAllV2ExprsVisitor<R> extends AbstractSoyNodeVisitor<R> {

    private final AbstractExprNodeVisitor<R> exprNodeVisitor;

    private final Shortcircuiter<R> shortcircuiter;

    public VisitAllV2ExprsVisitor(
        AbstractExprNodeVisitor<R> exprNodeVisitor, @Nullable Shortcircuiter<R> shortcircuiter) {
      this.exprNodeVisitor = exprNodeVisitor;
      this.shortcircuiter = shortcircuiter;
    }

    @Override protected void visitSoyNode(SoyNode node) {

      if (node instanceof ParentSoyNode<?>) {
        for (SoyNode child : ((ParentSoyNode<?>) node).getChildren()) {
          visit(child);
          if (shortcircuiter != null && shortcircuiter.shouldShortcircuit(exprNodeVisitor)) {
            return;
          }
        }
      }

      if (node instanceof ExprHolderNode) {
        ExprHolderNode nodeAsExprHolder = (ExprHolderNode) node;

        for (ExprUnion exprUnion : nodeAsExprHolder.getAllExprUnions()) {
          if (exprUnion.getExpr() == null) {
            continue;
          }

          try {
            exprNodeVisitor.exec(exprUnion.getExpr());
          } catch (SoySyntaxException sse) {
            associateMetaInfoWithException(nodeAsExprHolder, sse);
            throw sse;
          }
        }
      }
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Utils for cloning.


  /**
   * Clones the given node and then generates and sets new ids on all the cloned nodes (by default,
   * SoyNode.clone() creates cloned nodes with the same ids as the original nodes).
   *
   * <p> This function will use the original Soy tree's node id generator to generate the new node
   * ids for the cloned nodes. Thus, the original node to be cloned must be part of a full Soy tree.
   * However, this does not mean that the cloned node will become part of the original tree (unless
   * it is manually attached later). The cloned node will be an independent subtree with parent set
   * to null.
   *
   * @param <T> The type of the node being cloned.
   * @param origNode The original node to be cloned. This node must be part of a full Soy tree,
   *     because the generator for the new node ids will be retrieved from the root (SoyFileSetNode)
   *     of the tree.
   * @return The cloned node, with all new ids for its subtree.
   */
  public static <T extends SoyNode> T cloneWithNewIds(T origNode) {

    // Clone the node.
    @SuppressWarnings("unchecked")
    T clone = (T) origNode.clone();

    // Generate new ids.
    IdGenerator nodeIdGen = origNode.getNearestAncestor(SoyFileSetNode.class).getNodeIdGenerator();
    (new GenNewIdsVisitor(nodeIdGen)).exec(clone);

    return clone;
  }


  /**
   * Private helper for cloneWithNewIds() to set new ids on a cloned subtree.
   */
  private static class GenNewIdsVisitor extends AbstractSoyNodeVisitor<Void> {

    /** The generator for new node ids. */
    private IdGenerator nodeIdGen;

    /**
     * @param nodeIdGen The generator for new node ids.
     */
    public GenNewIdsVisitor(IdGenerator nodeIdGen) {
      this.nodeIdGen = nodeIdGen;
    }

    @Override protected void visitSoyNode(SoyNode node) {
      node.setId(nodeIdGen.genId());
      if (node instanceof ParentSoyNode<?>) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }
  }

}
