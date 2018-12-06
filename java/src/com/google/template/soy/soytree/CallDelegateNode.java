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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.soytree.CommandTagAttribute.UNSUPPORTED_ATTRIBUTE_KEY;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.StringNode;
import java.util.List;
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

  /** The variant expression for the delegate being called, or null. */
  @Nullable private final ExprRootNode variantExpr;

  /**
   * User-specified attribute to determine whether this delegate call defaults to empty string if
   * there is no active implementation. Default is false.
   */
  private final boolean allowEmptyDefault;

  /**
   * The list of params that need to be type checked when this node is run on a per delegate basis.
   * All the params that could be statically verified will be checked up front (by the {@code
   * CheckCallingParamTypesVisitor}), this list contains the params that could not be statically
   * checked.
   *
   * <p>NOTE:This list will be a subset of the params of the callee, not a subset of the params
   * passed from this caller.
   */
  @Nullable private ImmutableMap<String, Predicate<String>> paramsToRuntimeCheckByDelegate = null;

  public CallDelegateNode(
      int id,
      SourceLocation location,
      Identifier delCalleeName,
      List<CommandTagAttribute> attributes,
      ErrorReporter errorReporter) {
    super(id, location, "delcall", attributes, errorReporter);
    this.delCalleeName = delCalleeName.identifier();
    this.sourceDelCalleeName = delCalleeName;
    ExprRootNode variantExpr = null;
    boolean allowEmptyDefault = false;

    for (CommandTagAttribute attr : attributes) {
      String name = attr.getName().identifier();

      switch (name) {
        case "data":
        case "key":
        case MessagePlaceholders.PHNAME_ATTR:
        case MessagePlaceholders.PHEX_ATTR:
          // Parsed in CallNode.
          break;
        case "variant":
          ExprNode value = attr.valueAsExpr(errorReporter);
          // Do some sanity checks on the variant expression.
          if (value instanceof StringNode) {
            // If the variant is a fixed string, it evaluate to an identifier.
            String variantStr = ((StringNode) value).getValue();
            if (!BaseUtils.isIdentifier(variantStr)) {
              errorReporter.report(location, INVALID_VARIANT_EXPRESSION, variantStr);
            }
          } else if (value instanceof PrimitiveNode) {
            // Variant should not be other primitives (boolean, number, etc.)
            errorReporter.report(location, INVALID_VARIANT_EXPRESSION, value.toSourceString());
          }
          variantExpr = new ExprRootNode(value);
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
                  "data",
                  MessagePlaceholders.PHNAME_ATTR,
                  MessagePlaceholders.PHEX_ATTR,
                  "variant",
                  "allowemptydefault"));
      }
    }

    this.variantExpr = variantExpr;
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
    this.variantExpr = (orig.variantExpr != null) ? orig.variantExpr.copy(copyState) : null;
    this.allowEmptyDefault = orig.allowEmptyDefault;
    this.paramsToRuntimeCheckByDelegate = orig.paramsToRuntimeCheckByDelegate;
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
    return variantExpr;
  }

  /**
   * Sets the params that require runtime type checking for each possible delegate target.
   *
   * <p>This mechanism is used by the TOFU runtime only to save some work when calling templates.
   */
  public void setParamsToRuntimeCheck(
      ImmutableMap<String, Predicate<String>> paramsToRuntimeCheck) {
    checkState(this.paramsToRuntimeCheckByDelegate == null);
    this.paramsToRuntimeCheckByDelegate = checkNotNull(paramsToRuntimeCheck);
  }

  @Override
  public Predicate<String> getParamsToRuntimeCheck(String calleeTemplateName) {
    if (paramsToRuntimeCheckByDelegate == null) {
      return Predicates.alwaysTrue();
    }
    Predicate<String> params = paramsToRuntimeCheckByDelegate.get(calleeTemplateName);
    if (params == null) {
      // The callee was not known when we performed static type checking.  Check all params.
      return Predicates.alwaysTrue();
    }
    return params;
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
    if (getUserSuppliedPhName() != null) {
      commandText.append(" phname=\"").append(getUserSuppliedPhName()).append('"');
    }
    if (getUserSuppliedPhExample() != null) {
      commandText.append(" phex=\"").append(getUserSuppliedPhExample()).append('"');
    }
    if (variantExpr != null) {
      commandText.append(" variant=\"").append(variantExpr.toSourceString()).append('"');
    }
    if (allowEmptyDefault) {
      commandText.append(" allowemptydefault=\"true\"");
    }

    return commandText.toString();
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    ImmutableList.Builder<ExprRootNode> allExprs = ImmutableList.builder();
    if (variantExpr != null) {
      allExprs.add(variantExpr);
    }
    allExprs.addAll(super.getExprList());
    return allExprs.build();
  }

  @Override
  public CallDelegateNode copy(CopyState copyState) {
    return new CallDelegateNode(this, copyState);
  }
}
