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

package com.google.template.soy.jssrc.internal;

import com.google.template.soy.soytree.AbstractReturningSoyNodeVisitor;
import com.google.template.soy.soytree.AssignmentNode;
import com.google.template.soy.soytree.AutoImplNode;
import com.google.template.soy.soytree.BreakNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.ContinueNode;
import com.google.template.soy.soytree.DebuggerNode;
import com.google.template.soy.soytree.EvalNode;
import com.google.template.soy.soytree.ExternNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.JavaImplNode;
import com.google.template.soy.soytree.JsImplNode;
import com.google.template.soy.soytree.KeyNode;
import com.google.template.soy.soytree.LetNode;
import com.google.template.soy.soytree.LogNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.ReturnNode;
import com.google.template.soy.soytree.SkipNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.VeLogNode;
import com.google.template.soy.soytree.WhileNode;
import java.util.HashMap;
import java.util.Map;

/**
 * Visitor to determine whether the output string for the subtree rooted at a given node is
 * computable as the concatenation of one or more JS expressions. If this is false, it means the
 * generated code for computing the node's output must include one or more full JS statements.
 *
 * <p>Precondition: MsgNode should not exist in the tree.
 *
 * <p>TODO(user): This should no longer be necessary after CodeChunk migration. Rip it all out.
 */
public class IsComputableAsJsExprsVisitor extends AbstractReturningSoyNodeVisitor<Boolean> {

  /** The memoized results of past visits to nodes. */
  private final Map<SoyNode, Boolean> memoizedResults;

  protected IsComputableAsJsExprsVisitor() {
    memoizedResults = new HashMap<>();
  }

  /**
   * Executes this visitor on the children of the given node, and returns true if all children are
   * computable as JsExprs. Ignores whether the given node itself is computable as JsExprs or not.
   */
  public Boolean execOnChildren(ParentSoyNode<?> node) {
    return areChildrenComputableAsJsExprs(node);
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
  protected Boolean visitTemplateNode(TemplateNode node) {
    return areChildrenComputableAsJsExprs(node);
  }

  @Override
  protected Boolean visitRawTextNode(RawTextNode node) {
    return true;
  }

  @Override
  protected Boolean visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    return false;
  }

  @Override
  protected Boolean visitMsgPlaceholderNode(MsgPlaceholderNode node) {
    return areChildrenComputableAsJsExprs(node);
  }

  @Override
  protected Boolean visitMsgHtmlTagNode(MsgHtmlTagNode node) {
    return areChildrenComputableAsJsExprs(node);
  }

  @Override
  protected Boolean visitPrintNode(PrintNode node) {
    return true;
  }

  @Override
  protected Boolean visitLetNode(LetNode node) {
    return false;
  }

  @Override
  protected Boolean visitIfNode(IfNode node) {
    // If all children are computable as JS expressions, then this 'if' statement can be written
    // as an expression as well, using the ternary conditional operator ("? :").
    return areChildrenComputableAsJsExprs(node);
  }

  @Override
  protected Boolean visitIfCondNode(IfCondNode node) {
    return areChildrenComputableAsJsExprs(node);
  }

  @Override
  protected Boolean visitIfElseNode(IfElseNode node) {
    return areChildrenComputableAsJsExprs(node);
  }

  @Override
  protected Boolean visitSwitchNode(SwitchNode node) {
    return false;
  }

  @Override
  protected Boolean visitSwitchCaseNode(SwitchCaseNode node) {
    return false;
  }

  @Override
  protected Boolean visitSwitchDefaultNode(SwitchDefaultNode node) {
    return false;
  }

  @Override
  protected Boolean visitForNode(ForNode node) {
    return false;
  }

  @Override
  protected Boolean visitCallNode(CallNode node) {
    if (node.isErrorFallbackSkip()) {
      return false;
    }
    return areChildrenComputableAsJsExprs(node);
  }

  @Override
  protected Boolean visitCallParamValueNode(CallParamValueNode node) {
    return true;
  }

  @Override
  protected Boolean visitCallParamContentNode(CallParamContentNode node) {
    return areChildrenComputableAsJsExprs(node);
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
  protected Boolean visitHtmlAttributeValueNode(HtmlAttributeValueNode node) {
    return areChildrenComputableAsJsExprs(node);
  }

  @Override
  protected Boolean visitHtmlAttributeNode(HtmlAttributeNode node) {
    return false;
  }

  @Override
  protected Boolean visitHtmlOpenTagNode(HtmlOpenTagNode node) {
    return false;
  }

  @Override
  protected Boolean visitHtmlCloseTagNode(HtmlCloseTagNode node) {
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

  @Override
  protected Boolean visitSkipNode(SkipNode node) {
    return false;
  }

  @Override
  protected Boolean visitExternNode(ExternNode node) {
    return false;
  }

  @Override
  protected Boolean visitJsImplNode(JsImplNode node) {
    return false;
  }

  @Override
  protected Boolean visitJavaImplNode(JavaImplNode node) {
    return false;
  }

  @Override
  protected Boolean visitAutoImplNode(AutoImplNode node) {
    return false;
  }

  @Override
  protected Boolean visitAssignmentNode(AssignmentNode node) {
    return false;
  }

  @Override
  protected Boolean visitReturnNode(ReturnNode node) {
    return false;
  }

  @Override
  protected Boolean visitBreakNode(BreakNode node) {
    return false;
  }

  @Override
  protected Boolean visitContinueNode(ContinueNode node) {
    return false;
  }

  @Override
  protected Boolean visitWhileNode(WhileNode node) {
    return false;
  }

  @Override
  protected Boolean visitEvalNode(EvalNode node) {
    return false;
  }

  // -----------------------------------------------------------------------------------------------
  // Private helpers.

  /**
   * Private helper to check whether all children of a given parent node satisfy
   * IsComputableAsJsExprsVisitor.
   *
   * @param node The parent node whose children to check.
   * @return True if all children satisfy IsComputableAsJsExprsVisitor.
   */
  private boolean areChildrenComputableAsJsExprs(ParentSoyNode<?> node) {

    for (SoyNode child : node.getChildren()) {
      if (canSkipChild(child)) {
        continue;
      }
      if (!visit(child)) {
        return false;
      }
    }

    return true;
  }

  /** @return True if there is no point in visiting the child node, since it's always computable. */
  protected boolean canSkipChild(SoyNode child) {
    // TODO(user): This check is probably not worth doing.  Remove.
    return child instanceof RawTextNode || child instanceof PrintNode;
  }
}
