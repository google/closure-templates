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

import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.soytree.CommandTagAttribute.UNSUPPORTED_ATTRIBUTE_KEY;
import static com.google.template.soy.soytree.MessagePlaceholder.PHEX_ATTR;
import static com.google.template.soy.soytree.MessagePlaceholder.PHNAME_ATTR;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import java.util.List;

/**
 * Node representing a call to a basic template.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class CallBasicNode extends CallNode {

  /**
   * The callee expression. Usually this will contain a single node corresponding to the template to
   * be called.
   */
  private ExprRootNode calleeExpr;

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
        case PHNAME_ATTR:
        case PHEX_ATTR:
          // Parsed in CallNode.
          break;
        default:
          errorReporter.report(
              attr.getName().location(),
              UNSUPPORTED_ATTRIBUTE_KEY,
              ident,
              "call",
              ImmutableList.of("data", "key", PHNAME_ATTR, PHEX_ATTR));
      }
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
  }

  @Override
  public Kind getKind() {
    return Kind.CALL_BASIC_NODE;
  }

  /** Returns the callee name string as it appears in the source code. */
  public String getSourceCalleeName() {
    return calleeExpr.getRoot().toSourceString();
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

  public void setCalleeExpr(ExprRootNode calleeExpr) {
    this.calleeExpr = calleeExpr;
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    ImmutableList.Builder<ExprRootNode> allExprs = ImmutableList.builder();
    allExprs.add(calleeExpr);
    allExprs.addAll(super.getExprList());
    return allExprs.build();
  }

  @Override
  public String getCommandText() {
    StringBuilder commandText = new StringBuilder(getSourceCalleeName());

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
    return commandText.toString();
  }

  @Override
  public CallBasicNode copy(CopyState copyState) {
    return new CallBasicNode(this, copyState);
  }
}
