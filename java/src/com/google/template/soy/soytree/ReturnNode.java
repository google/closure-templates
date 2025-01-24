/*
 * Copyright 2025 Google Inc.
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
import com.google.template.soy.soytree.SoyNode.StatementNode;

/** Node representing a 'return' statement. */
public final class ReturnNode extends AbstractCommandNode implements ExprHolderNode, StatementNode {

  /** The value expression that the variable is set to. */
  private final ExprRootNode valueExpr;

  public ReturnNode(int id, SourceLocation location, ExprNode expr) {
    super(id, location, "return");
    this.valueExpr = new ExprRootNode(expr);
  }

  private ReturnNode(ReturnNode orig, CopyState copyState) {
    super(orig, copyState);
    this.valueExpr = orig.valueExpr.copy(copyState);
  }

  @Override
  public Kind getKind() {
    return Kind.RETURN_NODE;
  }

  /** Returns the value expression that this variable is set to. */
  public ExprRootNode getExpr() {
    return valueExpr;
  }

  @Override
  public String getCommandText() {
    return getExpr().toSourceString();
  }

  @Override
  public String getTagString() {
    return getTagString(true); // self-ending
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    return ImmutableList.of(valueExpr);
  }

  @Override
  public ReturnNode copy(CopyState copyState) {
    return new ReturnNode(this, copyState);
  }

  @SuppressWarnings("unchecked")
  @Override
  public ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }
}
