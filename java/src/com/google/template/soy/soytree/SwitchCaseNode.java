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
import com.google.template.soy.soytree.SoyNode.ConditionalBlockNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;

/**
 * Node representing a 'case' block in a 'switch' block.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class SwitchCaseNode extends CaseOrDefaultNode
    implements ConditionalBlockNode, ExprHolderNode {

  /** The parsed expression list. */
  private final ImmutableList<ExprRootNode> exprList;

  public SwitchCaseNode(
      int id,
      SourceLocation location,
      SourceLocation openTagLocation,
      ImmutableList<ExprNode> exprList) {
    super(id, location, openTagLocation, "case");
    this.exprList = ExprRootNode.wrap(exprList);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private SwitchCaseNode(SwitchCaseNode orig, CopyState copyState) {
    super(orig, copyState);
    ImmutableList.Builder<ExprRootNode> builder = ImmutableList.builder();
    for (ExprRootNode origExpr : orig.exprList) {
      builder.add(origExpr.copy(copyState));
    }
    this.exprList = builder.build();
  }

  @Override
  public Kind getKind() {
    return Kind.SWITCH_CASE_NODE;
  }

  @Override
  public String getCommandText() {
    return SoyTreeUtils.toSourceString(exprList);
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    return exprList;
  }

  @Override
  public SwitchCaseNode copy(CopyState copyState) {
    return new SwitchCaseNode(this, copyState);
  }
}
