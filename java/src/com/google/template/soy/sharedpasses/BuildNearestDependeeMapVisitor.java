/*
 * Copyright 2009 Google Inc.
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
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.ExprUnion;
import com.google.template.soy.soytree.LetNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ConditionalBlockNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.LocalVarBlockNode;
import com.google.template.soy.soytree.SoyNode.LocalVarNode;
import com.google.template.soy.soytree.SoyNode.LoopNode;
import com.google.template.soy.soytree.SoyNode.MsgBlockNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.jssrc.GoogMsgNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;


/**
 * Visitor for building a map from each node to its nearest dependee. The map built by this visitor
 * is useful in optimization passes that move nodes within the tree. In the context of this pass,
 * a dependee is a node that appears earlier in the depth-first traversal, and must remain in the
 * same traversal relationship with the depender after any modifications to the tree, in order to
 * preserve correctness.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> {@link #exec} must be called on a {@code TemplateNode} or an ancestor of a
 * {@code TemplateNode}.
 *
 * <p> More specifically, a dependee may be an ancestor of the depender or an older sibling of
 * either the depender or an ancestor of the depender. In the case of a dependee that is an ancestor
 * of the depender, the depender must not be moved outside of the subtree of the dependee. In the
 * case of a dependee that is an older sibling of either the depender or an ancestor of the
 * depender, this same relationship must be maintained when the depender is moved within the tree.
 *
 * <p> Dependees are one of:
 * (a) TemplateNode: Its nodes must remain in its subtree.
 * (b) SplitLevelTopNode: Its immediate children (the bottom level) must remain under it.
 * (c) ConditionalBlockNode other than LoopNode: It may never be executed, and we can't guarantee
 *     that any of the references made in a node's subtree will be defined in the case that the
 *     block is not executed, and furthermore, even if the node's subtree has no references, we
 *     don't want to needlessly process the node's subtree when the block is not executed. We make
 *     an exception for LoopNodes because we don't want to lose the ability to pull invariants out
 *     of loops.
 * (d) LocalVarNode: Its subtree or younger siblings may reference its local variable.
 * (e) MsgBlockNode: Any change to its immediate children would change the message to be translated,
 *     which would be incorrect.
 *
 * @author Kai Huang
 */
public class BuildNearestDependeeMapVisitor extends AbstractSoyNodeVisitor<Map<SoyNode, SoyNode>> {


  /** Stack of frames containing lists of nodes that may be dependees (for the current template). */
  private Deque<List<SoyNode>> potentialDependeeFrames;

  /** Map from each node to the set of all its dependees (for the current template). */
  private Map<SoyNode, Set<SoyNode>> allDependeesMap;

  /** Map from each node to its nearest dependee (the final result of this pass). */
  private Map<SoyNode, SoyNode> nearestDependeeMap;


