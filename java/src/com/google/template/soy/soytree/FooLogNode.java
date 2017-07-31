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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Node for a <code {@literal {}foolog...}</code> statement.
 *
 * <p>This is an experimental feature and the name is temporary. So don't get upset!
 */
public final class FooLogNode extends AbstractBlockCommandNode
    implements ExprHolderNode, StatementNode, BlockNode {

  @Nullable private final ExprRootNode dataExpr;
  @Nullable private final ExprRootNode logonlyExpr;
  private final Identifier name;
  @Nullable private Long loggingId;

  public FooLogNode(
      int id,
      SourceLocation location,
      Identifier name,
      List<CommandTagAttribute> attributes,
      ErrorReporter errorReporter) {
    super(id, location, "foolog");
    this.name = checkNotNull(name);
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
              "foolog",
              ImmutableList.of("logonly", "data"));
          break;
      }
    }
    this.dataExpr = configExpr;
    this.logonlyExpr = logonlyExpr;
  }

  private FooLogNode(FooLogNode orig, CopyState copyState) {
    super(orig, copyState);
    this.name = orig.name;
    this.dataExpr = orig.dataExpr == null ? null : orig.dataExpr.copy(copyState);
    this.logonlyExpr = orig.logonlyExpr == null ? null : orig.logonlyExpr.copy(copyState);
  }

  /** Returns the name associated with this log statement. */
  public Identifier getName() {
    return name;
  }

  /**
   * Returns the logging id associated with this log statement, or {@code null} if it doesn't exist.
   */
  @Nullable
  public Long getLoggingId() {
    return loggingId;
  }

  /** Returns a reference to the config expression, if there is one. */
  @Nullable
  public ExprRootNode getConfigExpression() {
    return dataExpr;
  }

  /** Returns a reference to the config expression, if there is one. */
  @Nullable
  public ExprRootNode getLogonlyExpression() {
    return logonlyExpr;
  }

  @Override
  public Kind getKind() {
    return Kind.FOO_LOG_NODE;
  }

  @Override
  public String getCommandText() {
    return name.identifier()
        + (dataExpr != null ? " data=\"" + dataExpr.toSourceString() + "\"" : "")
        + (logonlyExpr != null ? " logonly=\"" + logonlyExpr.toSourceString() + "\"" : "");
  }

  @Override
  public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }

  @Override
  public SoyNode copy(CopyState copyState) {
    return new FooLogNode(this, copyState);
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    ImmutableList.Builder<ExprRootNode> builder = ImmutableList.builder();
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
    sb.append("{/foolog}");
    return sb.toString();
  }

  public void setLoggingId(long id) {
    this.loggingId = id;
  }
}
