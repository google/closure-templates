/*
 * Copyright 2017 Google Inc.
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
import static com.google.template.soy.soytree.SoyTreeUtils.getNodeAsHtmlTagNode;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.MsgBlockNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Node for a <code {@literal {}velog...}</code> statement.
 */
public final class VeLogNode extends AbstractBlockCommandNode
    implements ExprHolderNode, StatementNode, MsgBlockNode {

  /**
   * An equivalence key for comparing {@link VeLogNode} instances.
   *
   * <p>This ignores things like {@link SoyNode#getId()} and {@link SoyNode#getSourceLocation()} and
   * is useful for deciding placeholder equivalence for velog nodes in messages.
   */
  static final class SamenessKey {
    private final VeLogNode delegate;

    private SamenessKey(VeLogNode delegate) {
      this.delegate = delegate;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof SamenessKey)) {
        return false;
      }
      SamenessKey otherKey = (SamenessKey) other;
      return ExprEquivalence.get().equivalent(delegate.veDataExpr, otherKey.delegate.veDataExpr)
          && ExprEquivalence.get().equivalent(delegate.logonlyExpr, otherKey.delegate.logonlyExpr)
          && ExprEquivalence.get().equivalent(delegate.dataExpr, otherKey.delegate.dataExpr);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          ExprEquivalence.get().wrap(delegate.veDataExpr),
          ExprEquivalence.get().wrap(delegate.logonlyExpr),
          ExprEquivalence.get().wrap(delegate.dataExpr));
    }
  }

  private final ExprRootNode veDataExpr;
  @Nullable private final ExprRootNode dataExpr;
  @Nullable private final ExprRootNode logonlyExpr;
  @Nullable private Long loggingId;

  public VeLogNode(
      int id,
      SourceLocation location,
      ExprNode veDataExpr,
      List<CommandTagAttribute> attributes,
      ErrorReporter errorReporter) {
    super(id, location, "velog");
    this.veDataExpr = new ExprRootNode(checkNotNull(veDataExpr));
    ExprRootNode configExpr = null;
    ExprRootNode logonlyExpr = null;
    for (CommandTagAttribute attr : attributes) {
      switch (attr.getName().identifier()) {
        case "logonly":
          logonlyExpr = new ExprRootNode(attr.valueAsExpr(errorReporter));
          break;
        case "data":
          configExpr = new ExprRootNode(attr.valueAsExpr(errorReporter));
          break;
        default:
          errorReporter.report(
              attr.getName().location(),
              CommandTagAttribute.UNSUPPORTED_ATTRIBUTE_KEY,
              attr.getName().identifier(),
              "velog",
              ImmutableList.of("logonly", "data"));
          break;
      }
    }
    this.dataExpr = configExpr;
    this.logonlyExpr = logonlyExpr;
  }

  private VeLogNode(VeLogNode orig, CopyState copyState) {
    super(orig, copyState);
    this.veDataExpr = orig.veDataExpr.copy(copyState);
    this.dataExpr = orig.dataExpr == null ? null : orig.dataExpr.copy(copyState);
    this.logonlyExpr = orig.logonlyExpr == null ? null : orig.logonlyExpr.copy(copyState);
    this.loggingId = orig.loggingId;
  }

  SamenessKey getSamenessKey() {
    return new SamenessKey(this);
  }

  /**
   * Returns the logging id associated with this log statement, or {@code null} if it doesn't exist.
   */
  @Nullable
  public Long getLoggingId() {
    return loggingId;
  }

  /** Returns a reference to the VE expression. */
  public ExprRootNode getVeDataExpression() {
    return veDataExpr;
  }

  /** Returns a reference to the config expression, if there is one. */
  @Nullable
  public ExprRootNode getConfigExpression() {
    return dataExpr;
  }

  /** Returns a reference to the logonly expression, if there is one. */
  @Nullable
  public ExprRootNode getLogonlyExpression() {
    return logonlyExpr;
  }

  @Override
  public Kind getKind() {
    return Kind.VE_LOG_NODE;
  }

  /** Returns the open tag node if it exists. */
  @Nullable
  public HtmlOpenTagNode getOpenTagNode() {
    if (numChildren() > 0) {
      return (HtmlOpenTagNode) getNodeAsHtmlTagNode(getChild(0), /*openTag=*/ true);
    }
    return null;
  }

  /** Returns the close tag node if it exists. */
  @Nullable
  public HtmlCloseTagNode getCloseTagNode() {
    if (numChildren() > 1) {
      return (HtmlCloseTagNode)
          getNodeAsHtmlTagNode(getChild(numChildren() - 1), /*openTag=*/ false);
    }
    return null;
  }

  @Override
  public String getCommandText() {
    return veDataExpr.toSourceString()
        + (dataExpr != null ? " data=\"" + dataExpr.toSourceString() + "\"" : "")
        + (logonlyExpr != null ? " logonly=\"" + logonlyExpr.toSourceString() + "\"" : "");
  }

  @Override
  public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }

  @Override
  public VeLogNode copy(CopyState copyState) {
    return new VeLogNode(this, copyState);
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    ImmutableList.Builder<ExprRootNode> builder = ImmutableList.builder();
    builder.add(veDataExpr);
    if (dataExpr != null) {
      builder.add(dataExpr);
    }
    if (logonlyExpr != null) {
      builder.add(logonlyExpr);
    }
    return builder.build();
  }

  @Override
  public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getTagString());
    appendSourceStringForChildren(sb);
    sb.append("{/velog}");
    return sb.toString();
  }

  public void setLoggingId(long id) {
    this.loggingId = id;
  }
}
