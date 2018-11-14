/*
 * Copyright 2018 Google Inc.
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
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;

/**
 * Node representing a 'key' statement, e.g. {@code <div {key $foo}></div>}. This keys DOM nodes for
 * incremental dom.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class KeyNode extends AbstractCommandNode
    implements StandaloneNode, StatementNode, ExprHolderNode {

  /** The parsed expression representing the key value. */
  private final ExprRootNode expr;

  public KeyNode(int id, SourceLocation location, ExprNode expr) {
    super(id, location, "key");
    this.expr = new ExprRootNode(expr);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private KeyNode(KeyNode orig, CopyState copyState) {
    super(orig, copyState);
    this.expr = orig.expr.copy(copyState);
  }

  @Override
  public Kind getKind() {
    return Kind.KEY_NODE;
  }

  /** Returns the parsed expression. */
  public ExprRootNode getExpr() {
    return expr;
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    return ImmutableList.of(expr);
  }

  @Override
  public String getCommandText() {
    return expr.toSourceString();
  }

  @Override
  public String toSourceString() {
    return getTagString();
  }

  @SuppressWarnings("unchecked")
  @Override
  public ParentSoyNode<StandaloneNode> getParent() {
    // Cast is necessary so this is typed as a parent with a StandaloneNode child (this KeyNode).
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }

  @Override
  public KeyNode copy(CopyState copyState) {
    return new KeyNode(this, copyState);
  }
}
