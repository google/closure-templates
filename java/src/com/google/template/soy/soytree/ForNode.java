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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.LoopNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;
import javax.annotation.Nullable;

/** Node representing a 'for' statement. Should always contain a ForNonemptyNode as only child. */
public final class ForNode extends AbstractParentCommandNode<BlockNode>
    implements StandaloneNode,
        SplitLevelTopNode<BlockNode>,
        StatementNode,
        LoopNode,
        ExprHolderNode,
        HtmlContext.HtmlContextHolder {

  /** The parsed expression for the list that we're iterating over. */
  private final ExprRootNode expr;

  private final SourceLocation openTagLocation;

  @Nullable private final Identifier label;

  @Nullable private HtmlContext htmlContext;

  /**
   * @param id The id for this node.
   * @param location The node's source location
   * @param openTagLocation The source location of the {for ...} block.
   * @param expr The loop collection expression
   */
  public ForNode(
      int id,
      SourceLocation location,
      SourceLocation openTagLocation,
      ExprNode expr,
      @Nullable Identifier label) {
    super(id, location, "for");
    this.expr = new ExprRootNode(expr);
    this.openTagLocation = openTagLocation;
    this.label = label;
  }

  public ForNode(int id, SourceLocation location, SourceLocation openTagLocation, ExprNode expr) {
    this(id, location, openTagLocation, expr, null);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private ForNode(ForNode orig, CopyState copyState) {
    super(orig, copyState);
    this.expr = orig.expr.copy(copyState);
    this.openTagLocation = orig.openTagLocation;
    this.htmlContext = orig.htmlContext;
    this.label = orig.label;
  }

  @Override
  public HtmlContext getHtmlContext() {
    return checkNotNull(
        htmlContext, "Cannot access HtmlContext before HtmlContextVisitor or InferenceEngine.");
  }

  public void setHtmlContext(HtmlContext value) {
    this.htmlContext = value;
  }

  @Override
  public Kind getKind() {
    return Kind.FOR_NODE;
  }

  /** Returns the parsed expression. */
  public ExprRootNode getExpr() {
    return expr;
  }

  public SourceLocation getOpenTagLocation() {
    return openTagLocation;
  }

  @Override
  @Nullable
  public Identifier getLabel() {
    return label;
  }

  @Override
  public String getCommandText() {
    String base =
        ((ForNonemptyNode) getChild(0)).getIndexVar() == null
            ? String.format(
                "%s in %s", ((ForNonemptyNode) getChild(0)).getVarRefName(), expr.toSourceString())
            : String.format(
                "%s, %s in %s",
                ((ForNonemptyNode) getChild(0)).getVarRefName(),
                ((ForNonemptyNode) getChild(0)).getIndexVar().refName(),
                expr.toSourceString());
    return label == null ? base : base + " label=\"" + label.identifier() + "\"";
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    return ImmutableList.of(expr);
  }

  @SuppressWarnings("unchecked")
  @Override
  public ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }

  @Override
  public ForNode copy(CopyState copyState) {
    return new ForNode(this, copyState);
  }
}
