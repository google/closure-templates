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

import static com.google.template.soy.soytree.CommandTagAttribute.UNSUPPORTED_ATTRIBUTE_KEY;
import static com.google.template.soy.soytree.MessagePlaceholder.PHEX_ATTR;
import static com.google.template.soy.soytree.MessagePlaceholder.PHNAME_ATTR;
import static com.google.template.soy.soytree.TemplateDelegateNode.VARIANT_ATTR;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.StringNode;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Node representing a call to a delegate template.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class CallDelegateNode extends CallNode {

  private static final SoyErrorKind INVALID_VARIANT_EXPRESSION =
      SoyErrorKind.of(
          "Invalid variant expression \"{0}\" in ''delcall''"
              + " (variant expression must evaluate to an identifier).");

  private final Identifier sourceDelCalleeName;

  /** The name of the delegate template being called. */
  private final String delCalleeName;

  /**
   * User-specified attribute to determine whether this delegate call defaults to empty string if
   * there is no active implementation. Default is false.
   */
  private final boolean allowEmptyDefault;

  private final Supplier<ExprRootNode> memoizedVariantExpr =
      Suppliers.memoize(
          () ->
              getAttributes().stream()
                  .filter(a -> VARIANT_ATTR.equals(a.getName().identifier()) && a.hasExprValue())
                  .findFirst()
                  .map(a -> a.valueAsExprList().get(0))
                  .orElse(null));

  public CallDelegateNode(
      int id,
      SourceLocation location,
      SourceLocation openTagLocation,
      Identifier delCalleeName,
      List<CommandTagAttribute> attributes,
      boolean selfClosing,
      ErrorReporter errorReporter) {
    super(id, location, openTagLocation, "delcall", attributes, selfClosing, errorReporter);
    this.delCalleeName = delCalleeName.identifier();
    this.sourceDelCalleeName = delCalleeName;
    boolean allowEmptyDefault = false;

    for (CommandTagAttribute attr : attributes) {
      String name = attr.getName().identifier();

      switch (name) {
        case "data":
        case "key":
        case PHNAME_ATTR:
        case PHEX_ATTR:
          // Parsed in CallNode.
          break;
        case "variant":
          ExprRootNode value = attr.valueAsExpr(errorReporter);
          // Do some sanity checks on the variant expression.
          if (value.getRoot() instanceof StringNode) {
            // If the variant is a fixed string, it evaluates to an identifier.
            String variantStr = ((StringNode) value.getRoot()).getValue();
            if (!BaseUtils.isIdentifier(variantStr)) {
              errorReporter.report(location, INVALID_VARIANT_EXPRESSION, variantStr);
            }
          } else if (value.getRoot() instanceof PrimitiveNode) {
            // Variant should not be other primitives (boolean, number, etc.)
            errorReporter.report(location, INVALID_VARIANT_EXPRESSION, value.toSourceString());
          }
          break;
        case "allowemptydefault":
          allowEmptyDefault = attr.valueAsEnabled(errorReporter);
          break;
        default:
          errorReporter.report(
              attr.getName().location(),
              UNSUPPORTED_ATTRIBUTE_KEY,
              name,
              "call",
              ImmutableList.of(
                  "data", "key", PHNAME_ATTR, PHEX_ATTR, "variant", "allowemptydefault"));
      }
    }

    this.allowEmptyDefault = allowEmptyDefault;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private CallDelegateNode(CallDelegateNode orig, CopyState copyState) {
    super(orig, copyState);
    this.delCalleeName = orig.delCalleeName;
    this.sourceDelCalleeName = orig.sourceDelCalleeName;
    this.allowEmptyDefault = orig.allowEmptyDefault;
  }

  @Override
  public Kind getKind() {
    return Kind.CALL_DELEGATE_NODE;
  }

  /** Returns the name of the delegate template being called. */
  public String getDelCalleeName() {
    return delCalleeName;
  }

  @Override
  public SourceLocation getSourceCalleeLocation() {
    return sourceDelCalleeName.location();
  }

  /** Returns the variant expression for the delegate being called, or null if it's a string. */
  @Nullable
  public ExprRootNode getDelCalleeVariantExpr() {
    return memoizedVariantExpr.get();
  }

  /** Returns whether this delegate call defaults to empty string if there's no active impl. */
  public boolean allowEmptyDefault() {
    return allowEmptyDefault;
  }

  @Override
  public String getCommandText() {
    StringBuilder commandText = new StringBuilder(delCalleeName);

    if (isPassingAllData()) {
      commandText.append(" data=\"all\"");
    } else if (getDataExpr() != null) {
      commandText.append(" data=\"").append(getDataExpr().toSourceString()).append('"');
    }
    getPlaceholder()
        .userSuppliedName()
        .ifPresent(phname -> commandText.append(" phname=\"").append(phname).append('"'));
    getPlaceholder()
        .example()
        .ifPresent(phex -> commandText.append(" phex=\"").append(phex).append('"'));
    ExprRootNode variantExpr = getDelCalleeVariantExpr();
    if (variantExpr != null) {
      commandText.append(" variant=\"").append(variantExpr.toSourceString()).append('"');
    }
    if (allowEmptyDefault) {
      commandText.append(" allowemptydefault=\"true\"");
    }

    return commandText.toString();
  }

  @Override
  public CallDelegateNode copy(CopyState copyState) {
    return new CallDelegateNode(this, copyState);
  }
}
