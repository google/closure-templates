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

import com.google.template.soy.basetree.AbstractNodeVisitor;
import com.google.template.soy.basetree.ParentNode;
import com.google.template.soy.soytree.SoyNode.LoopNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.jssrc.GoogMsgNode;
import com.google.template.soy.soytree.jssrc.GoogMsgRefNode;


/**
 * Abstract base class for all SoyNode visitors. A visitor is basically a function implemented for
 * some or all SoyNodes, where the implementation can be different for each specific node class.
 *
 * <p> Same as {@link AbstractReturningSoyNodeVisitor} except that in this class, internal
 * {@code visit()} calls do not return a value.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p>
 * To create a visitor:
 * <ol>
 * <li> Subclass this class.
 * <li> Implement {@code visit*Node()} methods for some specific node types.
 * <li> Implement fallback methods for node types not specifically handled. The most general
 *      fallback method is {@link #visitSoyNode visitSoyNode()}, which is usually needed. Other
 *      fallback methods include {@code visitLoopNode()} and {@code visitCallParamNode()}.
 * <li> Maybe implement a constructor, taking appropriate parameters for your visitor call.
 * <li> Maybe implement {@link #exec exec()} if this visitor needs to return a non-null final result
 *      and/or if this visitor has state that needs to be setup/reset before each unrelated use of
 *      {@code visit()}.
 * </ol>
 *
 * @param <R> The return type of this visitor.
 *
 * @see AbstractReturningSoyNodeVisitor
 * @author Kai Huang
 */
public abstract class AbstractSoyNodeVisitor<R> extends AbstractNodeVisitor<SoyNode, R> {


  @Override protected void visit(SoyNode node) {

    switch (node.getKind()) {

      case SOY_FILE_SET_NODE: visitSoyFileSetNode((SoyFileSetNode) node); break;
      case SOY_FILE_NODE: visitSoyFileNode((SoyFileNode) node); break;

      case TEMPLATE_BASIC_NODE: visitTemplateBasicNode((TemplateBasicNode) node); break;
      case TEMPLATE_DELEGATE_NODE: visitTemplateDelegateNode((TemplateDelegateNode) node); break;

      case RAW_TEXT_NODE: visitRawTextNode((RawTextNode) node); break;

      case MSG_NODE: visitMsgNode((MsgNode) node); break;
      case MSG_PLACEHOLDER_NODE: visitMsgPlaceholderNode((MsgPlaceholderNode) node); break;
      case GOOG_MSG_NODE: visitGoogMsgNode((GoogMsgNode) node); break;
      case GOOG_MSG_REF_NODE: visitGoogMsgRefNode((GoogMsgRefNode) node); break;
      case MSG_PLURAL_NODE: visitMsgPluralNode((MsgPluralNode) node); break;
      case MSG_PLURAL_CASE_NODE: visitMsgPluralCaseNode((MsgPluralCaseNode) node); break;
      case MSG_PLURAL_DEFAULT_NODE: visitMsgPluralDefaultNode((MsgPluralDefaultNode) node); break;
      case MSG_PLURAL_REMAINDER_NODE:
        visitMsgPluralRemainderNode((MsgPluralRemainderNode) node); break;
      case MSG_SELECT_NODE: visitMsgSelectNode((MsgSelectNode) node); break;
      case MSG_SELECT_CASE_NODE: visitMsgSelectCaseNode((MsgSelectCaseNode) node); break;
      case MSG_SELECT_DEFAULT_NODE: visitMsgSelectDefaultNode((MsgSelectDefaultNode) node); break;
      case MSG_HTML_TAG_NODE: visitMsgHtmlTagNode((MsgHtmlTagNode) node); break;

      case PRINT_NODE: visitPrintNode((PrintNode) node); break;
      case PRINT_DIRECTIVE_NODE: visitPrintDirectiveNode((PrintDirectiveNode) node); break;

      case CSS_NODE: visitCssNode((CssNode) node); break;

      case LET_VALUE_NODE: visitLetValueNode((LetValueNode) node); break;
      case LET_CONTENT_NODE: visitLetContentNode((LetContentNode) node); break;

      case IF_NODE: visitIfNode((IfNode) node); break;
      case IF_COND_NODE: visitIfCondNode((IfCondNode) node); break;
      case IF_ELSE_NODE: visitIfElseNode((IfElseNode) node); break;

      case SWITCH_NODE: visitSwitchNode((SwitchNode) node); break;
      case SWITCH_CASE_NODE: visitSwitchCaseNode((SwitchCaseNode) node); break;
      case SWITCH_DEFAULT_NODE: visitSwitchDefaultNode((SwitchDefaultNode) node); break;

      case FOREACH_NODE: visitForeachNode((ForeachNode) node); break;
      case FOREACH_NONEMPTY_NODE: visitForeachNonemptyNode((ForeachNonemptyNode) node); break;
      case FOREACH_IFEMPTY_NODE: visitForeachIfemptyNode((ForeachIfemptyNode) node); break;

      case FOR_NODE: visitForNode((ForNode) node); break;

      case CALL_BASIC_NODE: visitCallBasicNode((CallBasicNode) node); break;
      case CALL_DELEGATE_NODE: visitCallDelegateNode((CallDelegateNode) node); break;
      case CALL_PARAM_VALUE_NODE: visitCallParamValueNode((CallParamValueNode) node); break;
      case CALL_PARAM_CONTENT_NODE: visitCallParamContentNode((CallParamContentNode) node); break;

      default: throw new UnsupportedOperationException();
    }
  }


