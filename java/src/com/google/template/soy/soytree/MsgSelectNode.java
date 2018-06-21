/*
 * Copyright 2010 Google Inc.
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
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.MsgSubstUnitNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import javax.annotation.Nullable;

/**
 * Node representing a 'select' block.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class MsgSelectNode extends AbstractParentCommandNode<CaseOrDefaultNode>
    implements MsgSubstUnitNode, SplitLevelTopNode<CaseOrDefaultNode>, ExprHolderNode {

  /** Fallback base select var name. */
  public static final String FALLBACK_BASE_SELECT_VAR_NAME = "STATUS";

  /** The expression for the value to select on. */
  private final ExprRootNode selectExpr;

  /**
   * The base select var name (what the translator sees). Null if it should be generated from the
   * select expression.
   */
  @Nullable private final String baseSelectVarName;

  public MsgSelectNode(int id, SourceLocation location, ExprNode selectExpr) {
    super(id, location, "select");
    this.selectExpr = new ExprRootNode(selectExpr);
    this.baseSelectVarName = null;

    // TODO: Maybe allow user to write 'phname' attribute in 'select' tag.
    // Note: If we do add support for 'phname' for 'select', it would also be a good time to clean
    // up how 'phname' is parsed for 'call'. Right now, it's parsed in TemplateParser.jj because
    // 'print' needs it to be parsed in TemplateParser.jj (due to print directives possibly
    // appearing between the expression and the 'phname' attribute). But for 'call', it should
    // really be parsed in CallNode.
  }

  /**
   * @param id The id for this node.
   * @param sourceLocation The node's source location.
   * @param selectExpr The expression for the value to select on.
   * @param baseSelectVarName The base select var name to use (what the translator sees), or null if
   *     it should be generated from the select expression.
   */
  public MsgSelectNode(
      int id,
      SourceLocation sourceLocation,
      ExprRootNode selectExpr,
      @Nullable String baseSelectVarName) {
    super(id, sourceLocation, "select");
    this.selectExpr = selectExpr;
    this.baseSelectVarName = baseSelectVarName;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private MsgSelectNode(MsgSelectNode orig, CopyState copyState) {
    super(orig, copyState);
    this.selectExpr = orig.selectExpr.copy(copyState);
    this.baseSelectVarName = orig.baseSelectVarName;
    copyState.updateRefs(orig, this);
  }

  @Override
  public Kind getKind() {
    return Kind.MSG_SELECT_NODE;
  }

  /** Returns the expression for the value to select on. */
  public ExprRootNode getExpr() {
    return selectExpr;
  }

  /** Returns the base select var name (what the translator sees). */
  @Override
  public String getBaseVarName() {
    return (baseSelectVarName != null)
        ? baseSelectVarName
        : MsgSubstUnitBaseVarNameUtils.genNaiveBaseNameForExpr(
            selectExpr.getRoot(), FALLBACK_BASE_SELECT_VAR_NAME);
  }

  @Override
  public boolean shouldUseSameVarNameAs(MsgSubstUnitNode other) {
    if (!(other instanceof MsgSelectNode)) {
      return false;
    }

    MsgSelectNode that = (MsgSelectNode) other;
    return ExprEquivalence.get().equivalent(this.selectExpr, that.selectExpr);
  }

  @Override
  public String getCommandText() {
    return (baseSelectVarName == null)
        ? selectExpr.toSourceString()
        : selectExpr.toSourceString() + " phname=\"" + baseSelectVarName + "\"";
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    return ImmutableList.of(selectExpr);
  }

  @Override
  public MsgBlockNode getParent() {
    return (MsgBlockNode) super.getParent();
  }

  @Override
  public MsgSelectNode copy(CopyState copyState) {
    return new MsgSelectNode(this, copyState);
  }
}
