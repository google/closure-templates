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

import static com.google.template.soy.soytree.MessagePlaceholder.PHNAME_ATTR;
import static com.google.template.soy.soytree.MessagePlaceholder.validatePlaceholderName;
import static com.google.template.soy.soytree.MsgSubstUnitPlaceholderNameUtils.genNaiveBaseNameForExpr;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.CommandTagAttribute.CommandTagAttributesHolder;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.MsgBlockNode;
import com.google.template.soy.soytree.SoyNode.MsgSubstUnitNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Node representing a 'select' block.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class MsgSelectNode extends AbstractParentCommandNode<CaseOrDefaultNode>
    implements MsgSubstUnitNode,
        SplitLevelTopNode<CaseOrDefaultNode>,
        ExprHolderNode,
        CommandTagAttributesHolder {

  /** Fallback base select var name. */
  public static final String FALLBACK_BASE_SELECT_VAR_NAME = "STATUS";

  /** The expression for the value to select on. */
  private final ExprRootNode selectExpr;

  private final SourceLocation openTagLocation;

  private final MessagePlaceholder placeholder;

  private MsgSelectNode(
      int id,
      SourceLocation sourceLocation,
      SourceLocation openTagLocation,
      ExprRootNode selectExpr,
      MessagePlaceholder placeholder) {
    super(id, sourceLocation, "select");
    this.openTagLocation = openTagLocation;
    this.selectExpr = selectExpr;
    this.placeholder = placeholder;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private MsgSelectNode(MsgSelectNode orig, CopyState copyState) {
    super(orig, copyState);
    this.openTagLocation = orig.openTagLocation;
    this.selectExpr = orig.selectExpr.copy(copyState);
    this.placeholder = orig.placeholder;
    copyState.updateRefs(orig, this);
  }

  /**
   * Creates a select node specified directly via `select` expression in a Soy file.
   *
   * @param id The id for this node.
   * @param sourceLocation The node's source location.
   * @param selectExpr The expression for the value to select on.
   * @param attributes Attributes of select expression.
   * @param errorReporter For reporting parse errors.
   */
  public static MsgSelectNode fromSelectExpr(
      int id,
      SourceLocation sourceLocation,
      SourceLocation openTagLocation,
      ExprRootNode selectExpr,
      List<CommandTagAttribute> attributes,
      ErrorReporter errorReporter) {
    SourceLocation phNameLocation = null;
    String phName = null;
    for (CommandTagAttribute attribute : attributes) {
      if (PHNAME_ATTR.equals(attribute.getName().identifier())) {
        phNameLocation = attribute.getValueLocation();
        phName = validatePlaceholderName(attribute.getValue(), phNameLocation, errorReporter);
      } else {
        errorReporter.report(
            attribute.getName().location(),
            CommandTagAttribute.UNSUPPORTED_ATTRIBUTE_KEY_SINGLE,
            attribute.getName().identifier(),
            "select",
            PHNAME_ATTR);
      }
    }
    return new MsgSelectNode(
        id,
        sourceLocation,
        openTagLocation,
        selectExpr,
        (phName == null)
            ? MessagePlaceholder.create(
                genNaiveBaseNameForExpr(selectExpr.getRoot(), FALLBACK_BASE_SELECT_VAR_NAME))
            : MessagePlaceholder.createWithUserSuppliedName(phName, phNameLocation));
  }

  /**
   * Creates a select node for a gender expression.
   *
   * @param id The id for this node.
   * @param sourceLocation The node's source location.
   * @param genderExpr The gender expression for which we're bulding a select node.
   * @param baseSelectVarName The base select var name to use (what the translator sees), or null if
   *     it should be generated from the select expression.
   */
  public static MsgSelectNode fromGenderExpr(
      int id,
      SourceLocation sourceLocation,
      SourceLocation openTagLocation,
      ExprRootNode genderExpr,
      @Nullable String baseSelectVarName) {
    return new MsgSelectNode(
        id,
        sourceLocation,
        openTagLocation,
        genderExpr,
        (baseSelectVarName == null)
            ? MessagePlaceholder.create(
                genNaiveBaseNameForExpr(genderExpr.getRoot(), FALLBACK_BASE_SELECT_VAR_NAME))
            : MessagePlaceholder.create(baseSelectVarName));
  }

  @Override
  public Kind getKind() {
    return Kind.MSG_SELECT_NODE;
  }

  /** Returns the expression for the value to select on. */
  public ExprRootNode getExpr() {
    return selectExpr;
  }

  @Override
  public SourceLocation getOpenTagLocation() {
    return this.openTagLocation;
  }

  @Override
  public ImmutableList<CommandTagAttribute> getAttributes() {
    return ImmutableList.of();
  }

  /** Returns the base select var name (what the translator sees). */
  @Override
  public MessagePlaceholder getPlaceholder() {
    return placeholder;
  }

  @Override
  public boolean shouldUseSameVarNameAs(MsgSubstUnitNode other, ExprEquivalence exprEquivalence) {
    if (!(other instanceof MsgSelectNode)) {
      return false;
    }

    MsgSelectNode that = (MsgSelectNode) other;
    return exprEquivalence.equivalent(this.selectExpr, that.selectExpr);
  }

  @Override
  public String getCommandText() {
    Optional<String> phname = placeholder.userSuppliedName();
    return phname.isPresent()
        ? selectExpr.toSourceString() + " phname=\"" + phname.get() + "\""
        : selectExpr.toSourceString();
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
