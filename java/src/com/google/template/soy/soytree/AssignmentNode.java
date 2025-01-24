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

/** Node representing a 'set' statement. */
public final class AssignmentNode extends AbstractCommandNode
    implements ExprHolderNode, StatementNode {

  private final ExprRootNode lhs;
  private final ExprRootNode rhs;

  public AssignmentNode(int id, SourceLocation location, ExprNode lhs, ExprNode rhs) {
    super(id, location, "set");
    this.lhs = new ExprRootNode(lhs);
    this.rhs = new ExprRootNode(rhs);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private AssignmentNode(AssignmentNode orig, CopyState copyState) {
    super(orig, copyState);
    this.lhs = orig.lhs.copy(copyState);
    this.rhs = orig.rhs.copy(copyState);
  }

  @Override
  public Kind getKind() {
    return Kind.ASSIGNMENT_NODE;
  }

  public ExprRootNode getLhs() {
    return lhs;
  }

  public ExprRootNode getRhs() {
    return rhs;
  }

  @Override
  public String getCommandText() {
    return lhs.toSourceString() + " = " + rhs.toSourceString();
  }

  @Override
  public String getTagString() {
    return getTagString(true); // self-ending
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    return ImmutableList.of(lhs, rhs);
  }

  @Override
  public AssignmentNode copy(CopyState copyState) {
    return new AssignmentNode(this, copyState);
  }

  @SuppressWarnings("unchecked")
  @Override
  public ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }
}
