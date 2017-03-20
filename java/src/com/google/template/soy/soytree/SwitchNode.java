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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.SoyParsingContext;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;
import java.util.List;

/**
 * Node representing a 'switch' statement.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class SwitchNode extends AbstractParentCommandNode<BlockNode>
    implements StandaloneNode, SplitLevelTopNode<BlockNode>, StatementNode, ExprHolderNode {

  /** The parsed expression. */
  private final ExprRootNode expr;

  private SwitchNode(int id, String commandText, ExprRootNode expr, SourceLocation sourceLocation) {
    super(id, sourceLocation, "switch", commandText);
    this.expr = expr;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private SwitchNode(SwitchNode orig, CopyState copyState) {
    super(orig, copyState);
    this.expr = orig.expr.copy(copyState);
  }

  @Override
  public Kind getKind() {
    return Kind.SWITCH_NODE;
  }

  /** Returns true if this switch has a {@code default} case. */
  public boolean hasDefaultCase() {
    return numChildren() > 0 && getChild(numChildren() - 1) instanceof SwitchDefaultNode;
  }

  /** Returns the text for the expression to switch on. */
  public String getExprText() {
    return expr.toSourceString();
  }

  /** Returns the parsed expression. */
  public ExprRootNode getExpr() {
    return expr;
  }

  @Override
  public List<ExprUnion> getAllExprUnions() {
    return ImmutableList.of(new ExprUnion(expr));
  }

  @Override
  public String getCommandText() {
    return expr.toSourceString();
  }

  @Override
  public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }

  @Override
  public SwitchNode copy(CopyState copyState) {
    return new SwitchNode(this, copyState);
  }

  /** Builder for {@link SwitchNode}. */
  public static final class Builder {
    private final int id;
    private final String commandText;
    private final SourceLocation sourceLocation;

    /**
     * @param id The node's id.
     * @param commandText The node's command text.
     * @param sourceLocation The node's source location.
     */
    public Builder(int id, String commandText, SourceLocation sourceLocation) {
      this.id = id;
      this.commandText = commandText;
      this.sourceLocation = sourceLocation;
    }

    /**
     * Returns a new {@link SwitchNode} built from this builder's state, reporting syntax errors to
     * the given {@link ErrorReporter}.
     */
    public SwitchNode build(SoyParsingContext context) {
      ExprNode expr = new ExpressionParser(commandText, sourceLocation, context).parseExpression();
      return new SwitchNode(id, commandText, new ExprRootNode(expr), sourceLocation);
    }
  }
}
