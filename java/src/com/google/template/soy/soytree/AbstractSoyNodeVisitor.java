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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.basetree.AbstractNodeVisitor;
import com.google.template.soy.soytree.SoyNode.ConditionalBlockNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.LocalVarBlockNode;
import com.google.template.soy.soytree.SoyNode.LocalVarInlineNode;
import com.google.template.soy.soytree.SoyNode.LocalVarNode;
import com.google.template.soy.soytree.SoyNode.LoopNode;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderNode;
import com.google.template.soy.soytree.SoyNode.ParentExprHolderNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.SoyCommandNode;
import com.google.template.soy.soytree.SoyNode.SoyStatementNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.jssrc.GoogMsgNode;
import com.google.template.soy.soytree.jssrc.GoogMsgRefNode;

import java.util.List;


/**
 * Abstract base class for all SoyNode visitors. A visitor is basically a function implemented for
 * some or all SoyNodes, where the implementation can be different for each specific node class.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <ul>
 * <li> Sets up calling the appropriate {@code visitInternal()} method via reflection.
 * <li> Sets up defaulting to trying interface implementations for a given node class (if the
 *      specific node class does not have {@code visitInternal()} defined).
 * </ul>
 *
 * <p>
 * To create a visitor:
 * <ol>
 * <li> Subclass this class.
 * <li> Implement visitInternal() methods for some specific SoyNode classes and perhaps for some
 *      interfaces as well.
 *      <p> This is what happens when visit() is called on a node:
 *      <ul>
 *      <li> if the specific visitInternal() for that node class is implemented, then it is called,
 *      <li> else if one of the node class's interfaces has an implemented visitInternal(), then
 *           that method is called (with ties broken by approximate specificity of the interface)
 *      <li> else an UnsupportedOperationException is thrown.
 *      </ul>
 * <li> Implement a constructor (taking appropriate parameters for your visitor call), and perhaps
 *      implement a getResult() method or something similar for retrieving the result of the call.
 * <li> Implement setup().
 * </ol>
 *
 * @param <R> The return type.
 *
 * @author Kai Huang
 */
public abstract class AbstractSoyNodeVisitor<R> extends AbstractNodeVisitor<SoyNode, R> {


  /** All concrete SoyNode classes. */
  @SuppressWarnings("unchecked")  // varargs
  private static final List<Class<? extends SoyNode>> SOY_NODE_CLASSES =
      ImmutableList.<Class<? extends SoyNode>>of(
          SoyFileSetNode.class, SoyFileNode.class, TemplateNode.class,
          RawTextNode.class,
          MsgNode.class, GoogMsgNode.class /*JS Src backend*/,
          GoogMsgRefNode.class /*JS Src backend*/, MsgHtmlTagNode.class,
          PrintNode.class, PrintDirectiveNode.class, CssNode.class,
          IfNode.class, IfCondNode.class, IfElseNode.class,
          SwitchNode.class, SwitchCaseNode.class, SwitchDefaultNode.class,
          ForeachNode.class, ForeachNonemptyNode.class, ForeachIfemptyNode.class, ForNode.class,
          CallNode.class, CallParamValueNode.class, CallParamContentNode.class);

  /** SoyNode interfaces in approximate order of specificity. */
  @SuppressWarnings("unchecked")  // varargs
  private static final List<Class<? extends SoyNode>> SOY_NODE_INTERFACES =
      ImmutableList.<Class<? extends SoyNode>>of(
          MsgPlaceholderNode.class, ParentExprHolderNode.class, ExprHolderNode.class,
          LocalVarInlineNode.class, LocalVarBlockNode.class, LocalVarNode.class,
          LoopNode.class, ConditionalBlockNode.class, SoyStatementNode.class, SoyCommandNode.class,
          SplitLevelTopNode.class, ParentSoyNode.class, SoyNode.class);


  public AbstractSoyNodeVisitor() {
    super(SOY_NODE_CLASSES, SOY_NODE_INTERFACES);
  }


  /**
   * Helper to visit all the children of a node, in order.
   * @param node The parent node whose children to visit.
   */
  protected void visitChildren(ParentSoyNode<? extends SoyNode> node) {
    for (SoyNode child : node.getChildren()) {
      visit(child);
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete classes.


  protected void visitInternal(SoyFileSetNode node) {}

  protected void visitInternal(SoyFileNode node) {}

  protected void visitInternal(TemplateNode node) {}

  protected void visitInternal(RawTextNode node) {}

  protected void visitInternal(MsgNode node) {}

  protected void visitInternal(GoogMsgNode node) {}

  protected void visitInternal(GoogMsgRefNode node) {}

  protected void visitInternal(MsgHtmlTagNode node) {}

  protected void visitInternal(PrintNode node) {}

  protected void visitInternal(PrintDirectiveNode node) {}

  protected void visitInternal(CssNode node) {}

  protected void visitInternal(IfNode node) {}

  protected void visitInternal(IfCondNode node) {}

  protected void visitInternal(IfElseNode node) {}

  protected void visitInternal(SwitchNode node) {}

  protected void visitInternal(SwitchCaseNode node) {}

  protected void visitInternal(SwitchDefaultNode node) {}

  protected void visitInternal(ForeachNode node) {}

  protected void visitInternal(ForeachNonemptyNode node) {}

  protected void visitInternal(ForeachIfemptyNode node) {}

  protected void visitInternal(ForNode node) {}

  protected void visitInternal(CallNode node) {}

  protected void visitInternal(CallParamValueNode node) {}

  protected void visitInternal(CallParamContentNode node) {}


  // -----------------------------------------------------------------------------------------------
  // Implementations for interfaces.


  protected void visitInternal(SoyNode node) {}

  protected void visitInternal(ParentSoyNode<? extends SoyNode> node) {}

  protected void visitInternal(SplitLevelTopNode<? extends SoyNode> node) {}

  protected void visitInternal(SoyCommandNode node) {}

  protected void visitInternal(SoyStatementNode node) {}

  protected void visitInternal(ConditionalBlockNode<? extends SoyNode> node) {}

  protected void visitInternal(LoopNode<? extends SoyNode> node) {}

  protected void visitInternal(LocalVarNode node) {}

  protected void visitInternal(LocalVarBlockNode<? extends SoyNode> node) {}

  protected void visitInternal(LocalVarInlineNode node) {}

  protected void visitInternal(ExprHolderNode node) {}

  protected void visitInternal(ParentExprHolderNode<? extends SoyNode> node) {}

  protected void visitInternal(MsgPlaceholderNode node) {}

}