  @Override public Map<SoyNode, SoyNode> exec(SoyNode node) {

    Preconditions.checkArgument(
        node instanceof SoyFileSetNode || node instanceof SoyFileNode ||
        node instanceof TemplateNode);

    nearestDependeeMap = Maps.newHashMap();
    visit(node);
    return nearestDependeeMap;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitSoyFileSetNode(SoyFileSetNode node) {
    visitChildren(node);
  }


  @Override protected void visitSoyFileNode(SoyFileNode node) {
    visitChildren(node);
  }


  @Override protected void visitTemplateNode(TemplateNode node) {

    potentialDependeeFrames = new ArrayDeque<List<SoyNode>>();
    allDependeesMap = Maps.newHashMap();

    // Note: Add to potential dependees while visiting children because descendents can't be moved
    // out of the template.
    potentialDependeeFrames.push(Lists.<SoyNode>newArrayList(node));
    visitChildren(node);
    potentialDependeeFrames.pop();
  }


  @Override protected void visitGoogMsgNode(GoogMsgNode node) {

    visitSoyNode(node);

    // Note: Add to potential dependees before visiting younger siblings because it defines an
    // inline local variable.
    potentialDependeeFrames.peek().add(node);
  }


  @Override protected void visitLetNode(LetNode node) {

    visitSoyNode(node);

    // Note: Add to potential dependees before visiting younger siblings because it defines an
    // inline local variable.
    potentialDependeeFrames.peek().add(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {

    // ------ Recurse. ------
    // Note: We must recurse first, because a parent's children must have already been processed
    // (thus their mappings are already in allDependeesMap) when the parent is processed.

    if (node instanceof ParentSoyNode<?>) {

      List<SoyNode> newPotentialDependeeFrame = Lists.newArrayList();

      if (node instanceof TemplateNode || node instanceof SplitLevelTopNode<?> ||
          node instanceof ConditionalBlockNode || node instanceof LocalVarBlockNode ||
          node instanceof MsgBlockNode) {
        // This node is a potential dependee for descendants.
        newPotentialDependeeFrame.add(node);
      }

      potentialDependeeFrames.push(newPotentialDependeeFrame);
      visitChildren((ParentSoyNode<?>) node);
      potentialDependeeFrames.pop();
    }

    // ------ Process this node. ------

    // Find the top-level refs within expressions in this node, if any.
    Set<String> topLevelRefs;
    if (node instanceof ExprHolderNode) {
      topLevelRefs = Sets.newHashSet();
      for (ExprUnion exprUnion : ((ExprHolderNode) node).getAllExprUnions()) {
        topLevelRefs.addAll(getTopLevelRefsInExpr(exprUnion));
      }
    } else {
      topLevelRefs = null;
    }

    // Add mappings for this node to nearestDependeeMap and allDependeesMap.
    boolean foundNearestDependee = false;
    Set<SoyNode> allDependees = Sets.newHashSetWithExpectedSize(4);

    for (List<SoyNode> potentialDependeeFrame : potentialDependeeFrames) {
      // We must iterate in reverse if we wish to encounter the nearest dependee first.
      for (int i = potentialDependeeFrame.size() - 1; i >= 0; i--) {
        SoyNode potentialDependee = potentialDependeeFrame.get(i);

        if (isDependent(potentialDependee, node, topLevelRefs)) {
          if (!foundNearestDependee) {
            nearestDependeeMap.put(node, potentialDependee);
            foundNearestDependee = true;
          }
          allDependees.add(potentialDependee);
        }
      }
    }

    if (!foundNearestDependee) {
      throw new AssertionError();
    }

    allDependeesMap.put(node, allDependees);
  }


  // -----------------------------------------------------------------------------------------------
  // Helpers.


  /**
   * Helper for visitSoyNode() to determine whether a given node is dependent on a given potential
   * dependee.
   *
   * @param potentialDependee The potential dependee node.
   * @param node The node to check to see if it's a depender of the potential dependee.
   * @param topLevelRefs The top-level references made by this node. Does not include references
   *     made by its descendents. May be either empty or null if there are no references.
   * @return True if {@code node} is dependent on {@code potentialDependee}.
   */
  private boolean isDependent(
      SoyNode potentialDependee, SoyNode node, @Nullable Set<String> topLevelRefs) {

    if (potentialDependee instanceof TemplateNode ||
        (potentialDependee instanceof ConditionalBlockNode &&
         !(potentialDependee instanceof LoopNode))) {
      // A node can never be moved outside of its template, nor outside of any conditionally
      // executed block that contains it (we make an exception for loops).
      return true;
    }

    if (node.getParent() == potentialDependee &&
        (potentialDependee instanceof SplitLevelTopNode<?> ||
         potentialDependee instanceof MsgBlockNode)) {
      // The bottom level of a split-level structure cannot be moved. Also, the immediate children
      // of a MsgBlockNode cannot be moved because they define the message for translation.
      return true;
    }

    if (potentialDependee instanceof LocalVarNode) {
      // Check whether this node depends on the local var.
      if (topLevelRefs != null &&
          topLevelRefs.contains(((LocalVarNode) potentialDependee).getVarName())) {
        return true;
      }
      // Check whether any child depends on the local var.
      if (node instanceof ParentSoyNode<?>) {
        for (SoyNode child : ((ParentSoyNode<?>) node).getChildren()) {
          Set<SoyNode> allDependeesOfChild = allDependeesMap.get(child);
          if (allDependeesOfChild == null) {
            throw new AssertionError("Child has not been visited.");
          }
          if (allDependeesOfChild.contains(potentialDependee)) {
            return true;
          }
        }
      }
    }

    // If got past all the checks, then this node is not dependent on this potential dependee.
    return false;
  }


  // -----------------------------------------------------------------------------------------------
  // Helper to retrieve top-level references from a Soy expression.


  /**
   * Finds the top-level references within a Soy expression (V1 or V2 syntax).
   *
   * @param exprUnion The expression (V1 or V2 syntax).
   * @return The set of top-level references in the given expression.
   */
  private static Set<String> getTopLevelRefsInExpr(ExprUnion exprUnion) {

    if (exprUnion.getExpr() != null) {
      // V2 expression.
      return (new GetTopLevelRefsInExprVisitor()).exec(exprUnion.getExpr());
    } else {
      // V1 expression.
      return getTopLevelRefsInV1Expr(exprUnion.getExprText());
    }
  }


  /**
   * Helper for getTopLevelRefsInExpr() to get top-level references from a Soy V2 expression.
   * Returns the set of top-level references in the given expression.
   */
  private static class GetTopLevelRefsInExprVisitor extends AbstractExprNodeVisitor<Set<String>> {

    private Set<String> topLevelRefs;

    @Override public Set<String> exec(ExprNode node) {
      topLevelRefs = Sets.newHashSet();
      visit(node);
      return topLevelRefs;
    }

    @Override protected void visitDataRefNode(DataRefNode node) {
      topLevelRefs.add(node.getFirstKey());
      visitChildren(node);
    }

    @Override protected void visitExprNode(ExprNode node) {
      if (node instanceof ParentExprNode) {
        visitChildren((ParentExprNode) node);
      }
    }
  }


  /** Regex for a top-level reference in a Soy V1 expression. Used by getTopLevelRefsInV1Expr(). */
  private static final Pattern TOP_LEVEL_REF = Pattern.compile("\\$([a-zA-Z0-9_]+)");


  /**
   * Helper for getTopLevelRefsInExpr() to get top-level references from a Soy V1 expression.
   * @param exprText The text of the expression.
   * @return The set of top-level references in the expression.
   */
  private static Set<String> getTopLevelRefsInV1Expr(String exprText) {

    Set<String> topLevelRefs = Sets.newHashSet();
    Matcher matcher = TOP_LEVEL_REF.matcher(exprText);
    while (matcher.find()) {
      topLevelRefs.add(matcher.group(1));
    }
    return topLevelRefs;
  }

}
