/*
 * Copyright 2021 Google Inc.
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
import com.google.template.soy.soytree.defn.ConstVar;

/** Node representing a 'const' statement with a value expression. */
public final class ConstNode extends AbstractCommandNode implements ExprHolderNode {

  private final ConstVar var;

  /** The value expression that the variable is set to. */
  private final ExprRootNode valueExpr;

  private final boolean exported;

  public ConstNode(
      int id,
      SourceLocation location,
      String varName,
      SourceLocation varNameLocation,
      ExprNode expr,
      boolean exported) {
    super(id, location, "const");
    this.var = new ConstVar(varName, varNameLocation, null);
    this.valueExpr = new ExprRootNode(expr);
    this.exported = exported;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private ConstNode(ConstNode orig, CopyState copyState) {
    super(orig, copyState);
    this.var = new ConstVar(orig.var);
    this.valueExpr = orig.valueExpr.copy(copyState);
    this.exported = orig.exported;
    copyState.updateRefs(orig.var, this.var);
  }

  public ConstVar getVar() {
    return var;
  }

  public boolean isExported() {
    return exported;
  }

  @SuppressWarnings("unchecked")
  @Override
  public ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }

  @Override
  public Kind getKind() {
    return Kind.CONST_NODE;
  }

  /** Returns the value expression that this variable is set to. */
  public ExprRootNode getExpr() {
    return valueExpr;
  }

  @Override
  public String getCommandText() {
    return var.name() + ": " + valueExpr.toSourceString();
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
  public ConstNode copy(CopyState copyState) {
    return new ConstNode(this, copyState);
  }
}
