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

import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.internalutils.DataUtils;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ConstantNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FloatNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.shared.internal.NonpluginFunction;
import com.google.template.soy.sharedpasses.render.RenderException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import javax.inject.Inject;


/**
 * Visitor for simplifying expressions based on constant values known at compile time.
 *
 * Package-private helper for {@link SimplifyVisitor}.
 *
 * @author Kai Huang
 */
class SimplifyExprVisitor extends AbstractExprNodeVisitor<Void> {


  /** Empty env used in creating PreevalVisitors for this class. */
  private static final Deque<Map<String, SoyData>> EMPTY_ENV =
      new ArrayDeque<Map<String, SoyData>>(0);


  /** The PreevalVisitor for this instance (can reuse). */
  private final PreevalVisitor preevalVisitor;


  @Inject
  SimplifyExprVisitor(PreevalVisitorFactory preevalVisitorFactory) {
    this.preevalVisitor = preevalVisitorFactory.create(null, EMPTY_ENV);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementation for root node.


  @Override protected void visitExprRootNode(ExprRootNode<?> node) {
    visit(node.getChild(0));
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for collection nodes.


  protected void visitListLiteralNode(ListLiteralNode node) {
    // Visit children only. We cannot simplify the list literal itself.
    visitChildren(node);
  }


  protected void visitMapLiteralNode(MapLiteralNode node) {
    // Visit children only. We cannot simplify the map literal itself.
    visitChildren(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for operators.


  @Override protected void visitAndOpNode(AndOpNode node) {

    // Recurse.
    visitChildren(node);

    // Can simplify if either child is constant. We assume no side-effects.
    SoyData operand0 = getConstantOrNull(node.getChild(0));
    SoyData operand1 = getConstantOrNull(node.getChild(1));
    if (operand0 == null && operand1 == null) {
      return;  // cannot simplify
    }

    ExprNode replacementNode;
    if (operand0 != null && operand1 != null) {
      replacementNode = new BooleanNode(operand0.toBoolean() && operand1.toBoolean());
    } else if (operand0 != null) {
      replacementNode = operand0.toBoolean() ? node.getChild(1) : new BooleanNode(false);
    } else /*(operand1 != null)*/ {
      replacementNode = operand1.toBoolean() ? node.getChild(0) : new BooleanNode(false);
    }

    node.getParent().replaceChild(node, replacementNode);
  }


  @Override protected void visitOrOpNode(OrOpNode node) {

    // Recurse.
    visitChildren(node);

    // Can simplify if either child is constant. We assume no side-effects.
    SoyData operand0 = getConstantOrNull(node.getChild(0));
    SoyData operand1 = getConstantOrNull(node.getChild(1));
    if (operand0 == null && operand1 == null) {
      return;  // cannot simplify
    }

    ExprNode replacementNode;
    if (operand0 != null && operand1 != null) {
      replacementNode = new BooleanNode(operand0.toBoolean() || operand1.toBoolean());
    } else if (operand0 != null) {
      replacementNode = operand0.toBoolean() ? new BooleanNode(true) : node.getChild(1);
    } else /*(operand1 != null)*/ {
      replacementNode = operand1.toBoolean() ? new BooleanNode(true) : node.getChild(0);
    }

    node.getParent().replaceChild(node, replacementNode);
  }


  @Override protected void visitConditionalOpNode(ConditionalOpNode node) {

    // Recurse.
    visitChildren(node);

    // Can simplify if operand0 is constant. We assume no side-effects.
    SoyData operand0 = getConstantOrNull(node.getChild(0));
    if (operand0 == null) {
      return;  // cannot simplify
    }

    ExprNode replacementNode = operand0.toBoolean() ? node.getChild(1) : node.getChild(2);
    node.getParent().replaceChild(node, replacementNode);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for functions.


  @Override protected void visitFunctionNode(FunctionNode node) {

    // Cannot simplify nonplugin functions (this check is needed particularly because of hasData()).
    if (NonpluginFunction.forFunctionName(node.getFunctionName()) != null) {
      return;
    }

    // Default to fallback implementation.
    visitExprNode(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitExprNode(ExprNode node) {

    if (! (node instanceof ParentExprNode)) {
      return;
    }
    ParentExprNode nodeAsParent = (ParentExprNode) node;

    // Recurse.
    visitChildren(nodeAsParent);

    // If all children are constants, we attempt to preevaluate this node and replace it with a
    // constant.
    for (ExprNode child : nodeAsParent.getChildren()) {
      if (! (child instanceof ConstantNode)) {
        return;  // cannot preevaluate
      }
    }

    // Note that we need to catch RenderException because preevaluation may fail, e.g. when
    // (a) the expression uses a bidi function that needs bidiGlobalDir to be in scope, but the
    //     apiCallScope is not currently active,
    // (b) the expression uses an external function (Soy V1 syntax),
    // (c) other cases I haven't thought up.
    SoyData preevalResult;
    try {
      preevalResult = preevalVisitor.exec(nodeAsParent);
    } catch (RenderException e) {
      return;  // failed to preevaluate
    }
    ConstantNode newNode = DataUtils.convertPrimitiveDataToExpr((PrimitiveData) preevalResult);
    nodeAsParent.getParent().replaceChild(nodeAsParent, newNode);
  }


  // -----------------------------------------------------------------------------------------------
  // Helpers.


  private static SoyData getConstantOrNull(ExprNode expr) {

    switch (expr.getKind()) {
      case NULL_NODE: return NullData.INSTANCE;
      case BOOLEAN_NODE: return BooleanData.forValue(((BooleanNode) expr).getValue());
      case INTEGER_NODE: return IntegerData.forValue(((IntegerNode) expr).getValue());
      case FLOAT_NODE: return FloatData.forValue(((FloatNode) expr).getValue());
      case STRING_NODE: return StringData.forValue(((StringNode) expr).getValue());
      default: return null;
    }
  }

}
