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
import com.google.template.soy.soytree.SoyNode.ConditionalBlockNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;

/** Node representing a 'while' statement. */
public final class WhileNode extends AbstractBlockCommandNode
    implements ConditionalBlockNode, ExprHolderNode, StatementNode {

  /** The parsed expression for the condition. */
  private final ExprRootNode expr;

  public WhileNode(int id, SourceLocation location, SourceLocation openTagLocation, ExprNode expr) {
    super(id, location, openTagLocation, "while");
    this.expr = new ExprRootNode(expr);
  }

  private WhileNode(WhileNode orig, CopyState copyState) {
    super(orig, copyState);
    this.expr = orig.expr.copy(copyState);
  }

  @Override
  public Kind getKind() {
    return Kind.WHILE_NODE;
  }

  /** Returns the condition expression. */
  public ExprRootNode getExpr() {
    return expr;
  }

  @Override
  public String getCommandText() {
    return getExpr().toSourceString();
  }

  @Override
  public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getTagString());
    appendSourceStringForChildren(sb);
    sb.append("{/").append(getCommandName()).append('}');
    return sb.toString();
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    return ImmutableList.of(expr);
  }

  @Override
  public WhileNode copy(CopyState copyState) {
    return new WhileNode(this, copyState);
  }

  @SuppressWarnings("unchecked")
  @Override
  public ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }
}
