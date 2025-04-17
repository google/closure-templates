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
import com.google.template.soy.soytree.SoyNode.MsgSubstUnitNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;

/**
 * Abstract base class for all SoyNode visitors. A visitor is basically a function implemented for
 * some or all SoyNodes, where the implementation can be different for each specific node class.
 *
 * <p>Same as {@link AbstractReturningSoyNodeVisitor} except that in this class, internal {@code
 * visit()} calls do not return a value.
 *
 * <p>To create a visitor:
 *
 * <ol>
 *   <li>Subclass this class.
 *   <li>Implement {@code visit*Node()} methods for some specific node types.
 *   <li>Implement fallback methods for node types not specifically handled. The most general
 *       fallback method is {@link #visitSoyNode visitSoyNode()}, which is usually needed. Other
 *       fallback methods include {@code visitLoopNode()} and {@code visitCallParamNode()}.
 *   <li>Maybe implement a constructor, taking appropriate parameters for your visitor call.
 *   <li>Maybe implement {@link #exec exec()} if this visitor needs to return a non-null final
 *       result and/or if this visitor has state that needs to be setup/reset before each unrelated
 *       use of {@code visit()}.
 * </ol>
 *
 * @param <R> The return type of this visitor.
 * @see AbstractReturningSoyNodeVisitor
 */
public abstract class AbstractSoyNodeVisitor<R> extends AbstractNodeVisitor<SoyNode, R> {

