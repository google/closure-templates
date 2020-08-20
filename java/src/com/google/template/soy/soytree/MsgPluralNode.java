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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.template.soy.soytree.MessagePlaceholder.PHNAME_ATTR;
import static com.google.template.soy.soytree.MessagePlaceholder.validatePlaceholderName;
import static com.google.template.soy.soytree.MsgSubstUnitPlaceholderNameUtils.genNaiveBaseNameForExpr;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.CommandTagAttribute.CommandTagAttributesHolder;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.MsgSubstUnitNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Node representing a 'plural' block.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class MsgPluralNode extends AbstractParentCommandNode<CaseOrDefaultNode>
    implements MsgSubstUnitNode,
        SplitLevelTopNode<CaseOrDefaultNode>,
        ExprHolderNode,
        CommandTagAttributesHolder {

  private static final SoyErrorKind PLURAL_OFFSET_OUT_OF_BOUNDS =
      SoyErrorKind.of("The ''offset'' for plural must be a positive integer.");

  /** Fallback base plural var name. */
  public static final String FALLBACK_BASE_PLURAL_VAR_NAME = "NUM";

  /** The location of the {plural ...} tag. */
  private final SourceLocation openTagLocation;

  private final List<CommandTagAttribute> attributes;

  /** The offset. */
  private final int offset;

  /** The parsed expression. */
  private final ExprRootNode pluralExpr;

  private final MessagePlaceholder placeholder;

  private MsgPluralNode(
      int id,
      SourceLocation location,
      SourceLocation openTagLocation,
      ExprRootNode pluralExpr,
      List<CommandTagAttribute> attributes,
      int offset,
      MessagePlaceholder placeholder) {
    super(id, location, "plural");
    this.openTagLocation = openTagLocation;
    this.pluralExpr = pluralExpr;
    this.attributes = ImmutableList.copyOf(attributes);
    this.offset = offset;
    this.placeholder = placeholder;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private MsgPluralNode(MsgPluralNode orig, CopyState copyState) {
    super(orig, copyState);
    this.openTagLocation = orig.openTagLocation;
    this.pluralExpr = orig.pluralExpr.copy(copyState);
    this.attributes =
        orig.attributes.stream().map(c -> c.copy(copyState)).collect(toImmutableList());
    this.offset = orig.offset;
    this.placeholder = orig.placeholder;
    copyState.updateRefs(orig, this);
  }

  /**
   * Creates a plural node specified directly via `plural` expression in a Soy file.
   *
   * @param id The id for this node.
   * @param location The node's source location.
   * @param openTagLocation The location of the {plural ...} tag.
   * @param expr Expression containing plural.
   * @param attributes Attributes of plural expression.
   * @param errorReporter For reporting parse errors.
   */
  public static MsgPluralNode fromPluralExpr(
      int id,
      SourceLocation location,
      SourceLocation openTagLocation,
      ExprNode expr,
      List<CommandTagAttribute> attributes,
      ErrorReporter errorReporter) {
    int offset = 0;
    SourceLocation phNameLocation = null;
    String phName = null;
    for (CommandTagAttribute attribute : attributes) {
      switch (attribute.getName().identifier()) {
        case "offset":
          OptionalInt optionalOffset = attribute.valueAsOptionalInt(errorReporter);
          if (optionalOffset.isPresent()) {
            offset = optionalOffset.getAsInt();
            if (offset <= 0) {
              errorReporter.report(attribute.getValueLocation(), PLURAL_OFFSET_OUT_OF_BOUNDS);
              offset = 0;
            }
          }
          break;
        case PHNAME_ATTR:
          phNameLocation = attribute.getValueLocation();
          phName = validatePlaceholderName(attribute.getValue(), phNameLocation, errorReporter);
          break;
        default:
          errorReporter.report(
              attribute.getName().location(),
              CommandTagAttribute.UNSUPPORTED_ATTRIBUTE_KEY,
              attribute.getName().identifier(),
              "plural",
              ImmutableSet.of("offset", PHNAME_ATTR));
      }
    }

    return new MsgPluralNode(
        id,
        location,
        openTagLocation,
        new ExprRootNode(expr),
        attributes,
        offset,
        (phName == null)
            ? MessagePlaceholder.create(
                genNaiveBaseNameForExpr(expr, FALLBACK_BASE_PLURAL_VAR_NAME))
            : MessagePlaceholder.createWithUserSuppliedName(phName, phNameLocation));
  }

  /** The location of the {plural ...} tag. */
  @Override
  public SourceLocation getOpenTagLocation() {
    return this.openTagLocation;
  }

  @Override
  public List<CommandTagAttribute> getAttributes() {
    return this.attributes;
  }

  @Override
  public Kind getKind() {
    return Kind.MSG_PLURAL_NODE;
  }

  /** Returns the offset. */
  public int getOffset() {
    return offset;
  }

  /** Returns the parsed expression. */
  public ExprRootNode getExpr() {
    return pluralExpr;
  }

  /** Returns the base plural var name (what the translator sees). */
  @Override
  public MessagePlaceholder getPlaceholder() {
    return placeholder;
  }

  @Override
  public boolean shouldUseSameVarNameAs(MsgSubstUnitNode other, ExprEquivalence exprEquivalence) {
    if (!(other instanceof MsgPluralNode)) {
      return false;
    }

    MsgPluralNode that = (MsgPluralNode) other;
    return exprEquivalence.equivalent(this.pluralExpr, that.pluralExpr)
        && this.offset == that.offset
        && Objects.equals(this.placeholder.name(), that.placeholder.name());
  }

  @Override
  public String getCommandText() {
    StringBuilder builder = new StringBuilder(pluralExpr.toSourceString());
    if (offset > 0) {
      builder.append(" offset=\"").append(offset).append("\"");
    }
    placeholder
        .userSuppliedName()
        .ifPresent(phname -> builder.append(" phname=\"").append(phname).append("\""));
    return builder.toString();
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    return ImmutableList.of(pluralExpr);
  }

  @Override
  public MsgBlockNode getParent() {
    return (MsgBlockNode) super.getParent();
  }

  @Override
  public MsgPluralNode copy(CopyState copyState) {
    return new MsgPluralNode(this, copyState);
  }
}
