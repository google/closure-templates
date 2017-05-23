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

import static com.google.template.soy.soytree.CommandTagAttribute.UNSUPPORTED_ATTRIBUTE_KEY_SINGLE;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderInitialNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;
import javax.annotation.Nullable;

/**
 * Node representing a 'print' statement.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class PrintNode extends AbstractParentCommandNode<PrintDirectiveNode>
    implements StandaloneNode,
        SplitLevelTopNode<PrintDirectiveNode>,
        StatementNode,
        ExprHolderNode,
        MsgPlaceholderInitialNode {

  /** Fallback base placeholder name. */
  private static final String FALLBACK_BASE_PLACEHOLDER_NAME = "XXX";

  /** Whether the command 'print' is implicit. */
  private final boolean isImplicit;

  /** The parsed expression. */
  private final ExprRootNode expr;

  /** The user-supplied placeholder name, or null if not supplied or not applicable. */
  @Nullable private final String userSuppliedPlaceholderName;

  @Nullable private HtmlContext htmlContext;

  // TODO(user): Consider adding static factory methods for implicit vs explicit print.
  public PrintNode(
      int id,
      SourceLocation location,
      boolean isImplicit,
      ExprNode expr,
      @Nullable CommandTagAttribute phname,
      ErrorReporter errorReporter) {
    super(id, location, "print");
    this.isImplicit = isImplicit;
    this.expr = new ExprRootNode(expr);

    if (phname == null) {
      this.userSuppliedPlaceholderName = null;
    } else {
      if (phname.hasName("phname")) {
        this.userSuppliedPlaceholderName = phname.getValue();
      } else {
        errorReporter.report(
            phname.getName().location(),
            UNSUPPORTED_ATTRIBUTE_KEY_SINGLE,
            phname.getName().identifier(),
            "print",
            "phname");
        this.userSuppliedPlaceholderName = null;
      }
    }
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private PrintNode(PrintNode orig, CopyState copyState) {
    super(orig, copyState);
    this.isImplicit = orig.isImplicit;
    this.expr = orig.expr.copy(copyState);
    this.userSuppliedPlaceholderName = orig.userSuppliedPlaceholderName;
    this.htmlContext = orig.htmlContext;
  }

  /**
   * Gets the HTML source context (typically tag, attribute value, HTML PCDATA, or plain text) which
   * this node emits in. This affects how the node is escaped (for traditional backends) or how it's
   * passed to incremental DOM APIs.
   */
  public HtmlContext getHtmlContext() {
    return Preconditions.checkNotNull(
        htmlContext, "Cannot access HtmlContext before HtmlTransformVisitor");
  }

  public void setHtmlContext(HtmlContext value) {
    this.htmlContext = value;
  }

  @Override
  public Kind getKind() {
    return Kind.PRINT_NODE;
  }

  /** Returns whether the 'print' command name was implicit. */
  public boolean isImplicit() {
    return isImplicit;
  }

  /** Returns the parsed expression. */
  public ExprRootNode getExpr() {
    return expr;
  }

  @Nullable
  @Override
  public String getUserSuppliedPhName() {
    return userSuppliedPlaceholderName;
  }

  @Override
  public String genBasePhName() {

    if (userSuppliedPlaceholderName != null) {
      return BaseUtils.convertToUpperUnderscore(userSuppliedPlaceholderName);
    }

    return MsgSubstUnitBaseVarNameUtils.genNaiveBaseNameForExpr(
        expr.getRoot(), FALLBACK_BASE_PLACEHOLDER_NAME);
  }

  @Override
  public Object genSamenessKey() {
    // PrintNodes are considered the same placeholder if they have the same command text.
    return getCommandText();
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    return ImmutableList.of(expr);
  }

  @Override
  public String getCommandName() {
    return isImplicit ? "" : "print";
  }

  @Override
  public String getCommandText() {
    StringBuilder sb = new StringBuilder();
    sb.append(expr.toSourceString());
    for (PrintDirectiveNode child : getChildren()) {
      sb.append(' ').append(child.toSourceString());
    }
    if (userSuppliedPlaceholderName != null) {
      sb.append(" phname=\"").append(userSuppliedPlaceholderName).append('"');
    }
    return sb.toString();
  }

  @Override
  public String toSourceString() {
    return getTagString();
  }

  @SuppressWarnings("unchecked")
  @Override
  public ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }

  @Override
  public PrintNode copy(CopyState copyState) {
    return new PrintNode(this, copyState);
  }
}