  /**
   * Helper to visit all the children of a node, in order.
   * @param node The parent node whose children to visit.
   * @see #visitChildrenAllowingConcurrentModification
   */
  protected void visitChildren(ParentSoyNode<?> node) {
    visitChildren((ParentNode<? extends SoyNode>) node);
  }


  /**
   * Helper to visit all the children of a node, in order.
   *
   * This method differs from {@code visitChildren} in that we are iterating through a copy of the
   * children. Thus, concurrent modification of the list of children is allowed.
   *
   * @param node The parent node whose children to visit.
   * @see #visitChildren
   */
  protected void visitChildrenAllowingConcurrentModification(ParentSoyNode<?> node) {
    visitChildrenAllowingConcurrentModification((ParentNode<? extends SoyNode>) node);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete nodes.


  protected void visitSoyFileSetNode(SoyFileSetNode node) {
    visitSoyNode(node);
  }

  protected void visitSoyFileNode(SoyFileNode node) {
    visitSoyNode(node);
  }

  protected void visitTemplateBasicNode(TemplateBasicNode node) {
    visitTemplateNode(node);
  }

  protected void visitTemplateDelegateNode(TemplateDelegateNode node) {
    visitTemplateNode(node);
  }

  protected void visitTemplateNode(TemplateNode node) {
    visitSoyNode(node);
  }

  protected void visitRawTextNode(RawTextNode node) {
    visitSoyNode(node);
  }

  protected void visitMsgNode(MsgNode node) {
    visitSoyNode(node);
  }

  protected void visitMsgPlaceholderNode(MsgPlaceholderNode node) {
    visitSoyNode(node);
  }

  protected void visitGoogMsgNode(GoogMsgNode node) {
    visitSoyNode(node);
  }

  protected void visitGoogMsgRefNode(GoogMsgRefNode node) {
    visitSoyNode(node);
  }

  protected void visitMsgPluralNode(MsgPluralNode node) {
    visitSoyNode(node);
  }

  protected void visitMsgPluralCaseNode(MsgPluralCaseNode node) {
    visitSoyNode(node);
  }

  protected void visitMsgPluralDefaultNode(MsgPluralDefaultNode node) {
    visitSoyNode(node);
  }

  protected void visitMsgPluralRemainderNode(MsgPluralRemainderNode node) {
    visitSoyNode(node);
  }

  protected void visitMsgSelectNode(MsgSelectNode node) {
    visitSoyNode(node);
  }

  protected void visitMsgSelectCaseNode(MsgSelectCaseNode node) {
    visitSoyNode(node);
  }

  protected void visitMsgSelectDefaultNode(MsgSelectDefaultNode node) {
    visitSoyNode(node);
  }

  protected void visitMsgHtmlTagNode(MsgHtmlTagNode node) {
    visitSoyNode(node);
  }

  protected void visitPrintNode(PrintNode node) {
    visitSoyNode(node);
  }

  protected void visitPrintDirectiveNode(PrintDirectiveNode node) {
    visitSoyNode(node);
  }

  protected void visitCssNode(CssNode node) {
    visitSoyNode(node);
  }

  protected void visitLetValueNode(LetValueNode node) {
    visitLetNode(node);
  }

  protected void visitLetContentNode(LetContentNode node) {
    visitLetNode(node);
  }

  protected void visitLetNode(LetNode node) {
    visitSoyNode(node);
  }

  protected void visitIfNode(IfNode node) {
    visitSoyNode(node);
  }

  protected void visitIfCondNode(IfCondNode node) {
    visitSoyNode(node);
  }

  protected void visitIfElseNode(IfElseNode node) {
    visitSoyNode(node);
  }

  protected void visitSwitchNode(SwitchNode node) {
    visitSoyNode(node);
  }

  protected void visitSwitchCaseNode(SwitchCaseNode node) {
    visitSoyNode(node);
  }

  protected void visitSwitchDefaultNode(SwitchDefaultNode node) {
    visitSoyNode(node);
  }

  protected void visitForeachNode(ForeachNode node) {
    visitSoyNode(node);
  }

  protected void visitForeachIfemptyNode(ForeachIfemptyNode node) {
    visitSoyNode(node);
  }

  protected void visitForeachNonemptyNode(ForeachNonemptyNode node) {
    visitLoopNode(node);
  }

  protected void visitForNode(ForNode node) {
    visitLoopNode(node);
  }

  protected void visitLoopNode(LoopNode node) {
    visitSoyNode(node);
  }

  protected void visitCallBasicNode(CallBasicNode node) {
    visitCallNode(node);
  }

  protected void visitCallDelegateNode(CallDelegateNode node) {
    visitCallNode(node);
  }

  protected void visitCallNode(CallNode node) {
    visitSoyNode(node);
  }

  protected void visitCallParamValueNode(CallParamValueNode node) {
    visitCallParamNode(node);
  }

  protected void visitCallParamContentNode(CallParamContentNode node) {
    visitCallParamNode(node);
  }

  protected void visitCallParamNode(CallParamNode node) {
    visitSoyNode(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  /**
   * @param node the visited node.
   */
  protected void visitSoyNode(SoyNode node) {
    throw new UnsupportedOperationException();
  }

}
