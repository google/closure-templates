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

import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.SoyJsSrcOptions.CodeStyle;
import com.google.template.soy.shared.internal.ApiCallScope;
import com.google.template.soy.soytree.AbstractReturningSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.jssrc.GoogMsgNode;
import com.google.template.soy.soytree.jssrc.GoogMsgRefNode;

import java.util.Map;


/**
 * Visitor to determine whether the output string for the subtree rooted at a given node is
 * computable as the concatenation of one or more JS expressions. If this is false, it means the
 * generated code for computing the node's output must include one or more full JS statements.
 *
 * <p> Precondition: MsgNode should not exist in the tree.
 *
 * <p> Important: This class is in {@link ApiCallScope} because it memoizes results that are
 * reusable for the same parse tree. If we change the parse tree between uses of the scoped
 * instance, then the results may not be correct. (In that case, we would need to take this class
 * out of {@code ApiCallScope} and rewrite the code somehow to still take advantage of the
 * memoized results to the extent that they remain correct.)
 *
 * @author Kai Huang
 */
@ApiCallScope
class IsComputableAsJsExprsVisitor extends AbstractReturningSoyNodeVisitor<Boolean> {


  /** The options for generating JS source code. */
  private final SoyJsSrcOptions jsSrcOptions;

  /** The memoized results of past visits to nodes. */
  private final Map<SoyNode, Boolean> memoizedResults;


  /**
   * @param jsSrcOptions The options for generating JS source code.
   */
  @Inject
  IsComputableAsJsExprsVisitor(SoyJsSrcOptions jsSrcOptions) {
    this.jsSrcOptions = jsSrcOptions;
    memoizedResults = Maps.newHashMap();
  }


  @Override protected Boolean visit(SoyNode node) {

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


  @Override protected Boolean visitTemplateNode(TemplateNode node) {
    return areChildrenComputableAsJsExprs(node);
  }


  @Override protected Boolean visitRawTextNode(RawTextNode node) {
    return true;
  }


  @Override protected Boolean visitGoogMsgNode(GoogMsgNode node) {
    return false;
  }


  @Override protected Boolean visitMsgPlaceholderNode(MsgPlaceholderNode node) {
    return areChildrenComputableAsJsExprs(node);
  }


  @Override protected Boolean visitGoogMsgRefNode(GoogMsgRefNode node) {
    return true;
  }


  @Override protected Boolean visitMsgHtmlTagNode(MsgHtmlTagNode node) {
    return areChildrenComputableAsJsExprs(node);
  }


  @Override protected Boolean visitPrintNode(PrintNode node) {
    return true;
  }


  @Override protected Boolean visitCssNode(CssNode node) {
    return true;
  }


  @Override protected Boolean visitLetNode(LetNode node) {
    return false;
  }


  @Override protected Boolean visitIfNode(IfNode node) {
    // If all children are computable as JS expressions, then this 'if' statement can be written
    // as an expression as well, using the ternary conditional operator ("? :").
    return areChildrenComputableAsJsExprs(node);
  }


  @Override protected Boolean visitIfCondNode(IfCondNode node) {
    return areChildrenComputableAsJsExprs(node);
  }


  @Override protected Boolean visitIfElseNode(IfElseNode node) {
    return areChildrenComputableAsJsExprs(node);
  }


  @Override protected Boolean visitSwitchNode(SwitchNode node) {
    return false;
  }


  @Override protected Boolean visitForeachNode(ForeachNode node) {
    return false;
  }


  @Override protected Boolean visitForNode(ForNode node) {
    return false;
  }


  @Override protected Boolean visitCallNode(CallNode node) {
    return jsSrcOptions.getCodeStyle() == CodeStyle.CONCAT && areChildrenComputableAsJsExprs(node);
  }


  @Override protected Boolean visitCallParamValueNode(CallParamValueNode node) {
    return true;
  }


  @Override protected Boolean visitCallParamContentNode(CallParamContentNode node) {
    return areChildrenComputableAsJsExprs(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Private helpers.


  /**
   * Private helper to check whether all children of a given parent node satisfy
   * IsComputableAsJsExprsVisitor.
   * @param node The parent node whose children to check.
   * @return True if all children satisfy IsComputableAsJsExprsVisitor. 
   */
  private boolean areChildrenComputableAsJsExprs(ParentSoyNode<?> node) {

    for (SoyNode child : node.getChildren()) {
      // Note: Save time by not visiting RawTextNode and PrintNode children.
      if (! (child instanceof RawTextNode) && ! (child instanceof PrintNode)) {
        if (! visit(child)) {
          return false;
        }
      }
    }

    return true;
  }

}
