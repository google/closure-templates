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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * Node representing a call to a basic template.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class CallBasicNode extends CallNode {
  private static final SoyErrorKind DATA_ATTRIBUTE_ONLY_ALLOWED_ON_STATIC_CALLS =
      SoyErrorKind.of("The `data` attribute is only allowed on static calls.");

  /**
   * The callee expression. Usually this will contain a single node corresponding to the template to
   * be called.
   */
  private final ExprRootNode calleeExpr;

  /**
   * The list of params that need to be type checked when this node is run. All the params that
   * could be statically verified will be checked up front (by the {@code
   * CheckCallingParamTypesVisitor}), this list contains the params that could not be statically
   * checked.
   *
   * <p>NOTE:This list will be a subset of the params of the callee, not a subset of the params
   * passed from this caller.
   */
  @Nullable private Predicate<String> paramsToRuntimeTypeCheck = null;

  public CallBasicNode(
      int id,
      SourceLocation location,
      SourceLocation openTagLocation,
      ExprNode calleeExpr,
      List<CommandTagAttribute> attributes,
      boolean selfClosing,
      ErrorReporter errorReporter) {
    super(id, location, openTagLocation, "call", attributes, selfClosing, errorReporter);

    this.calleeExpr = new ExprRootNode(calleeExpr);

    for (CommandTagAttribute attr : attributes) {
      String ident = attr.getName().identifier();

      switch (ident) {
        case "data":
        case "key":
        case MessagePlaceholders.PHNAME_ATTR:
        case MessagePlaceholders.PHEX_ATTR:
          // Parsed in CallNode.
          break;
        default:
          errorReporter.report(
              attr.getName().location(),
              UNSUPPORTED_ATTRIBUTE_KEY,
              ident,
              "call",
              ImmutableList.of(
                  "data", MessagePlaceholders.PHNAME_ATTR, MessagePlaceholders.PHEX_ATTR));
      }
    }
    if (isPassingData() && !isStaticCall()) {
      errorReporter.report(openTagLocation, DATA_ATTRIBUTE_ONLY_ALLOWED_ON_STATIC_CALLS);
    }
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private CallBasicNode(CallBasicNode orig, CopyState copyState) {
    super(orig, copyState);
    this.calleeExpr = orig.calleeExpr.copy(copyState);
    this.paramsToRuntimeTypeCheck = orig.paramsToRuntimeTypeCheck;
  }

  @Override
  public Kind getKind() {
    return Kind.CALL_BASIC_NODE;
  }

  /** Returns the callee name string as it appears in the source code. */
  public String getSourceCalleeName() {
    checkState(isStaticCall());
    return ((TemplateLiteralNode) calleeExpr.getRoot()).toSourceString();
  }

  @Override
  public SourceLocation getSourceCalleeLocation() {
    return calleeExpr.getSourceLocation();
  }

  /** Returns the full name of the template being called, or null if not yet set. */
  public String getCalleeName() {
    checkState(isStaticCall(), "Expected static call, but found: %s", calleeExpr.getRoot());
    return ((TemplateLiteralNode) calleeExpr.getRoot()).getResolvedName();
  }

  public boolean isStaticCall() {
    return calleeExpr.getRoot().getKind() == ExprNode.Kind.TEMPLATE_LITERAL_NODE;
  }

  public ExprRootNode getCalleeExpr() {
    return calleeExpr;
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    ImmutableList.Builder<ExprRootNode> allExprs = ImmutableList.builder();
    allExprs.add(calleeExpr);
    allExprs.addAll(super.getExprList());
    return allExprs.build();
  }

  /**
   * Sets the names of the params that require runtime type checking against callee's types.
   *
   * <p>This mechanism is used by the TOFU runtime only to save some work when calling templates.
   */
  public void setParamsToRuntimeCheck(Predicate<String> paramNames) {
    checkState(this.paramsToRuntimeTypeCheck == null);
    this.paramsToRuntimeTypeCheck = checkNotNull(paramNames);
  }

  @Override
  public Predicate<String> getParamsToRuntimeCheck(String calleeTemplateName) {
    return paramsToRuntimeTypeCheck == null ? arg -> true : paramsToRuntimeTypeCheck;
  }

  @Override
  public String getCommandText() {
    StringBuilder commandText = new StringBuilder(getSourceCalleeName());

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

    return commandText.toString();
  }

  @Override
  public CallBasicNode copy(CopyState copyState) {
    return new CallBasicNode(this, copyState);
  }
}
