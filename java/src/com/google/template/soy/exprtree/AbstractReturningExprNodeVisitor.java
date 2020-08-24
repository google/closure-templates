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

package com.google.template.soy.exprtree;

import com.google.template.soy.basetree.AbstractReturningNodeVisitor;
import com.google.template.soy.basetree.ParentNode;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.AssertNonNullOpNode;
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
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TimesOpNode;
import java.util.List;

/**
 * Abstract base class for all ExprNode visitors. A visitor is basically a function implemented for
 * some or all ExprNodes, where the implementation can be different for each specific node class.
 *
 * <p>Same as {@link AbstractExprNodeVisitor} except that in this class, internal {@code visit()}
 * calls return a value.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p>To create a visitor:
 *
 * <ol>
 *   <li> Subclass this class.
 *   <li> Implement {@code visit*Node()} methods for some specific node types.
 *   <li> Implement fallback methods for node types not specifically handled. The most general
 *       fallback method is {@link #visitExprNode visitExprNode()}, which is usually needed. Other
 *       fallback methods include {@code visitPrimitiveNode()} and {@code visitOperatorNode()}.
 *   <li> Maybe implement a constructor, taking appropriate parameters for your visitor call.
 *   <li> Maybe implement {@link #exec exec()} if this visitor needs to return a non-null final
 *       result and/or if this visitor has state that needs to be setup/reset before each unrelated
 *       use of {@code visit()}.
 * </ol>
 *
 * @param <R> The return type of this visitor.
 * @see AbstractExprNodeVisitor
 */
