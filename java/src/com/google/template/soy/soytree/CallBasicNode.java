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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.UnionType;
import java.util.List;
import javax.annotation.Nullable;

/** Node representing a call to a basic template. */
public final class CallBasicNode extends CallNode {

  /**
   * The callee expression. Usually this will contain a single node corresponding to the template to
   * be called.
   */
  private ExprRootNode calleeExpr;

  private ExprEquivalence.Wrapper originalShortFormExprEquivalence = null;

  private boolean lazy = false;

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
        case CallNode.ERROR_FALLBACK:
          // Parsed in CallNode.
          break;
        case "variant":
          // Returned directly by getVariantExpr(). Just call valueAsExpr() to validate it here.
          attr.valueAsExpr(errorReporter);
          break;
        case "lazy":
          this.lazy = attr.valueAsEnabled(errorReporter);
          break;
        default:
          errorReporter.report(
              attr.getName().location(),
              UNSUPPORTED_ATTRIBUTE_KEY,
              ident,
              "call",
              ImmutableList.of(
                  "data",
                  CallNode.ERROR_FALLBACK,
                  "key",
                  PHNAME_ATTR,
                  PHEX_ATTR,
                  "variant",
                  "lazy"));
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
    this.lazy = orig.lazy;
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

  public boolean isLazy() {
    return this.lazy;
  }

  public boolean isHtml() {
    return isHtmlReturnType(getCalleeExpr().getType());
  }

  private boolean isHtmlReturnType(SoyType type) {
    if (type instanceof TemplateType) {
      return ((TemplateType) type).getContentKind().getSanitizedContentKind().isHtml();
    }
    if (type instanceof UnionType) {
      for (SoyType member : ((UnionType) type).getMembers()) {
        if (isHtmlReturnType(member)) {
          return true;
        }
      }
    }
    return false;
  }

  public TemplateType getStaticType() {
    Preconditions.checkArgument(isStaticCall());
    return (TemplateType) getCalleeExpr().getType();
  }

  public void setCalleeExpr(ExprRootNode calleeExpr) {
    this.calleeExpr = calleeExpr;
  }

  public void setOriginalShortFormExprEquivalence(
      ExprEquivalence.Wrapper originalShortFormExprEquivalence) {
    this.originalShortFormExprEquivalence = originalShortFormExprEquivalence;
  }

  @Nullable
  public ExprEquivalence.Wrapper getOriginalShortFormExprEquivalence() {
    return originalShortFormExprEquivalence;
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
    var variant = getVariantExpr();
    if (variant != null) {
      commandText.append(" variant=\"").append(variant.toSourceString()).append('"');
    }
    getPlaceholder()
        .userSuppliedName()
        .ifPresent(phname -> commandText.append(" phname=\"").append(phname).append('"'));
    getPlaceholder()
        .example()
        .ifPresent(phex -> commandText.append(" phex=\"").append(phex).append('"'));
    return commandText.toString();
  }

  @Nullable
  public ExprRootNode getVariantExpr() {
    return getAttributes().stream()
        .filter(a -> "variant".equals(a.getName().identifier()) && a.hasExprValue())
        .findFirst()
        .map(a -> a.valueAsExprList().get(0))
        .orElse(null);
  }

  @Override
  public CallBasicNode copy(CopyState copyState) {
    return new CallBasicNode(this, copyState);
  }
}
