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

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.basicfunctions.RangeFunction;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;
import java.util.List;

/**
 * Node representing a 'foreach' statement. Should always contain a ForNonemptyNode as the first
 * child. May contain a second child, which should be a ForIfemptyNode.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p>TODO(b/70577468): eliminate LoopNode
 *
 */
public final class ForNode extends AbstractParentCommandNode<BlockNode>
    implements StandaloneNode, SplitLevelTopNode<BlockNode>, StatementNode, ExprHolderNode {

  /** The parsed expression for the list that we're iterating over. */
  private final ExprRootNode expr;

  /**
   * @param id The id for this node.
   * @param location The node's source location
   * @param expr The loop collection expression
   */
  public ForNode(int id, SourceLocation location, String commandName, ExprNode expr) {
    super(id, location, commandName);
    this.expr = new ExprRootNode(expr);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private ForNode(ForNode orig, CopyState copyState) {
    super(orig, copyState);
    this.expr = orig.expr.copy(copyState);
  }

  @Override
  public Kind getKind() {
    return Kind.FOR_NODE;
  }

  /** Returns true if this {@code foreach} loop has and {@code ifempty} block. */
  public boolean hasIfEmptyBlock() {
    return numChildren() > 1;
  }

  /** Returns the parsed expression. */
  public ExprRootNode getExpr() {
    return expr;
  }

  /**
   * Returns a {@link RangeArgs} object if the loop expression is a {@code range(...)} expression.
   */
  public Optional<RangeArgs> exprAsRangeArgs() {
    // TODO(b/70577468): consider caching this value? This would only help out TOFU, so it might not
    // be worth it.
    if (expr.getRoot() instanceof FunctionNode) {
      FunctionNode fn = (FunctionNode) expr.getRoot();
      if (fn.getSoyFunction() instanceof RangeFunction) {
        return Optional.of(RangeArgs.create(fn.getChildren()));
      }
    }
    return Optional.absent();
  }

  @Override
  public String getCommandText() {
    return "$" + ((ForNonemptyNode) getChild(0)).getVarName() + " in " + expr.toSourceString();
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    return ImmutableList.of(expr);
  }

  @SuppressWarnings("unchecked")
  @Override
  public ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }

  @Override
  public ForNode copy(CopyState copyState) {
    return new ForNode(this, copyState);
  }

  /** The arguments to a {@code range(...)} expression in a {@code {for ...}} loop statement. */
  @AutoValue
  public abstract static class RangeArgs {
    static RangeArgs create(List<ExprNode> args) {
      switch (args.size()) {
        case 1:
          return new AutoValue_ForNode_RangeArgs(
              Optional.<ExprNode>absent(), args.get(0), Optional.<ExprNode>absent());
        case 2:
          return new AutoValue_ForNode_RangeArgs(
              Optional.of(args.get(0)), args.get(1), Optional.<ExprNode>absent());
        case 3:
          return new AutoValue_ForNode_RangeArgs(
              Optional.of(args.get(0)), args.get(1), Optional.of(args.get(2)));
        default:
          throw new AssertionError();
      }
    }

    /** The expression for the iteration start point. Default is {@code 0}. */
    public abstract Optional<ExprNode> start();

    /** The expression for the iteration end point. This is interpreted as an exclusive limit. */
    public abstract ExprNode limit();

    /** The expression for the iteration increment. Default is {@code 1}. */
    public abstract Optional<ExprNode> increment();
  }
}
