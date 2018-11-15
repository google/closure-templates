/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.pysrc.internal;

import com.google.template.soy.soytree.AbstractReturningSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.DebuggerNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.KeyNode;
import com.google.template.soy.soytree.LetNode;
import com.google.template.soy.soytree.LogNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.VeLogNode;
import java.util.HashMap;
import java.util.Map;

/**
 * Visitor to determine whether the output string for the subtree rooted at a given node is
 * computable as the concatenation of one or more Python expressions. If this is false, it means the
 * generated code for computing the node's output must include one or more full Python statements.
 *
 */
class IsComputableAsPyExprVisitor extends AbstractReturningSoyNodeVisitor<Boolean> {

  /** The memoized results of past visits to nodes. */
  private final Map<SoyNode, Boolean> memoizedResults;

  IsComputableAsPyExprVisitor() {
    memoizedResults = new HashMap<>();
  }

  /**
   * Executes this visitor on the children of the given node, and returns true if all children are
   * computable as PyExprs. Ignores whether the given node itself is computable as PyExprs or not.
   */
  public Boolean execOnChildren(ParentSoyNode<?> node) {
    return areChildrenComputableAsPyExprs(node);
  }

  @Override
  protected Boolean visit(SoyNode node) {
    if (memoizedResults.containsKey(node)) {
      return memoizedResults.get(node);
    } else {
      Boolean result = super.visit(node);
      memoizedResults.put(node, result);
      return result;
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  @Override
  protected Boolean visitRawTextNode(RawTextNode node) {
    return true;
  }

  @Override
  protected Boolean visitPrintNode(PrintNode node) {
    return true;
  }

  @Override
  protected Boolean visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    return true;
  }

  @Override
  protected Boolean visitMsgNode(MsgNode node) {
    return true;
  }

  @Override
  protected Boolean visitLetNode(LetNode node) {
    return false;
  }


  @Override
  protected Boolean visitIfNode(IfNode node) {
    // If all children are computable as Python expressions, then this 'if' statement can be written
    // as an expression as well, using the ternary conditional operator ("'a' if x else 'b'").
    return areChildrenComputableAsPyExprs(node);
  }

  @Override
  protected Boolean visitIfCondNode(IfCondNode node) {
    return areChildrenComputableAsPyExprs(node);
  }

  @Override
  protected Boolean visitIfElseNode(IfElseNode node) {
    return areChildrenComputableAsPyExprs(node);
  }

  @Override
  protected Boolean visitSwitchNode(SwitchNode node) {
    return false;
  }

  @Override
  protected Boolean visitForNode(ForNode node) {
    // TODO(dcphillips): Consider using list comprehensions to generate the output of a foreach.
    return false;
  }

  @Override
  protected Boolean visitCallNode(CallNode node) {
    return areChildrenComputableAsPyExprs(node);
  }

  @Override
  protected Boolean visitCallParamValueNode(CallParamValueNode node) {
    return true;
  }

  @Override
  protected Boolean visitCallParamContentNode(CallParamContentNode node) {
    return areChildrenComputableAsPyExprs(node);
  }

  @Override
  protected Boolean visitLogNode(LogNode node) {
    return false;
  }

  @Override
  protected Boolean visitDebuggerNode(DebuggerNode node) {
    return false;
  }

  @Override
  protected Boolean visitKeyNode(KeyNode node) {
    return false;
  }

  @Override
  protected Boolean visitVeLogNode(VeLogNode node) {
    return false;
  }

  // -----------------------------------------------------------------------------------------------
  // Private helpers.

  /**
   * Private helper to check whether all SoyNode children of a given parent node satisfy
   * IsComputableAsPyExprVisitor. ExprNode children are assumed to be computable as PyExprs.
   *
   * @param node The parent node whose children to check.
   * @return True if all children satisfy IsComputableAsPyExprVisitor.
   */
  private boolean areChildrenComputableAsPyExprs(ParentSoyNode<?> node) {

    for (SoyNode child : node.getChildren()) {
      // Note: Save time by not visiting RawTextNode and PrintNode children.
      if (!(child instanceof RawTextNode) && !(child instanceof PrintNode)) {
        if (!visit(child)) {
          return false;
        }
      }
    }

    return true;
  }
}
