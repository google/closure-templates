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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.DataRefKeyNode;
import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachIfemptyNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ConditionalBlockNode;
import com.google.template.soy.soytree.SoyNode.LocalVarNode;
import com.google.template.soy.soytree.SoyNode.LoopNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
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
 * (e) MsgNode or GoogMsgNode: Any change to its immediate children would change the message to be
 *     translated, which would be incorrect.
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


  @Override protected void setup() {
    potentialDependeeFrames = null;
    allDependeesMap = null;
    nearestDependeeMap = Maps.newHashMap();
  }


  @Override protected Map<SoyNode, SoyNode> getResult() {
    return nearestDependeeMap;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete classes.


  @Override protected void visitInternal(SoyFileSetNode node) {
    visitChildren(node);
  }


  @Override protected void visitInternal(SoyFileNode node) {
    visitChildren(node);
  }


  @Override protected void visitInternal(TemplateNode node) {

    potentialDependeeFrames = new ArrayDeque<List<SoyNode>>();
    allDependeesMap = Maps.newHashMap();

    // Note: Add to potential dependees because descendents can't be moved out of the template.
    potentialDependeeFrames.push(Lists.<SoyNode>newArrayList(node));
    visitChildren(node);
    potentialDependeeFrames.pop();
  }


  @Override protected void visitInternal(MsgNode node) {
    // Note: Add to potential dependees because its children define the message for translation.
    visitParentPotentialDependeeHelper(node, null);
  }


  @Override protected void visitInternal(GoogMsgNode node) {

    // Note: Add to potential dependees because its children define the message for translation.
    visitParentPotentialDependeeHelper(node, null);

    // Note: Add to potential dependees because it defines an inline local variable.
    potentialDependeeFrames.peek().add(node);
  }


  @Override protected void visitInternal(PrintNode node) {
    Set<String> topLevelRefs = getTopLevelRefsInExpr(node.getExpr(), node.getExprText());
    // Note: Add to potential dependees because it is the top of a split-level structure.
    visitParentPotentialDependeeHelper(node, topLevelRefs);
  }


  @Override protected void visitInternal(PrintDirectiveNode node) {

    Set<String> topLevelRefs = Sets.newHashSet();
    for (ExprNode arg : node.getArgs()) {
      topLevelRefs.addAll(getTopLevelRefsInExpr(arg, null));
    }

    visitHelper(node, topLevelRefs);
  }


  @Override protected void visitInternal(IfNode node) {
    // Note: Add to potential dependees because it is the top of a split-level structure.
    visitParentPotentialDependeeHelper(node, null);
  }


  @Override protected void visitInternal(IfCondNode node) {
    Set<String> topLevelRefs = getTopLevelRefsInExpr(node.getExpr(), node.getExprText());
    // Note: Add to potential dependees because it is a conditionally executed block.
    visitParentPotentialDependeeHelper(node, topLevelRefs);
  }


  @Override protected void visitInternal(IfElseNode node) {
    // Note: Add to potential dependees because it is a conditionally executed block.
    visitParentPotentialDependeeHelper(node, null);
  }


  @Override protected void visitInternal(SwitchNode node) {
    Set<String> topLevelRefs = getTopLevelRefsInExpr(node.getExpr(), node.getExprText());
    // Note: Add to potential dependees because it is the top of a split-level structure.
    visitParentPotentialDependeeHelper(node, topLevelRefs);
  }


  @Override protected void visitInternal(SwitchCaseNode node) {

    Set<String> topLevelRefs = Sets.newHashSet();
    for (ExprNode caseExpr : node.getExprList()) {
      topLevelRefs.addAll(getTopLevelRefsInExpr(caseExpr, null));
    }

    // Note: Add to potential dependees because it is a conditionally executed block.
    visitParentPotentialDependeeHelper(node, topLevelRefs);
  }


  @Override protected void visitInternal(SwitchDefaultNode node) {
    // Note: Add to potential dependees because it is a conditionally executed block.
    visitParentPotentialDependeeHelper(node, null);
  }


  @Override protected void visitInternal(ForeachNode node) {
    Set<String> topLevelRefs = getTopLevelRefsInExpr(node.getDataRef(), node.getDataRefText());
    // Note: Add to potential dependees because it is the top of a split-level structure.
    visitParentPotentialDependeeHelper(node, topLevelRefs);
  }


  @Override protected void visitInternal(ForeachNonemptyNode node) {
    // Note: Add to potential dependees because it defines a local variable.
    visitParentPotentialDependeeHelper(node, null);
  }


  @Override protected void visitInternal(ForeachIfemptyNode node) {
    // Note: Add to potential dependees because it is a conditionally executed block.
    visitParentPotentialDependeeHelper(node, null);
  }


  @Override protected void visitInternal(ForNode node) {

    Set<String> topLevelRefs = Sets.newHashSet();
    for (ExprNode rangeArg : node.getRangeArgs()) {
      topLevelRefs.addAll(getTopLevelRefsInExpr(rangeArg, null));
    }

    // Note: Add to potential dependees because it defines a local variable.
    visitParentPotentialDependeeHelper(node, topLevelRefs);
  }


  @Override protected void visitInternal(CallNode node) {
    // Note: Add to potential dependees because it is the top of a split-level structure.
    visitParentPotentialDependeeHelper(node, null);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for interfaces.


  @Override protected void visitInternal(SoyNode node) {
    visitHelper(node, null);
  }


  @Override protected void visitInternal(ParentSoyNode<? extends SoyNode> node) {

    potentialDependeeFrames.push(Lists.<SoyNode>newArrayList());
    visitChildren(node);
    potentialDependeeFrames.pop();

    visitHelper(node, null);
  }


  // -----------------------------------------------------------------------------------------------
  // Helpers.


  /**
   * Helper for visiting a given node and adding its mappings in nearestDependeeMap and
   * allDependeesMap.
   *
   * <p> Precondition: If this node is a parent, its children must have already been visited (thus
   * their mappings are already in allDependeesMap).
   *
   * @param node The node to visit.
   * @param topLevelRefs The top-level references made by this node. Does not include references
   *     made by its descendents. May be either empty or null if there are no references.
   */
  private void visitHelper(SoyNode node, @Nullable Set<String> topLevelRefs) {

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


  /**
   * Helper for visitHelper() to determine whether a given node is dependent on a given potential
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
        (potentialDependee instanceof SplitLevelTopNode ||
         potentialDependee instanceof MsgNode ||
         potentialDependee instanceof GoogMsgNode)) {
      // The bottom level of a split-level structure cannot be moved. Also, the immediate children
      // of a MsgNode or GoogMsgNode cannot be moved because they define the message for
      // translation.
      return true;
    }

    if (potentialDependee instanceof LocalVarNode) {
      // Check whether this node depends on the local var.
      if (topLevelRefs != null &&
          topLevelRefs.contains(((LocalVarNode) potentialDependee).getLocalVarName())) {
        return true;
      }
      // Check whether any child depends on the local var.
      if (node instanceof ParentSoyNode) {
        @SuppressWarnings("unchecked")
        ParentSoyNode<? extends SoyNode> nodeCast = (ParentSoyNode<? extends SoyNode>) node;
        for (SoyNode child : nodeCast.getChildren()) {
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


  /**
   * Helper for visiting a node that is both a potential dependee and a parent. This function takes
   * care of visiting the node's children.
   *
   * @param parentPotentialDependee The node to visit.
   * @param topLevelRefs The top-level references made by this node. Does not include references
   *     made by its descendents. May be either empty or null if there are no references.
   */
  private void visitParentPotentialDependeeHelper(
      ParentSoyNode<? extends SoyNode> parentPotentialDependee,
      @Nullable Set<String> topLevelRefs) {

    potentialDependeeFrames.push(Lists.<SoyNode>newArrayList(parentPotentialDependee));
    visitChildren(parentPotentialDependee);
    potentialDependeeFrames.pop();

    visitHelper(parentPotentialDependee, topLevelRefs);
  }


  // -----------------------------------------------------------------------------------------------
  // Helper to retrieve top-level references from a Soy expression.


  /**
   * Finds the top-level references within a Soy expression (V1 or V2 syntax).
   *
   * @param expr The expression tree, or null of the expression is not in Soy V2 syntax.
   * @param exprText The expression text. Required if the parameter {@code expr} is null, otherwise
   *     optional and will not be used.
   * @return The set of top-level references in the given expression.
   */
  private static Set<String> getTopLevelRefsInExpr(
      @Nullable ExprNode expr, @Nullable String exprText) {

    if (expr != null) {
      // V2 expression.
      return (new GetTopLevelRefsInExprVisitor()).exec(expr);
    } else {
      // V1 expression.
      return getTopLevelRefsInV1Expr(exprText);
    }
  }


  /**
   * Helper for getTopLevelRefsInExpr() to get top-level references from a Soy V2 expression.
   * Returns the set of top-level references in the given expression.
   */
  private static class GetTopLevelRefsInExprVisitor extends AbstractExprNodeVisitor<Set<String>> {

    private Set<String> topLevelRefs;

    @Override protected void setup() {
      topLevelRefs = Sets.newHashSet();
    }

    @Override protected Set<String> getResult() {
      return topLevelRefs;
    }

    @Override protected void visitInternal(DataRefNode node) {
      topLevelRefs.add(((DataRefKeyNode) node.getChild(0)).getKey());
      visitChildren(node);
    }

    @Override protected void visitInternal(ExprNode node) {
      // Nothing to do for other nodes.
    }

    @Override protected void visitInternal(ParentExprNode node) {
      visitChildren(node);
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