  @Override
  protected void visit(SoyNode node) {

    switch (node.getKind()) {
      case SOY_FILE_SET_NODE:
        visitSoyFileSetNode((SoyFileSetNode) node);
        break;
      case SOY_FILE_NODE:
        visitSoyFileNode((SoyFileNode) node);
        break;

      case IMPORT_NODE:
        visitImportNode((ImportNode) node);
        break;

      case TEMPLATE_ELEMENT_NODE:
        visitTemplateElementNode((TemplateElementNode) node);
        break;
      case TEMPLATE_BASIC_NODE:
        visitTemplateBasicNode((TemplateBasicNode) node);
        break;
      case TEMPLATE_DELEGATE_NODE:
        visitTemplateDelegateNode((TemplateDelegateNode) node);
        break;

      case RAW_TEXT_NODE:
        visitRawTextNode((RawTextNode) node);
        break;

      case MSG_FALLBACK_GROUP_NODE:
        visitMsgFallbackGroupNode((MsgFallbackGroupNode) node);
        break;
      case MSG_NODE:
        visitMsgNode((MsgNode) node);
        break;
      case MSG_PLURAL_NODE:
        visitMsgPluralNode((MsgPluralNode) node);
        break;
      case MSG_PLURAL_CASE_NODE:
        visitMsgPluralCaseNode((MsgPluralCaseNode) node);
        break;
      case MSG_PLURAL_DEFAULT_NODE:
        visitMsgPluralDefaultNode((MsgPluralDefaultNode) node);
        break;
      case MSG_SELECT_NODE:
        visitMsgSelectNode((MsgSelectNode) node);
        break;
      case MSG_SELECT_CASE_NODE:
        visitMsgSelectCaseNode((MsgSelectCaseNode) node);
        break;
      case MSG_SELECT_DEFAULT_NODE:
        visitMsgSelectDefaultNode((MsgSelectDefaultNode) node);
        break;
      case MSG_PLACEHOLDER_NODE:
        visitMsgPlaceholderNode((MsgPlaceholderNode) node);
        break;
      case MSG_HTML_TAG_NODE:
        visitMsgHtmlTagNode((MsgHtmlTagNode) node);
        break;

      case PRINT_NODE:
        visitPrintNode((PrintNode) node);
        break;
      case PRINT_DIRECTIVE_NODE:
        visitPrintDirectiveNode((PrintDirectiveNode) node);
        break;

      case CONST_NODE:
        visitConstNode((ConstNode) node);
        break;
      case TYPEDEF_NODE:
        visitTypeDefNode((TypeDefNode) node);
        break;
      case LET_VALUE_NODE:
        visitLetValueNode((LetValueNode) node);
        break;
      case LET_CONTENT_NODE:
        visitLetContentNode((LetContentNode) node);
        break;
      case RETURN_NODE:
        visitReturnNode((ReturnNode) node);
        break;
      case ASSIGNMENT_NODE:
        visitAssignmentNode((AssignmentNode) node);
        break;

      case IF_NODE:
        visitIfNode((IfNode) node);
        break;
      case IF_COND_NODE:
        visitIfCondNode((IfCondNode) node);
        break;
      case IF_ELSE_NODE:
        visitIfElseNode((IfElseNode) node);
        break;

      case SWITCH_NODE:
        visitSwitchNode((SwitchNode) node);
        break;
      case SWITCH_CASE_NODE:
        visitSwitchCaseNode((SwitchCaseNode) node);
        break;
      case SWITCH_DEFAULT_NODE:
        visitSwitchDefaultNode((SwitchDefaultNode) node);
        break;

      case FOR_NODE:
        visitForNode((ForNode) node);
        break;
      case FOR_NONEMPTY_NODE:
        visitForNonemptyNode((ForNonemptyNode) node);
        break;

      case WHILE_NODE:
        visitWhileNode((WhileNode) node);
        break;

      case BREAK_NODE:
        visitBreakNode((BreakNode) node);
        break;
      case CONTINUE_NODE:
        visitContinueNode((ContinueNode) node);
        break;

      case CALL_BASIC_NODE:
        visitCallBasicNode((CallBasicNode) node);
        break;
      case CALL_DELEGATE_NODE:
        visitCallDelegateNode((CallDelegateNode) node);
        break;
      case CALL_PARAM_VALUE_NODE:
        visitCallParamValueNode((CallParamValueNode) node);
        break;
      case CALL_PARAM_CONTENT_NODE:
        visitCallParamContentNode((CallParamContentNode) node);
        break;

      case HTML_CLOSE_TAG_NODE:
        visitHtmlCloseTagNode((HtmlCloseTagNode) node);
        break;
      case HTML_OPEN_TAG_NODE:
        visitHtmlOpenTagNode((HtmlOpenTagNode) node);
        break;
      case HTML_COMMENT_NODE:
        visitHtmlCommentNode((HtmlCommentNode) node);
        break;
      case HTML_ATTRIBUTE_NODE:
        visitHtmlAttributeNode((HtmlAttributeNode) node);
        break;
      case HTML_ATTRIBUTE_VALUE_NODE:
        visitHtmlAttributeValueNode((HtmlAttributeValueNode) node);
        break;

      case KEY_NODE:
        visitKeyNode((KeyNode) node);
        break;

      case SKIP_NODE:
        visitSkipNode((SkipNode) node);
        break;

      case VE_LOG_NODE:
        visitVeLogNode((VeLogNode) node);
        break;

      case LOG_NODE:
        visitLogNode((LogNode) node);
        break;
      case DEBUGGER_NODE:
        visitDebuggerNode((DebuggerNode) node);
        break;
      case EXTERN_NODE:
        visitExternNode((ExternNode) node);
        break;
      case JAVA_IMPL_NODE:
        visitJavaImplNode((JavaImplNode) node);
        break;
      case JS_IMPL_NODE:
        visitJsImplNode((JsImplNode) node);
        break;
      case AUTO_IMPL_NODE:
        visitAutoImplNode((AutoImplNode) node);
        break;
      case EVAL_NODE:
        visitEvalNode((EvalNode) node);
        break;
      default:
        visitSoyNode(node);
        break;
    }
  }

  /**
   * Helper to visit all the children of a node, in order.
   *
   * @param node The parent node whose children to visit.
   * @see #visitChildrenAllowingConcurrentModification
   */
  protected void visitChildren(ParentSoyNode<?> node) {
    visitChildren((ParentNode<? extends SoyNode>) node);
  }

