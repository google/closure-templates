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

import static com.google.template.soy.soytree.CommandTagAttribute.UNSUPPORTED_ATTRIBUTE_KEY;

import com.google.common.base.Equivalence;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderInitialNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;
import java.util.List;
import java.util.Objects;
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

  /** The user-supplied placeholder example, or null if not supplied. */
  @Nullable private final String userSuppliedPlaceholderExample;

  @Nullable private HtmlContext htmlContext;

  // TODO(user): Consider adding static factory methods for implicit vs explicit print.
  public PrintNode(
      int id,
      SourceLocation location,
      boolean isImplicit,
      ExprNode expr,
      Iterable<CommandTagAttribute> attributes,
      ErrorReporter errorReporter) {
    super(id, location, isImplicit ? "" : "print");
    this.isImplicit = isImplicit;
    this.expr = new ExprRootNode(expr);

    String placeholderName = null;
    String placeholderExample = null;
    for (CommandTagAttribute attribute : attributes) {
      switch (attribute.getName().identifier()) {
        case MessagePlaceholders.PHNAME_ATTR:
          placeholderName =
              MessagePlaceholders.validatePlaceholderName(
                  attribute.getValue(), attribute.getValueLocation(), errorReporter);
          break;
        case MessagePlaceholders.PHEX_ATTR:
          placeholderExample =
              MessagePlaceholders.validatePlaceholderExample(
                  attribute.getValue(), attribute.getValueLocation(), errorReporter);
          break;
        default:
          errorReporter.report(
              attribute.getName().location(),
              UNSUPPORTED_ATTRIBUTE_KEY,
              attribute.getName().identifier(),
              "print",
              ImmutableList.of(MessagePlaceholders.PHNAME_ATTR, MessagePlaceholders.PHEX_ATTR));
      }
    }
    this.userSuppliedPlaceholderName = placeholderName;
    this.userSuppliedPlaceholderExample = placeholderExample;
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
    this.userSuppliedPlaceholderExample = orig.userSuppliedPlaceholderExample;
    this.htmlContext = orig.htmlContext;
  }

  /**
   * Gets the HTML source context immediately prior to the node (typically tag, attribute value,
   * HTML PCDATA, or plain text) which this node emits in. This affects how the node is escaped (for
   * traditional backends) or how it's passed to incremental DOM APIs.
   */
  public HtmlContext getHtmlContext() {
    return Preconditions.checkNotNull(
        htmlContext, "Cannot access HtmlContext before HtmlContextVisitor or InferenceEngine.");
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

  public boolean hasUserSpecifiedPrintDirectives() {
    return getChildren().stream().anyMatch(pd -> !pd.isSynthetic());
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

  @Nullable
  @Override
  public String getUserSuppliedPhExample() {
    return userSuppliedPlaceholderExample;
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
    return new SamenessKey(this);
  }

  private static final class SamenessKey {
    final PrintNode node;

    SamenessKey(PrintNode node) {
      this.node = node;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof SamenessKey)) {
        return false;
      }
      PrintNode other = ((SamenessKey) obj).node;
      return Objects.equals(node.getUserSuppliedPhName(), other.getUserSuppliedPhName())
          && PrintEquivalence.get().equivalent(node, other);
    }

    @Override
    public int hashCode() {
      return Objects.hash(node.getUserSuppliedPhName(), PrintEquivalence.get().wrap(node));
    }
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    return ImmutableList.of(expr);
  }

  @Override
  public String getCommandText() {
    return doGetCommandText(true);
  }

  private String doGetCommandText(boolean includePhExample) {
    StringBuilder sb = new StringBuilder();
    sb.append(expr.toSourceString());
    for (PrintDirectiveNode child : getChildren()) {
      sb.append(' ').append(child.toSourceString());
    }
    if (userSuppliedPlaceholderName != null) {
      sb.append(" phname=\"").append(userSuppliedPlaceholderName).append('"');
    }
    if (includePhExample && userSuppliedPlaceholderExample != null) {
      sb.append(" phex=\"").append(userSuppliedPlaceholderExample).append('"');
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

  /**
   * Equivalence relation for print nodes.
   *
   * <p>Doesn't account for {@code phname} or {@code phex} attributes
   */
  static final class PrintEquivalence extends Equivalence<PrintNode> {
    private static final PrintEquivalence INSTANCE = new PrintEquivalence();

    static PrintEquivalence get() {
      return INSTANCE;
    }

    @Override
    protected boolean doEquivalent(PrintNode a, PrintNode b) {
      ExprEquivalence exprEquivalence = ExprEquivalence.get();
      if (!exprEquivalence.equivalent(a.getExpr(), b.getExpr())) {
        return false;
      }
      List<PrintDirectiveNode> aDirectives = a.getChildren();
      List<PrintDirectiveNode> bDirectives = b.getChildren();
      if (aDirectives.size() != bDirectives.size()) {
        return false;
      }
      for (int i = 0; i < aDirectives.size(); ++i) {
        PrintDirectiveNode aDirective = aDirectives.get(i);
        PrintDirectiveNode bDirective = bDirectives.get(i);
        if (!aDirective.getName().equals(bDirective.getName())) {
          return false;
        }
        // cast ImmutableList<ExprRootNode> to List<ExprNode>
        @SuppressWarnings("unchecked")
        List<ExprNode> one = (List<ExprNode>) ((List<?>) aDirective.getExprList());
        @SuppressWarnings("unchecked")
        List<ExprNode> two = (List<ExprNode>) ((List<?>) bDirective.getExprList());
        if (!exprEquivalence.pairwise().equivalent(one, two)) {
          return false;
        }
      }
      return true;
    }

    @Override
    protected int doHash(PrintNode t) {
      ExprEquivalence exprEquivalence = ExprEquivalence.get();
      int hc = exprEquivalence.hash(t.getExpr());
      for (PrintDirectiveNode child : t.getChildren()) {
        // cast ImmutableList<ExprRootNode> to List<ExprNode>
        @SuppressWarnings("unchecked")
        List<ExprNode> list = (List<ExprNode>) ((List<?>) child.getExprList());
        hc = 31 * hc + child.getName().hashCode();
        hc = 31 * hc + exprEquivalence.pairwise().hash(list);
      }
      return hc;
    }
  }
}
