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

package com.google.template.soy.exprtree;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.basetree.AbstractNodeVisitor;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.DivideByOpNode;
import com.google.template.soy.exprtree.OperatorNodes.EqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.MinusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ModOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NegativeOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TimesOpNode;

import java.util.List;


/**
 * Abstract base class for all ExprNode visitors. A visitor is basically a function implemented for
 * some or all ExprNodes, where the implementation can be different for each specific node class.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <ul>
 * <li> Sets up calling the appropriate {@code visitInternal()} method via reflection.
 * <li> Sets up defaulting to trying interface implementations for a given node class (if the
 *      specific node class does not have {@code visitInternal()} defined).
 * </ul>
 *
 * <p>
 * To create a visitor:
 * <ol>
 * <li> Subclass this class.
 * <li> Implement visitInternal() methods for some specific ExprNode classes and perhaps for some
 *      interfaces as well.
 *      <p> This is what happens when visit() is called on a node:
 *      <ul>
 *      <li> if the specific visitInternal() for that node class is implemented, then it is called,
 *      <li> else if one of the node class's interfaces has an implemented visitInternal(), then
 *           that method is called (with ties broken by approximate specificity of the interface)
 *      <li> else an UnsupportedOperationException is thrown.
 *      </ul>
 * <li> Implement a constructor (taking appropriate parameters for your visitor call), and perhaps
 *      implement a getResult() method or something similar for retrieving the result of the call.
 * <li> Implement reset().
 * </ol>
 *
 * @param <R> The return type.
 *
 * @author Kai Huang
 */
public abstract class AbstractExprNodeVisitor<R> extends AbstractNodeVisitor<ExprNode, R> {


  /** All concrete ExprNode classes. */
  @SuppressWarnings("unchecked")  // varargs
  private static final List<Class<? extends ExprNode>> EXPR_NODE_CLASSES =
      ImmutableList.<Class<? extends ExprNode>>of(
          ExprRootNode.class,
          NullNode.class, BooleanNode.class, IntegerNode.class, FloatNode.class, StringNode.class,
          VarNode.class, DataRefNode.class, DataRefKeyNode.class, DataRefIndexNode.class,
          GlobalNode.class,
          NegativeOpNode.class, NotOpNode.class,
          TimesOpNode.class, DivideByOpNode.class, ModOpNode.class,
          PlusOpNode.class, MinusOpNode.class,
          LessThanOpNode.class, GreaterThanOpNode.class,
          LessThanOrEqualOpNode.class, GreaterThanOrEqualOpNode.class,
          EqualOpNode.class, NotEqualOpNode.class,
          AndOpNode.class, OrOpNode.class, ConditionalOpNode.class,
          FunctionNode.class);

  /** ExprNode interfaces in approximate order of specificity. */
  @SuppressWarnings("unchecked")  // varargs
  private static final List<Class<? extends ExprNode>> EXPR_NODE_INTERFACES =
      ImmutableList.<Class<? extends ExprNode>>of(
          PrimitiveNode.class, OperatorNode.class, ParentExprNode.class, ExprNode.class);


  public AbstractExprNodeVisitor() {
    super(EXPR_NODE_CLASSES, EXPR_NODE_INTERFACES);
  }


  /**
   * Helper to visit all the children of a node, in order.
   * @param node The parent node whose children to visit.
   */
  protected void visitChildren(ParentExprNode node) {
    for (ExprNode child : node.getChildren()) {
      visit(child);
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Implementation for a dummy root node.


  protected void visitInternal(ExprRootNode<? extends ExprNode> node) {}


  // -----------------------------------------------------------------------------------------------
  // Implementations for primitives and data references (concrete classes).


  protected void visitInternal(NullNode node) {}

  protected void visitInternal(BooleanNode node) {}

  protected void visitInternal(IntegerNode node) {}

  protected void visitInternal(FloatNode node) {}

  protected void visitInternal(StringNode node) {}

  protected void visitInternal(VarNode node) {}

  protected void visitInternal(DataRefNode node) {}

  protected void visitInternal(DataRefKeyNode node) {}

  protected void visitInternal(DataRefIndexNode node) {}

  protected void visitInternal(GlobalNode node) {}


  // -----------------------------------------------------------------------------------------------
  // Implementations for operators (concrete classes).


  protected void visitInternal(NegativeOpNode node) {}

  protected void visitInternal(NotOpNode node) {}

  protected void visitInternal(TimesOpNode node) {}

  protected void visitInternal(DivideByOpNode node) {}

  protected void visitInternal(ModOpNode node) {}

  protected void visitInternal(PlusOpNode node) {}

  protected void visitInternal(MinusOpNode node) {}

  protected void visitInternal(LessThanOpNode node) {}

  protected void visitInternal(GreaterThanOpNode node) {}

  protected void visitInternal(LessThanOrEqualOpNode node) {}

  protected void visitInternal(GreaterThanOrEqualOpNode node) {}

  protected void visitInternal(EqualOpNode node) {}

  protected void visitInternal(NotEqualOpNode node) {}

  protected void visitInternal(AndOpNode node) {}

  protected void visitInternal(OrOpNode node) {}

  protected void visitInternal(ConditionalOpNode node) {}


  // -----------------------------------------------------------------------------------------------
  // Implementation for functions.


  protected void visitInternal(FunctionNode node) {}


  // -----------------------------------------------------------------------------------------------
  // Implementations for interfaces.


  protected void visitInternal(ExprNode node) {}

  protected void visitInternal(ParentExprNode node) {}

  protected void visitInternal(OperatorNode node) {}

  protected void visitInternal(PrimitiveNode node) {}

}
