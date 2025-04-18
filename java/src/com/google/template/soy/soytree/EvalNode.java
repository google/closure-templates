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
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;

/** Node representing an {eval} command. */
public final class EvalNode extends AbstractCommandNode
    implements StandaloneNode, StatementNode, ExprHolderNode {

  private final ExprRootNode expr;

  public EvalNode(int id, SourceLocation location, ExprNode expr) {
    super(id, location, "eval");
    this.expr = new ExprRootNode(expr);
  }

  private EvalNode(EvalNode orig, CopyState copyState) {
    super(orig, copyState);
    this.expr = orig.expr.copy(copyState);
  }

  @Override
  public EvalNode copy(CopyState copyState) {
    return new EvalNode(this, copyState);
  }

  @Override
  public Kind getKind() {
    return Kind.EVAL_NODE;
  }

  @Override
  public String getCommandText() {
    return expr.toSourceString();
  }

  @Override
  public String toSourceString() {
    return getTagString();
  }

  public ExprRootNode getExpr() {
    return expr;
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
}
