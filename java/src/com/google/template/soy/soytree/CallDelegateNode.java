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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.TriState;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.soytree.defn.TemplateParam;
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

  /**
   * The name of the delegate template being called.
   *
   * <p>Not final. The contextual autoescaper can rewrite the callee name, if the same callee
   * template is called into from two different contexts, and the autoescaper needs to clone a
   * template and retarget the call.
   */
  private String delCalleeName;

  /** The variant expression for the delegate being called, or null. */
  @Nullable private final ExprRootNode variantExpr;

  /**
   * User-specified attribute to determine whether this delegate call defaults to empty string if
   * there is no active implementation. Default is false.
   *
   * <p>TriState.UNSET if attribute is not specified.
   */
  private final TriState allowEmptyDefault;

  /**
   * The list of params that need to be type checked when this node is run on a per delegate basis.
   * All the params that could be statically verified will be checked up front (by the {@code
   * CheckCallingParamTypesVisitor}), this list contains the params that could not be statically
   * checked.
   *
   * <p>NOTE:This list will be a subset of the params of the callee, not a subset of the params
   * passed from this caller.
   */
  @Nullable
  private ImmutableMap<TemplateDelegateNode, ImmutableList<TemplateParam>>
      paramsToRuntimeCheckByDelegate = null;

  public CallDelegateNode(
      int id,
      SourceLocation location,
      String delCalleeName,
      List<CommandTagAttribute> attributes,
      ErrorReporter errorReporter) {
    super(id, location, "delcall", attributes, errorReporter);
    this.delCalleeName = delCalleeName;

    ExprRootNode variantExpr = null;
    TriState allowEmptyDefault = TriState.UNSET;

    for (CommandTagAttribute attr : attributes) {
      String name = attr.getName().identifier();

      switch (name) {
        case "data":
        case "phname":
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
          allowEmptyDefault = attr.valueAsTriState(errorReporter);
          break;
        default:
          errorReporter.report(
              attr.getName().location(),
              UNSUPPORTED_ATTRIBUTE_KEY,
              name,
              "call",
              ImmutableList.of("data", "phname", "variant", "allowemptydefault"));
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

  /** Do not call this method outside the contextual autoescaper. */
  public void setDelCalleeName(String delCalleeName) {
    Preconditions.checkArgument(BaseUtils.isDottedIdentifier(delCalleeName));
    this.delCalleeName = delCalleeName;
  }

  /** Returns the variant expression for the delegate being called, or null if it's a string. */
  @Nullable
  public ExprRootNode getDelCalleeVariantExpr() {
    return variantExpr;
  }

  /** Sets the params that require runtime type checking for each possible delegate target. */
  public void setParamsToRuntimeCheck(
      ImmutableMap<TemplateDelegateNode, ImmutableList<TemplateParam>> paramsToRuntimeCheck) {
    checkState(this.paramsToRuntimeCheckByDelegate == null);
    this.paramsToRuntimeCheckByDelegate = checkNotNull(paramsToRuntimeCheck);
  }

  @Override
  public ImmutableList<TemplateParam> getParamsToRuntimeCheck(TemplateNode callee) {
    if (paramsToRuntimeCheckByDelegate == null) {
      return callee.getParams();
    }
    ImmutableList<TemplateParam> params = paramsToRuntimeCheckByDelegate.get(callee);
    if (params == null) {
      // The callee was not known when we performed static type checking.  Check all params.
      return callee.getParams();
    }
    return params;
  }

  /** Returns whether this delegate call defaults to empty string if there's no active impl. */
  public boolean allowEmptyDefault() {
    // Default to 'false' if not specified.
    return allowEmptyDefault == TriState.ENABLED;
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
    if (variantExpr != null) {
      commandText.append(" variant=\"").append(variantExpr.toSourceString()).append('"');
    }
    if (allowEmptyDefault.isSet()) {
      commandText
          .append(" allowemptydefault=\"")
          .append(allowEmptyDefault == TriState.ENABLED)
          .append('"');
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
