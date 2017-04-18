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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ConditionalBlockNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;

/**
 * Node representing a block within an 'if' statement that has a conditional expression (i.e. either
 * the 'if' block or an 'elseif' block).
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class IfCondNode extends AbstractBlockCommandNode
    implements ConditionalBlockNode, ExprHolderNode {

  /** The parsed expression. */
  private final ExprRootNode expr;

  /**
   * @param id The id for this node.
   * @param location The node's source location.
   * @param commandName The command name -- either 'if' or 'elseif'.
   * @param expr The if condition.
   */
  public IfCondNode(int id, SourceLocation location, String commandName, ExprNode expr) {
    super(id, location, commandName);
    Preconditions.checkArgument(commandName.equals("if") || commandName.equals("elseif"));
    this.expr = new ExprRootNode(expr);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private IfCondNode(IfCondNode orig, CopyState copyState) {
    super(orig, copyState);
    this.expr = orig.expr.copy(copyState);
  }

  @Override
  public Kind getKind() {
    return Kind.IF_COND_NODE;
  }

  /** Returns the parsed expression. */
  public ExprRootNode getExpr() {
    return expr;
  }

  @Override
  public String getCommandName() {
    return (getParent().getChild(0) == this) ? "if" : "elseif";
  }

  @Override
  public String getCommandText() {
    return expr.toSourceString();
  }

  @Override
  public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getTagString());
    appendSourceStringForChildren(sb);
    // Note: No end tag.
    return sb.toString();
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    return ImmutableList.of(expr);
  }

  @Override
  public IfCondNode copy(CopyState copyState) {
    return new IfCondNode(this, copyState);
  }
}
