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

package com.google.template.soy.soytree;

import com.google.template.soy.basetree.AbstractReturningNodeVisitor;
import com.google.template.soy.basetree.ParentNode;
import com.google.template.soy.soytree.SoyNode.LoopNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.jssrc.GoogMsgNode;
import com.google.template.soy.soytree.jssrc.GoogMsgRefNode;

import java.util.List;


/**
 * Abstract base class for all SoyNode visitors. A visitor is basically a function implemented for
 * some or all SoyNodes, where the implementation can be different for each specific node class.
 *
 * <p> Same as {@link AbstractSoyNodeVisitor} except that in this class, internal {@code visit()}
 * calls return a value.
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
 * @see AbstractSoyNodeVisitor
 * @author Kai Huang
 */
public abstract class AbstractReturningSoyNodeVisitor<R>
    extends AbstractReturningNodeVisitor<SoyNode, R> {


  @Override protected R visit(SoyNode node) {

    switch (node.getKind()) {

      case SOY_FILE_SET_NODE: return visitSoyFileSetNode((SoyFileSetNode) node);
      case SOY_FILE_NODE: return visitSoyFileNode((SoyFileNode) node);
      case TEMPLATE_BASIC_NODE: return visitTemplateBasicNode((TemplateBasicNode) node);
      case TEMPLATE_DELEGATE_NODE: return visitTemplateDelegateNode((TemplateDelegateNode) node);

      case RAW_TEXT_NODE: return visitRawTextNode((RawTextNode) node);

      case GOOG_MSG_NODE: return visitGoogMsgNode((GoogMsgNode) node);
      case GOOG_MSG_REF_NODE: return visitGoogMsgRefNode((GoogMsgRefNode) node);

      case MSG_NODE: return visitMsgNode((MsgNode) node);
      case MSG_PLURAL_NODE: return visitMsgPluralNode((MsgPluralNode) node);
      case MSG_PLURAL_CASE_NODE: return visitMsgPluralCaseNode((MsgPluralCaseNode) node);
      case MSG_PLURAL_DEFAULT_NODE: return visitMsgPluralDefaultNode((MsgPluralDefaultNode) node);
      case MSG_PLURAL_REMAINDER_NODE:
        return visitMsgPluralRemainderNode((MsgPluralRemainderNode) node);
      case MSG_SELECT_NODE: return visitMsgSelectNode((MsgSelectNode) node);
      case MSG_SELECT_CASE_NODE: return visitMsgSelectCaseNode((MsgSelectCaseNode) node);
      case MSG_SELECT_DEFAULT_NODE: return visitMsgSelectDefaultNode((MsgSelectDefaultNode) node);
      case MSG_PLACEHOLDER_NODE: return visitMsgPlaceholderNode((MsgPlaceholderNode) node);
      case MSG_HTML_TAG_NODE: return visitMsgHtmlTagNode((MsgHtmlTagNode) node);

      case PRINT_NODE: return visitPrintNode((PrintNode) node);
      case PRINT_DIRECTIVE_NODE: return visitPrintDirectiveNode((PrintDirectiveNode) node);

      case CSS_NODE: return visitCssNode((CssNode) node);

      case LET_VALUE_NODE: return visitLetValueNode((LetValueNode) node);
      case LET_CONTENT_NODE: return visitLetContentNode((LetContentNode) node);

      case IF_NODE: return visitIfNode((IfNode) node);
      case IF_COND_NODE: return visitIfCondNode((IfCondNode) node);
      case IF_ELSE_NODE: return visitIfElseNode((IfElseNode) node);

      case SWITCH_NODE: return visitSwitchNode((SwitchNode) node);
      case SWITCH_CASE_NODE: return visitSwitchCaseNode((SwitchCaseNode) node);
      case SWITCH_DEFAULT_NODE: return visitSwitchDefaultNode((SwitchDefaultNode) node);

      case FOREACH_NODE: return visitForeachNode((ForeachNode) node);
      case FOREACH_NONEMPTY_NODE: return visitForeachNonemptyNode((ForeachNonemptyNode) node);
      case FOREACH_IFEMPTY_NODE: return visitForeachIfemptyNode((ForeachIfemptyNode) node);

      case FOR_NODE: return visitForNode((ForNode) node);

      case CALL_BASIC_NODE: return visitCallBasicNode((CallBasicNode) node);
      case CALL_DELEGATE_NODE: return visitCallDelegateNode((CallDelegateNode) node);
      case CALL_PARAM_VALUE_NODE: return visitCallParamValueNode((CallParamValueNode) node);
      case CALL_PARAM_CONTENT_NODE: return visitCallParamContentNode((CallParamContentNode) node);

      case LOG_NODE: return visitLogNode((LogNode) node);
      case DEBUGGER_NODE: return visitDebuggerNode((DebuggerNode) node);

      default: throw new UnsupportedOperationException();
    }
  }


  /**
   * Helper to visit all the children of a node, in order.
   * @param node The parent node whose children to visit.
   * @return The list of return values from visiting the children.
   * @see #visitChildrenAllowingConcurrentModification
   */
  protected List<R> visitChildren(ParentSoyNode<?> node) {
    return visitChildren((ParentNode<? extends SoyNode>) node);
  }


  /**
   * Helper to visit all the children of a node, in order.
   *
   * This method differs from {@code visitChildren} in that we are iterating through a copy of the
   * children. Thus, concurrent modification of the list of children is allowed.
   *
   * @param node The parent node whose children to visit.
   * @return The list of return values from visiting the children.
   * @see #visitChildren
   */
  protected List<R> visitChildrenAllowingConcurrentModification(ParentSoyNode<?> node) {
    return visitChildrenAllowingConcurrentModification((ParentNode<? extends SoyNode>) node);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete nodes.


  protected R visitSoyFileSetNode(SoyFileSetNode node) {
    return visitSoyNode(node);
  }

  protected R visitSoyFileNode(SoyFileNode node) {
    return visitSoyNode(node);
  }

  protected R visitTemplateBasicNode(TemplateNode node) {
    return visitTemplateNode(node);
  }

  protected R visitTemplateDelegateNode(TemplateNode node) {
    return visitTemplateNode(node);
  }

  protected R visitTemplateNode(TemplateNode node) {
    return visitSoyNode(node);
  }

  protected R visitRawTextNode(RawTextNode node) {
    return visitSoyNode(node);
  }

  protected R visitGoogMsgNode(GoogMsgNode node) {
    return visitSoyNode(node);
  }

  protected R visitGoogMsgRefNode(GoogMsgRefNode node) {
    return visitSoyNode(node);
  }

  protected R visitMsgNode(MsgNode node) {
    return visitSoyNode(node);
  }

  protected R visitMsgPluralNode(MsgPluralNode node) {
    return visitSoyNode(node);
  }

  protected R visitMsgPluralCaseNode(MsgPluralCaseNode node) {
    return visitSoyNode(node);
  }

  protected R visitMsgPluralDefaultNode(MsgPluralDefaultNode node) {
    return visitSoyNode(node);
  }

  protected R visitMsgPluralRemainderNode(MsgPluralRemainderNode node) {
    return visitSoyNode(node);
  }

  protected R visitMsgSelectNode(MsgSelectNode node) {
    return visitSoyNode(node);
  }

  protected R visitMsgSelectCaseNode(MsgSelectCaseNode node) {
    return visitSoyNode(node);
  }

  protected R visitMsgSelectDefaultNode(MsgSelectDefaultNode node) {
    return visitSoyNode(node);
  }

  protected R visitMsgPlaceholderNode(MsgPlaceholderNode node) {
    return visitSoyNode(node);
  }

  protected R visitMsgHtmlTagNode(MsgHtmlTagNode node) {
    return visitSoyNode(node);
  }

  protected R visitPrintNode(PrintNode node) {
    return visitSoyNode(node);
  }

  protected R visitPrintDirectiveNode(PrintDirectiveNode node) {
    return visitSoyNode(node);
  }

  protected R visitCssNode(CssNode node) {
    return visitSoyNode(node);
  }

  protected R visitLetValueNode(LetValueNode node) {
    return visitLetNode(node);
  }

  protected R visitLetContentNode(LetContentNode node) {
    return visitLetNode(node);
  }

  protected R visitLetNode(LetNode node) {
    return visitSoyNode(node);
  }

  protected R visitIfNode(IfNode node) {
    return visitSoyNode(node);
  }

  protected R visitIfCondNode(IfCondNode node) {
    return visitSoyNode(node);
  }

  protected R visitIfElseNode(IfElseNode node) {
    return visitSoyNode(node);
  }

  protected R visitSwitchNode(SwitchNode node) {
    return visitSoyNode(node);
  }

  protected R visitSwitchCaseNode(SwitchCaseNode node) {
    return visitSoyNode(node);
  }

  protected R visitSwitchDefaultNode(SwitchDefaultNode node) {
    return visitSoyNode(node);
  }

  protected R visitForeachNode(ForeachNode node) {
    return visitSoyNode(node);
  }

  protected R visitForeachIfemptyNode(ForeachIfemptyNode node) {
    return visitSoyNode(node);
  }

  protected R visitForeachNonemptyNode(ForeachNonemptyNode node) {
    return visitLoopNode(node);
  }

  protected R visitForNode(ForNode node) {
    return visitLoopNode(node);
  }

  protected R visitLoopNode(LoopNode node) {
    return visitSoyNode(node);
  }

  protected R visitCallBasicNode(CallBasicNode node) {
    return visitCallNode(node);
  }

  protected R visitCallDelegateNode(CallDelegateNode node) {
    return visitCallNode(node);
  }

  protected R visitCallNode(CallNode node) {
    return visitSoyNode(node);
  }

  protected R visitCallParamValueNode(CallParamValueNode node) {
    return visitCallParamNode(node);
  }

  protected R visitCallParamContentNode(CallParamContentNode node) {
    return visitCallParamNode(node);
  }

  protected R visitCallParamNode(CallParamNode node) {
    return visitSoyNode(node);
  }

  protected R visitLogNode(LogNode node) {
    return visitSoyNode(node);
  }

  protected R visitDebuggerNode(DebuggerNode node) {
    return visitSoyNode(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  /**
   * @param node the visited node.
   */
  protected R visitSoyNode(SoyNode node) {
    throw new UnsupportedOperationException();
  }

}
