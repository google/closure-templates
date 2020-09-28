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
import com.google.template.soy.soytree.CommandTagAttribute.CommandTagAttributesHolder;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;

/**
 * Node representing a 'switch' statement.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class SwitchNode extends AbstractParentCommandNode<CaseOrDefaultNode>
    implements StandaloneNode,
        SplitLevelTopNode<CaseOrDefaultNode>,
        StatementNode,
        ExprHolderNode,
        CommandTagAttributesHolder {

  /** The parsed expression. */
  private final ExprRootNode expr;

  private final SourceLocation openTagLocation;

  public SwitchNode(
      int id, SourceLocation location, SourceLocation openTagLocation, ExprNode expr) {
    super(id, location, "switch");
    this.expr = new ExprRootNode(expr);
    this.openTagLocation = openTagLocation;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private SwitchNode(SwitchNode orig, CopyState copyState) {
    super(orig, copyState);
    this.expr = orig.expr.copy(copyState);
    this.openTagLocation = orig.openTagLocation;
  }

  @Override
  public Kind getKind() {
    return Kind.SWITCH_NODE;
  }

  @Override
  public SourceLocation getOpenTagLocation() {
    return this.openTagLocation;
  }

  @Override
  public ImmutableList<CommandTagAttribute> getAttributes() {
    return ImmutableList.of();
  }

  /** Returns true if this switch has a {@code default} case. */
  public boolean hasDefaultCase() {
    return numChildren() > 0 && getChild(numChildren() - 1) instanceof SwitchDefaultNode;
  }

  /** Returns the parsed expression. */
  public ExprRootNode getExpr() {
    return expr;
  }

  @Override
  public String getCommandText() {
    return expr.toSourceString();
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
  public SwitchNode copy(CopyState copyState) {
    return new SwitchNode(this, copyState);
  }
}
