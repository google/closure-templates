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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;

import java.util.List;

import javax.annotation.Nullable;


/**
 * Shared utilities for the 'soytree' package.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class SoytreeUtils {


  private SoytreeUtils() {}


  /**
   * Retrieves all nodes in a tree that are an instance of a particular class.
   *
   * @param <T> The type of node to retrieve.
   * @param rootSoyNode The parse tree to search.
   * @param classObject The class whose instances to search for, including subclasses.
   * @return The nodes in the order they appear.
   */
  public static <T extends SoyNode> List<T> getAllNodesOfType(
      SoyNode rootSoyNode, final Class<T> classObject) {
    return getAllNodesOfType(rootSoyNode, classObject, true);
  }


  /**
   * Retrieves all nodes in a tree that are an instance of a particular class.
   *
   * @param <T> The type of node to retrieve.
   * @param rootSoyNode The parse tree to search.
   * @param classObject The class whose instances to search for, including subclasses.
   * @param doSearchSubtreesOfMatchedNodes Whether to keep searching subtrees of matched nodes for
   *     more nodes of the given type.
   * @return The nodes in the order they appear.
   */
  public static <T extends SoyNode> List<T> getAllNodesOfType(
      SoyNode rootSoyNode, final Class<T> classObject,
      final boolean doSearchSubtreesOfMatchedNodes) {

    final ImmutableList.Builder<T> matchedNodesBuilder = ImmutableList.builder();

    AbstractSoyNodeVisitor<Void> visitor = new AbstractSoyNodeVisitor<Void>() {
      @SuppressWarnings("unchecked") // Casting safe after isAssignableFrom check.
      @Override public void visitSoyNode(SoyNode soyNode) {
        if (classObject.isAssignableFrom(soyNode.getClass())) {
          matchedNodesBuilder.add((T) soyNode);
          if (! doSearchSubtreesOfMatchedNodes) {
            return;
          }
        }
        if (soyNode instanceof ParentSoyNode<?>) {
          visitChildren((ParentSoyNode<?>) soyNode);
        }
      }
    };

    visitor.exec(rootSoyNode);
    return matchedNodesBuilder.build();
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
        for (ExprUnion exprUnion : ((ExprHolderNode) node).getAllExprUnions()) {
          if (exprUnion.getExpr() == null) {
            continue;
          }

          try {
            exprNodeVisitor.exec(exprUnion.getExpr());
          } catch (SoySyntaxException sse) {
            throw SoySyntaxExceptionUtils.associateNode(sse, node);
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
   * @param nodeIdGen The ID generator used for the tree.
   * @return The cloned node, with all new ids for its subtree.
   */
  public static <T extends SoyNode> T cloneWithNewIds(T origNode, IdGenerator nodeIdGen) {

    // Clone the node.
    @SuppressWarnings("unchecked")
    T clone = (T) origNode.clone();

    // Generate new ids.
    (new GenNewIdsVisitor(nodeIdGen)).exec(clone);

    return clone;
  }


  /**
   * Clones the given list of nodes and then generates and sets new ids on all the cloned nodes (by
   * default, SoyNode.clone() creates cloned nodes with the same ids as the original nodes).
   *
   * <p> This function will use the original Soy tree's node id generator to generate the new node
   * ids for the cloned nodes. Thus, the original nodes to be cloned must be part of a full Soy
   * tree. However, this does not mean that the cloned nodes will become part of the original tree
   * (unless they are manually attached later). The cloned nodes will be independent subtrees with
   * parents set to null.
   *
   * @param <T> The type of the nodes being cloned.
   * @param origNodes The original nodes to be cloned. These nodes must be part of a full Soy tree,
   *     because the generator for the new node ids will be retrieved from the root (SoyFileSetNode)
   *     of the tree.
   * @param nodeIdGen The ID generator used for the tree.
   * @return The cloned nodes, with all new ids for their subtrees.
   */
  public static <T extends SoyNode> List<T> cloneListWithNewIds(
      List<T> origNodes, IdGenerator nodeIdGen) {

    Preconditions.checkNotNull(origNodes);
    if (origNodes.size() == 0) {
      return Lists.newArrayListWithCapacity(0);
    }

    List<T> clones = Lists.newArrayListWithCapacity(origNodes.size());
    for (T origNode : origNodes) {
      @SuppressWarnings("unchecked")
      T clone = (T) origNode.clone();
      (new GenNewIdsVisitor(nodeIdGen)).exec(clone);
      clones.add(clone);
    }

    return clones;
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
