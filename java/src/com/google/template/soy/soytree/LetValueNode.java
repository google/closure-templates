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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;

/**
 * Node representing a 'let' statement with a value expression.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class LetValueNode extends LetNode implements ExprHolderNode {

  /** The value expression that the variable is set to. */
  private final ExprRootNode valueExpr;

  public LetValueNode(int id, SourceLocation location, String varName, ExprNode expr) {
    super(id, location, varName);
    this.valueExpr = new ExprRootNode(expr);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private LetValueNode(LetValueNode orig, CopyState copyState) {
    super(orig, copyState);
    this.valueExpr = orig.valueExpr.copy(copyState);
  }

  @Override
  public Kind getKind() {
    return Kind.LET_VALUE_NODE;
  }

  /** Returns the value expression that this variable is set to. */
  public ExprRootNode getExpr() {
    return valueExpr;
  }

  @Override
  public String getCommandText() {
    return "$" + getVarName() + " : " + getExpr().toSourceString();
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
  public LetValueNode copy(CopyState copyState) {
    return new LetValueNode(this, copyState);
  }
}