  /**
   * Helper to visit all the children of a node, in order.
   *
   * <p>This method differs from {@code visitChildren} in that we are iterating through a copy of
   * the children. Thus, concurrent modification of the list of children is allowed.
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

  protected void visitExternNode(ExternNode node) {
    visitSoyNode(node);
  }

  protected void visitJavaImplNode(JavaImplNode node) {
    visitSoyNode(node);
  }

  protected void visitJsImplNode(JsImplNode node) {
    visitSoyNode(node);
  }

  protected void visitAutoImplNode(AutoImplNode node) {
    visitSoyNode(node);
  }

  protected void visitImportNode(ImportNode node) {
    visitSoyNode(node);
  }

  protected void visitTemplateBasicNode(TemplateBasicNode node) {
    visitTemplateNode(node);
  }

  protected void visitTemplateElementNode(TemplateElementNode node) {
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

  protected void visitSkipNode(SkipNode node) {
    visitSoyNode(node);
  }

  protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    visitSoyNode(node);
  }

  protected void visitMsgNode(MsgNode node) {
    visitSoyNode(node);
  }

  protected void visitMsgPluralNode(MsgPluralNode node) {
    visitMsgSubstUnitNode(node);
  }

  protected void visitMsgPluralCaseNode(MsgPluralCaseNode node) {
    visitSoyNode(node);
  }

  protected void visitMsgPluralDefaultNode(MsgPluralDefaultNode node) {
    visitSoyNode(node);
  }

  protected void visitMsgSelectNode(MsgSelectNode node) {
    visitMsgSubstUnitNode(node);
  }

  protected void visitMsgSelectCaseNode(MsgSelectCaseNode node) {
    visitSoyNode(node);
  }

  protected void visitMsgSelectDefaultNode(MsgSelectDefaultNode node) {
    visitSoyNode(node);
  }

  protected void visitMsgPlaceholderNode(MsgPlaceholderNode node) {
    visitMsgSubstUnitNode(node);
  }

  protected void visitMsgHtmlTagNode(MsgHtmlTagNode node) {
    visitSoyNode(node);
  }

  protected void visitMsgSubstUnitNode(MsgSubstUnitNode node) {
    visitSoyNode(node);
  }

  protected void visitPrintNode(PrintNode node) {
    visitSoyNode(node);
  }

  protected void visitPrintDirectiveNode(PrintDirectiveNode node) {
    visitSoyNode(node);
  }

  protected void visitConstNode(ConstNode node) {
    visitSoyNode(node);
  }

  protected void visitTypeDefNode(TypeDefNode node) {
    visitSoyNode(node);
  }

  protected void visitLetValueNode(LetValueNode node) {
    visitLetNode(node);
  }

  protected void visitLetContentNode(LetContentNode node) {
    visitLetNode(node);
  }

  protected void visitReturnNode(ReturnNode node) {
    visitSoyNode(node);
  }

  protected void visitAssignmentNode(AssignmentNode node) {
    visitSoyNode(node);
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

  protected void visitForNode(ForNode node) {
    visitSoyNode(node);
  }

  protected void visitForNonemptyNode(ForNonemptyNode node) {
    visitSoyNode(node);
  }

  protected void visitWhileNode(WhileNode node) {
    visitSoyNode(node);
  }

  protected void visitBreakNode(BreakNode node) {
    visitSoyNode(node);
  }

  protected void visitContinueNode(ContinueNode node) {
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

  protected void visitHtmlOpenTagNode(HtmlOpenTagNode node) {
    visitSoyNode(node);
  }

  protected void visitHtmlCloseTagNode(HtmlCloseTagNode node) {
    visitSoyNode(node);
  }

  protected void visitHtmlAttributeNode(HtmlAttributeNode node) {
    visitSoyNode(node);
  }

  protected void visitHtmlAttributeValueNode(HtmlAttributeValueNode node) {
    visitSoyNode(node);
  }

  protected void visitHtmlCommentNode(HtmlCommentNode node) {
    visitSoyNode(node);
  }

  protected void visitKeyNode(KeyNode node) {
    visitSoyNode(node);
  }

  protected void visitVeLogNode(VeLogNode node) {
    visitSoyNode(node);
  }

  protected void visitLogNode(LogNode node) {
    visitSoyNode(node);
  }

  protected void visitDebuggerNode(DebuggerNode node) {
    visitSoyNode(node);
  }

  protected void visitEvalNode(EvalNode node) {
    visitSoyNode(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  /**
   * @param node the visited node.
   */
  protected void visitSoyNode(SoyNode node) {
    throw new UnsupportedOperationException("no implementation for: " + node);
  }
}