public abstract class AbstractReturningExprNodeVisitor<R>
    extends AbstractReturningNodeVisitor<ExprNode, R> {

  @Override
  protected R visit(ExprNode node) {

    switch (node.getKind()) {
      case EXPR_ROOT_NODE:
        return visitExprRootNode((ExprRootNode) node);

      case NULL_NODE:
        return visitNullNode((NullNode) node);
      case BOOLEAN_NODE:
        return visitBooleanNode((BooleanNode) node);
      case INTEGER_NODE:
        return visitIntegerNode((IntegerNode) node);
      case FLOAT_NODE:
        return visitFloatNode((FloatNode) node);
      case STRING_NODE:
        return visitStringNode((StringNode) node);

      case LIST_LITERAL_NODE:
        return visitListLiteralNode((ListLiteralNode) node);
      case LIST_COMPREHENSION_NODE:
        return visitListComprehensionNode((ListComprehensionNode) node);
      case MAP_LITERAL_NODE:
        return visitMapLiteralNode((MapLiteralNode) node);
      case RECORD_LITERAL_NODE:
        return visitRecordLiteralNode((RecordLiteralNode) node);

      case VAR_REF_NODE:
        return visitVarRefNode((VarRefNode) node);
      case FIELD_ACCESS_NODE:
        return visitFieldAccessNode((FieldAccessNode) node);
      case ITEM_ACCESS_NODE:
        return visitItemAccessNode((ItemAccessNode) node);
      case METHOD_CALL_NODE:
        return visitMethodCallNode((MethodCallNode) node);
      case NULL_SAFE_ACCESS_NODE:
        return visitNullSafeAccessNode((NullSafeAccessNode) node);

      case GLOBAL_NODE:
        return visitGlobalNode((GlobalNode) node);

      case GROUP_NODE:
        return visitGroupNode((GroupNode) node);

      case NEGATIVE_OP_NODE:
        return visitNegativeOpNode((NegativeOpNode) node);
      case NOT_OP_NODE:
        return visitNotOpNode((NotOpNode) node);
      case ASSERT_NON_NULL_OP_NODE:
        return visitAssertNonNullOpNode((AssertNonNullOpNode) node);
      case TIMES_OP_NODE:
        return visitTimesOpNode((TimesOpNode) node);
      case DIVIDE_BY_OP_NODE:
        return visitDivideByOpNode((DivideByOpNode) node);
      case MOD_OP_NODE:
        return visitModOpNode((ModOpNode) node);
      case PLUS_OP_NODE:
        return visitPlusOpNode((PlusOpNode) node);
      case MINUS_OP_NODE:
        return visitMinusOpNode((MinusOpNode) node);
      case LESS_THAN_OP_NODE:
        return visitLessThanOpNode((LessThanOpNode) node);
      case GREATER_THAN_OP_NODE:
        return visitGreaterThanOpNode((GreaterThanOpNode) node);
      case LESS_THAN_OR_EQUAL_OP_NODE:
        return visitLessThanOrEqualOpNode((LessThanOrEqualOpNode) node);
      case GREATER_THAN_OR_EQUAL_OP_NODE:
        return visitGreaterThanOrEqualOpNode((GreaterThanOrEqualOpNode) node);
      case EQUAL_OP_NODE:
        return visitEqualOpNode((EqualOpNode) node);
      case NOT_EQUAL_OP_NODE:
        return visitNotEqualOpNode((NotEqualOpNode) node);
      case AND_OP_NODE:
        return visitAndOpNode((AndOpNode) node);
      case OR_OP_NODE:
        return visitOrOpNode((OrOpNode) node);
      case NULL_COALESCING_OP_NODE:
        return visitNullCoalescingOpNode((NullCoalescingOpNode) node);
      case CONDITIONAL_OP_NODE:
        return visitConditionalOpNode((ConditionalOpNode) node);

      case FUNCTION_NODE:
        return visitFunctionNode((FunctionNode) node);

      case VE_LITERAL_NODE:
        return visitVeLiteralNode((VeLiteralNode) node);
      case TEMPLATE_LITERAL_NODE:
        return visitTemplateLiteralNode((TemplateLiteralNode) node);

      default:
        throw new UnsupportedOperationException();
    }
  }

  /**
   * Helper to visit all the children of a node, in order.
   *
   * @param node The parent node whose children to visit.
   * @return The list of return values from visiting the children.
   * @see #visitChildrenAllowingConcurrentModification
   */
  protected List<R> visitChildren(ParentExprNode node) {
    return visitChildren((ParentNode<ExprNode>) node);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for misc nodes.

  protected R visitExprRootNode(ExprRootNode node) {
    return visitExprNode(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for primitive nodes.

  protected R visitNullNode(NullNode node) {
    return visitPrimitiveNode(node);
  }

  protected R visitBooleanNode(BooleanNode node) {
    return visitPrimitiveNode(node);
  }

  protected R visitIntegerNode(IntegerNode node) {
    return visitPrimitiveNode(node);
  }

  protected R visitFloatNode(FloatNode node) {
    return visitPrimitiveNode(node);
  }

  protected R visitStringNode(StringNode node) {
    return visitPrimitiveNode(node);
  }

  protected R visitPrimitiveNode(PrimitiveNode node) {
    return visitExprNode(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for collection nodes.

  protected R visitListLiteralNode(ListLiteralNode node) {
    return visitExprNode(node);
  }

  protected R visitListComprehensionNode(ListComprehensionNode node) {
    return visitExprNode(node);
  }

  protected R visitRecordLiteralNode(RecordLiteralNode node) {
    return visitExprNode(node);
  }

  protected R visitMapLiteralNode(MapLiteralNode node) {
    return visitExprNode(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for reference nodes.

  protected R visitVarRefNode(VarRefNode node) {
    return visitExprNode(node);
  }

  protected R visitDataAccessNode(DataAccessNode node) {
    return visitExprNode(node);
  }

  protected R visitFieldAccessNode(FieldAccessNode node) {
    return visitDataAccessNode(node);
  }

  protected R visitItemAccessNode(ItemAccessNode node) {
    return visitDataAccessNode(node);
  }

  protected R visitMethodCallNode(MethodCallNode node) {
    return visitDataAccessNode(node);
  }

  protected R visitNullSafeAccessNode(NullSafeAccessNode node) {
    return visitExprNode(node);
  }

  protected R visitGlobalNode(GlobalNode node) {
    return visitExprNode(node);
  }

  protected R visitGroupNode(GroupNode node) {
    return visitExprNode(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for operator nodes.

  protected R visitNegativeOpNode(NegativeOpNode node) {
    return visitOperatorNode(node);
  }

  protected R visitNotOpNode(NotOpNode node) {
    return visitOperatorNode(node);
  }

  protected R visitAssertNonNullOpNode(AssertNonNullOpNode node) {
    return visitOperatorNode(node);
  }

  protected R visitTimesOpNode(TimesOpNode node) {
    return visitOperatorNode(node);
  }

  protected R visitDivideByOpNode(DivideByOpNode node) {
    return visitOperatorNode(node);
  }

  protected R visitModOpNode(ModOpNode node) {
    return visitOperatorNode(node);
  }

  protected R visitPlusOpNode(PlusOpNode node) {
    return visitOperatorNode(node);
  }

  protected R visitMinusOpNode(MinusOpNode node) {
    return visitOperatorNode(node);
  }

  protected R visitLessThanOpNode(LessThanOpNode node) {
    return visitOperatorNode(node);
  }

  protected R visitGreaterThanOpNode(GreaterThanOpNode node) {
    return visitOperatorNode(node);
  }

  protected R visitLessThanOrEqualOpNode(LessThanOrEqualOpNode node) {
    return visitOperatorNode(node);
  }

  protected R visitGreaterThanOrEqualOpNode(GreaterThanOrEqualOpNode node) {
    return visitOperatorNode(node);
  }

  protected R visitEqualOpNode(EqualOpNode node) {
    return visitOperatorNode(node);
  }

  protected R visitNotEqualOpNode(NotEqualOpNode node) {
    return visitOperatorNode(node);
  }

  protected R visitAndOpNode(AndOpNode node) {
    return visitOperatorNode(node);
  }

  protected R visitOrOpNode(OrOpNode node) {
    return visitOperatorNode(node);
  }

  protected R visitConditionalOpNode(ConditionalOpNode node) {
    return visitOperatorNode(node);
  }

  protected R visitNullCoalescingOpNode(NullCoalescingOpNode node) {
    return visitOperatorNode(node);
  }

  protected R visitOperatorNode(OperatorNode node) {
    return visitExprNode(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for function nodes.

  protected R visitFunctionNode(FunctionNode node) {
    return visitExprNode(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for ve nodes.

  protected R visitVeLiteralNode(VeLiteralNode node) {
    return visitExprNode(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for template nodes.
  protected R visitTemplateLiteralNode(TemplateLiteralNode node) {
    return visitExprNode(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  /** @param node The node being visited. */
  protected R visitExprNode(ExprNode node) {
    throw new UnsupportedOperationException("no implementation for: " + node);
  }
}
