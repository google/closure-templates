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

package com.google.template.soy.sharedpasses.opti;

import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.internalutils.InternalValueUtils;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FloatNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.sharedpasses.render.Environment;
import com.google.template.soy.sharedpasses.render.RenderException;
import javax.inject.Inject;

/**
 * Visitor for simplifying expressions based on constant values known at compile time.
 *
 * <p>Package-private helper for {@link SimplifyVisitor}.
 *
 */
final class SimplifyExprVisitor extends AbstractExprNodeVisitor<Void> {

  /** The PreevalVisitor for this instance (can reuse). */
  private final PreevalVisitor preevalVisitor;

  @Inject
  SimplifyExprVisitor(PreevalVisitorFactory preevalVisitorFactory) {
    this.preevalVisitor = preevalVisitorFactory.create(Environment.prerenderingEnvironment());
  }

  // -----------------------------------------------------------------------------------------------
  // Implementation for root node.

  @Override
  protected void visitExprRootNode(ExprRootNode node) {
    visit(node.getRoot());
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for collection nodes.

  @Override
  protected void visitListLiteralNode(ListLiteralNode node) {
    // Visit children only. We cannot simplify the list literal itself.
    visitChildren(node);
  }

  @Override
  protected void visitMapLiteralNode(MapLiteralNode node) {
    // Visit children only. We cannot simplify the map literal itself.
    visitChildren(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for reference nodes.

  // TODO: Port this to the new representation once we figure out what it does.
  /*
    @Override protected void visitDataRefNode(DataRefNode node) {

      boolean allExprsAreConstant = true;
      for (ExprNode child : node.getChildren()) {
        if (child instanceof DataRefAccessExprNode) {
          ExprNode expr = ((DataRefAccessExprNode) child).getChild(0);
          visit(expr);
          if (! (expr instanceof ConstantNode)) {
            allExprsAreConstant = false;
          }
        }
      }

      if (allExprsAreConstant) {
        attemptPreeval(node);
      }
    }
  */

  // -----------------------------------------------------------------------------------------------
  // Implementations for operators.

  @Override
  protected void visitAndOpNode(AndOpNode node) {

    // Recurse.
    visitChildren(node);

    // Can simplify if either child is constant. We assume no side-effects.
    SoyValue operand0 = getConstantOrNull(node.getChild(0));
    if (operand0 != null) {
      ExprNode replacementNode = operand0.coerceToBoolean() ? node.getChild(1) : node.getChild(0);
      node.getParent().replaceChild(node, replacementNode);
    }
  }

  @Override
  protected void visitOrOpNode(OrOpNode node) {

    // Recurse.
    visitChildren(node);

    // Can simplify if either child is constant. We assume no side-effects.
    SoyValue operand0 = getConstantOrNull(node.getChild(0));
    if (operand0 != null) {
      ExprNode replacementNode = operand0.coerceToBoolean() ? node.getChild(0) : node.getChild(1);
      node.getParent().replaceChild(node, replacementNode);
    }
  }

  @Override
  protected void visitConditionalOpNode(ConditionalOpNode node) {

    // Recurse.
    visitChildren(node);

    // Can simplify if operand0 is constant. We assume no side-effects.
    SoyValue operand0 = getConstantOrNull(node.getChild(0));
    if (operand0 == null) {
      return; // cannot simplify
    }

    ExprNode replacementNode = operand0.coerceToBoolean() ? node.getChild(1) : node.getChild(2);
    node.getParent().replaceChild(node, replacementNode);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for functions.

  @Override
  protected void visitFunctionNode(FunctionNode node) {

    // Cannot simplify nonplugin functions.
    // TODO(brndn): we can actually simplify checkNotNull and quoteKeysIfJs.
    if (node.getSoyFunction() instanceof BuiltinFunction) {
      return;
    }

    // Default to fallback implementation.
    visitExprNode(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override
  protected void visitExprNode(ExprNode node) {

    if (!(node instanceof ParentExprNode)) {
      return;
    }
    ParentExprNode nodeAsParent = (ParentExprNode) node;

    // Recurse.
    visitChildren(nodeAsParent);

    // If all children are constants, we attempt to preevaluate this node and replace it with a
    // constant.
    for (ExprNode child : nodeAsParent.getChildren()) {
      if (!isConstant(child)) {
        return; // cannot preevaluate
      }
    }
    attemptPreeval(nodeAsParent);
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers.

  /**
   * Attempts to preevaluate a node. If successful, the node is replaced with a new constant node in
   * the tree. If unsuccessful, the tree is not changed.
   */
  private void attemptPreeval(ExprNode node) {

    // Note that we need to catch RenderException because preevaluation may fail, e.g. when
    // (a) the expression uses a bidi function that needs bidiGlobalDir to be in scope, but the
    //     apiCallScope is not currently active,
    // (b) the expression uses an external function (Soy V1 syntax),
    // (c) other cases I haven't thought up.

    SoyValue preevalResult;
    try {
      preevalResult = preevalVisitor.exec(node);
    } catch (RenderException e) {
      return; // failed to preevaluate
    }
    PrimitiveNode newNode =
        InternalValueUtils.convertPrimitiveDataToExpr((PrimitiveData) preevalResult);
    if (newNode != null) {
      node.getParent().replaceChild(node, newNode);
    }
  }

  static boolean isConstant(ExprNode expr) {
    return (expr instanceof GlobalNode && ((GlobalNode) expr).isResolved())
        || expr instanceof PrimitiveNode;
  }

  /** Returns the value of the given expression if it's constant, else returns null. */
  static SoyValue getConstantOrNull(ExprNode expr) {

    switch (expr.getKind()) {
      case NULL_NODE:
        return NullData.INSTANCE;
      case BOOLEAN_NODE:
        return BooleanData.forValue(((BooleanNode) expr).getValue());
      case INTEGER_NODE:
        return IntegerData.forValue(((IntegerNode) expr).getValue());
      case FLOAT_NODE:
        return FloatData.forValue(((FloatNode) expr).getValue());
      case STRING_NODE:
        return StringData.forValue(((StringNode) expr).getValue());
      case GLOBAL_NODE:
        GlobalNode global = (GlobalNode) expr;
        if (global.isResolved()) {
          return getConstantOrNull(global.getValue());
        }
        return null;
      default:
        return null;
    }
  }
}
