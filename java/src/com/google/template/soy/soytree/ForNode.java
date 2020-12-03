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
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;

/**
 * Node representing a 'for' statement. Should always contain a ForNonemptyNode as the first child.
 * May contain a second child, which should be a ForIfemptyNode.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class ForNode extends AbstractParentCommandNode<BlockNode>
    implements StandaloneNode, SplitLevelTopNode<BlockNode>, StatementNode, ExprHolderNode {

  /** The parsed expression for the list that we're iterating over. */
  private final ExprRootNode expr;

  private final SourceLocation openTagLocation;

  /**
   * @param id The id for this node.
   * @param location The node's source location
   * @param openTagLocation The source location of the {for ...} block.
   * @param expr The loop collection expression
   */
  public ForNode(int id, SourceLocation location, SourceLocation openTagLocation, ExprNode expr) {
    super(id, location, "for");
    this.expr = new ExprRootNode(expr);
    this.openTagLocation = openTagLocation;
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
  }

  @Override
  public Kind getKind() {
    return Kind.FOR_NODE;
  }

  /** Returns true if this {@code foreach} loop has and {@code ifempty} block. */
  public boolean hasIfEmptyBlock() {
    return numChildren() > 1;
  }

  /** Returns the parsed expression. */
  public ExprRootNode getExpr() {
    return expr;
  }

  public SourceLocation getOpenTagLocation() {
    return openTagLocation;
  }

  @Override
  public String getCommandText() {
    return ((ForNonemptyNode) getChild(0)).getIndexVar() == null
        ? String.format(
            "%s in %s", ((ForNonemptyNode) getChild(0)).getVarRefName(), expr.toSourceString())
        : String.format(
            "%s, %s in %s",
            ((ForNonemptyNode) getChild(0)).getVarRefName(),
            ((ForNonemptyNode) getChild(0)).getIndexVar().refName(),
            expr.toSourceString());
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
