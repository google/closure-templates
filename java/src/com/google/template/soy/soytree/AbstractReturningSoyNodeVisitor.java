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
import com.google.template.soy.soytree.SoyNode.MsgSubstUnitNode;

/**
 * Abstract base class for all SoyNode visitors. A visitor is basically a function implemented for
 * some or all SoyNodes, where the implementation can be different for each specific node class.
 *
 * <p>Same as {@link AbstractSoyNodeVisitor} except that in this class, internal {@code visit()}
 * calls return a value.
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
 * @see AbstractSoyNodeVisitor
 */
public abstract class AbstractReturningSoyNodeVisitor<R>
    extends AbstractReturningNodeVisitor<SoyNode, R> {

  @Override
  protected R visit(SoyNode node) {

    switch (node.getKind()) {
      case SOY_FILE_SET_NODE:
        return visitSoyFileSetNode((SoyFileSetNode) node);
      case SOY_FILE_NODE:
        return visitSoyFileNode((SoyFileNode) node);
      case IMPORT_NODE:
        return visitImportNode((ImportNode) node);
      case TEMPLATE_ELEMENT_NODE:
        return visitTemplateElementNode((TemplateElementNode) node);
      case TEMPLATE_BASIC_NODE:
        return visitTemplateBasicNode((TemplateBasicNode) node);
      case TEMPLATE_DELEGATE_NODE:
        return visitTemplateDelegateNode((TemplateDelegateNode) node);

      case RAW_TEXT_NODE:
        return visitRawTextNode((RawTextNode) node);

      case MSG_FALLBACK_GROUP_NODE:
        return visitMsgFallbackGroupNode((MsgFallbackGroupNode) node);
      case MSG_NODE:
        return visitMsgNode((MsgNode) node);
      case MSG_PLURAL_NODE:
        return visitMsgPluralNode((MsgPluralNode) node);
      case MSG_PLURAL_CASE_NODE:
        return visitMsgPluralCaseNode((MsgPluralCaseNode) node);
      case MSG_PLURAL_DEFAULT_NODE:
        return visitMsgPluralDefaultNode((MsgPluralDefaultNode) node);
      case MSG_SELECT_NODE:
        return visitMsgSelectNode((MsgSelectNode) node);
      case MSG_SELECT_CASE_NODE:
        return visitMsgSelectCaseNode((MsgSelectCaseNode) node);
      case MSG_SELECT_DEFAULT_NODE:
        return visitMsgSelectDefaultNode((MsgSelectDefaultNode) node);
      case MSG_PLACEHOLDER_NODE:
        return visitMsgPlaceholderNode((MsgPlaceholderNode) node);
      case MSG_HTML_TAG_NODE:
        return visitMsgHtmlTagNode((MsgHtmlTagNode) node);

      case PRINT_NODE:
        return visitPrintNode((PrintNode) node);
      case PRINT_DIRECTIVE_NODE:
        return visitPrintDirectiveNode((PrintDirectiveNode) node);

      case CONST_NODE:
        return visitConstNode((ConstNode) node);
      case TYPEDEF_NODE:
        return visitTypeDefNode((TypeDefNode) node);
      case LET_VALUE_NODE:
        return visitLetValueNode((LetValueNode) node);
      case LET_CONTENT_NODE:
        return visitLetContentNode((LetContentNode) node);
      case RETURN_NODE:
        return visitReturnNode((ReturnNode) node);
      case ASSIGNMENT_NODE:
        return visitAssignmentNode((AssignmentNode) node);

      case IF_NODE:
        return visitIfNode((IfNode) node);
      case IF_COND_NODE:
        return visitIfCondNode((IfCondNode) node);
      case IF_ELSE_NODE:
        return visitIfElseNode((IfElseNode) node);

      case SWITCH_NODE:
        return visitSwitchNode((SwitchNode) node);
      case SWITCH_CASE_NODE:
        return visitSwitchCaseNode((SwitchCaseNode) node);
      case SWITCH_DEFAULT_NODE:
        return visitSwitchDefaultNode((SwitchDefaultNode) node);

      case FOR_NODE:
        return visitForNode((ForNode) node);
      case FOR_NONEMPTY_NODE:
        return visitForNonemptyNode((ForNonemptyNode) node);

      case WHILE_NODE:
        return visitWhileNode((WhileNode) node);

      case BREAK_NODE:
        return visitBreakNode((BreakNode) node);
      case CONTINUE_NODE:
        return visitContinueNode((ContinueNode) node);

      case CALL_BASIC_NODE:
        return visitCallBasicNode((CallBasicNode) node);
      case CALL_DELEGATE_NODE:
        return visitCallDelegateNode((CallDelegateNode) node);
      case CALL_PARAM_VALUE_NODE:
        return visitCallParamValueNode((CallParamValueNode) node);
      case CALL_PARAM_CONTENT_NODE:
        return visitCallParamContentNode((CallParamContentNode) node);

      case HTML_COMMENT_NODE:
        return visitHtmlCommentNode((HtmlCommentNode) node);
      case HTML_CLOSE_TAG_NODE:
        return visitHtmlCloseTagNode((HtmlCloseTagNode) node);
      case HTML_OPEN_TAG_NODE:
        return visitHtmlOpenTagNode((HtmlOpenTagNode) node);
      case HTML_ATTRIBUTE_NODE:
        return visitHtmlAttributeNode((HtmlAttributeNode) node);
      case HTML_ATTRIBUTE_VALUE_NODE:
        return visitHtmlAttributeValueNode((HtmlAttributeValueNode) node);

      case KEY_NODE:
        return visitKeyNode((KeyNode) node);

      case SKIP_NODE:
        return visitSkipNode((SkipNode) node);

      case VE_LOG_NODE:
        return visitVeLogNode((VeLogNode) node);
      case LOG_NODE:
        return visitLogNode((LogNode) node);
      case DEBUGGER_NODE:
        return visitDebuggerNode((DebuggerNode) node);
      case EXTERN_NODE:
        return visitExternNode((ExternNode) node);
      case JAVA_IMPL_NODE:
        return visitJavaImplNode((JavaImplNode) node);
      case JS_IMPL_NODE:
        return visitJsImplNode((JsImplNode) node);
      case AUTO_IMPL_NODE:
        return visitAutoImplNode((AutoImplNode) node);
      case EVAL_NODE:
        return visitEvalNode((EvalNode) node);
    }
    return visitSoyNode(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete nodes.

  protected R visitSoyFileSetNode(SoyFileSetNode node) {
    return visitSoyNode(node);
  }

  protected R visitSoyFileNode(SoyFileNode node) {
    return visitSoyNode(node);
  }

  protected R visitImportNode(ImportNode node) {
    return visitSoyNode(node);
  }

  protected R visitTemplateBasicNode(TemplateBasicNode node) {
    return visitTemplateNode(node);
  }

  protected R visitTemplateElementNode(TemplateElementNode node) {
    return visitTemplateNode(node);
  }

  protected R visitTemplateDelegateNode(TemplateDelegateNode node) {
    return visitTemplateNode(node);
  }

  protected R visitTemplateNode(TemplateNode node) {
    return visitSoyNode(node);
  }

  protected R visitExternNode(ExternNode node) {
    return visitSoyNode(node);
  }

  protected R visitJavaImplNode(JavaImplNode node) {
    return visitSoyNode(node);
  }

  protected R visitJsImplNode(JsImplNode node) {
    return visitSoyNode(node);
  }

  protected R visitAutoImplNode(AutoImplNode node) {
    return visitSoyNode(node);
  }

  protected R visitRawTextNode(RawTextNode node) {
    return visitSoyNode(node);
  }

  protected R visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    return visitSoyNode(node);
  }

  protected R visitMsgNode(MsgNode node) {
    return visitSoyNode(node);
  }

  protected R visitMsgPluralNode(MsgPluralNode node) {
    return visitMsgSubstUnitNode(node);
  }

  protected R visitMsgPluralCaseNode(MsgPluralCaseNode node) {
    return visitSoyNode(node);
  }

  protected R visitMsgPluralDefaultNode(MsgPluralDefaultNode node) {
    return visitSoyNode(node);
  }

  protected R visitMsgSelectNode(MsgSelectNode node) {
    return visitMsgSubstUnitNode(node);
  }

  protected R visitMsgSelectCaseNode(MsgSelectCaseNode node) {
    return visitSoyNode(node);
  }

  protected R visitMsgSelectDefaultNode(MsgSelectDefaultNode node) {
    return visitSoyNode(node);
  }

  protected R visitMsgPlaceholderNode(MsgPlaceholderNode node) {
    return visitMsgSubstUnitNode(node);
  }

  protected R visitMsgHtmlTagNode(MsgHtmlTagNode node) {
    return visitSoyNode(node);
  }

  protected R visitMsgSubstUnitNode(MsgSubstUnitNode node) {
    return visitSoyNode(node);
  }

  protected R visitPrintNode(PrintNode node) {
    return visitSoyNode(node);
  }

  protected R visitPrintDirectiveNode(PrintDirectiveNode node) {
    return visitSoyNode(node);
  }

  protected R visitConstNode(ConstNode node) {
    return visitSoyNode(node);
  }

  protected R visitTypeDefNode(TypeDefNode node) {
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

  protected R visitReturnNode(ReturnNode node) {
    return visitSoyNode(node);
  }

  protected R visitAssignmentNode(AssignmentNode node) {
    return visitSoyNode(node);
  }

  protected R visitSkipNode(SkipNode node) {
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

  protected R visitForNode(ForNode node) {
    return visitSoyNode(node);
  }

  protected R visitForNonemptyNode(ForNonemptyNode node) {
    return visitSoyNode(node);
  }

  protected R visitWhileNode(WhileNode node) {
    return visitSoyNode(node);
  }

  protected R visitBreakNode(BreakNode node) {
    return visitSoyNode(node);
  }

  protected R visitContinueNode(ContinueNode node) {
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

  protected R visitHtmlOpenTagNode(HtmlOpenTagNode node) {
    return visitSoyNode(node);
  }

  protected R visitHtmlCloseTagNode(HtmlCloseTagNode node) {
    return visitSoyNode(node);
  }

  protected R visitHtmlAttributeNode(HtmlAttributeNode node) {
    return visitSoyNode(node);
  }

  protected R visitHtmlAttributeValueNode(HtmlAttributeValueNode node) {
    return visitSoyNode(node);
  }

  protected R visitKeyNode(KeyNode node) {
    return visitSoyNode(node);
  }

  protected R visitVeLogNode(VeLogNode node) {
    return visitSoyNode(node);
  }

  protected R visitLogNode(LogNode node) {
    return visitSoyNode(node);
  }

  protected R visitDebuggerNode(DebuggerNode node) {
    return visitSoyNode(node);
  }

  protected R visitHtmlCommentNode(HtmlCommentNode node) {
    return visitSoyNode(node);
  }

  protected R visitEvalNode(EvalNode node) {
    return visitSoyNode(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  /**
   * @param node the visited node.
   */
  protected R visitSoyNode(SoyNode node) {
    throw new UnsupportedOperationException(
        "no implementation for: "
            + node
            + "'"
            + node.toSourceString()
            + "'\nwith parent: "
            + node.getParent()
            + "'"
            + node.getParent().toSourceString()
            + "'\nwith grandparent: "
            + node.getParent().getParent()
            + "'"
            + node.getParent().getParent().toSourceString()
            + "'");
  }
}
