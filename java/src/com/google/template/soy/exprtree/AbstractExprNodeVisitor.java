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

import com.google.template.soy.basetree.AbstractNodeVisitor;
import com.google.template.soy.basetree.ParentNode;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.OperatorNodes.AmpAmpOpNode;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.AsOpNode;
import com.google.template.soy.exprtree.OperatorNodes.AssertNonNullOpNode;
import com.google.template.soy.exprtree.OperatorNodes.BarBarOpNode;
import com.google.template.soy.exprtree.OperatorNodes.BitwiseAndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.BitwiseOrOpNode;
import com.google.template.soy.exprtree.OperatorNodes.BitwiseXorOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.DivideByOpNode;
import com.google.template.soy.exprtree.OperatorNodes.EqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.InstanceOfOpNode;
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
import com.google.template.soy.exprtree.OperatorNodes.ShiftLeftOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ShiftRightOpNode;
import com.google.template.soy.exprtree.OperatorNodes.SpreadOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TimesOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TripleEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TripleNotEqualOpNode;

/**
 * Abstract base class for all ExprNode visitors. A visitor is basically a function implemented for
 * some or all ExprNodes, where the implementation can be different for each specific node class.
 *
 * <p>Same as {@link AbstractReturningExprNodeVisitor} except that in this class, internal {@code
 * visit()} calls do not return a value.
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
 * @see AbstractReturningExprNodeVisitor
 */
public abstract class AbstractExprNodeVisitor<R> extends AbstractNodeVisitor<ExprNode, R> {

  @Override
  protected final void visit(ExprNode node) {

    switch (node.getKind()) {
      case EXPR_ROOT_NODE:
        visitExprRootNode((ExprRootNode) node);
        break;

      case NULL_NODE:
        visitNullNode((NullNode) node);
        break;
      case UNDEFINED_NODE:
        visitUndefinedNode((UndefinedNode) node);
        break;
      case BOOLEAN_NODE:
        visitBooleanNode((BooleanNode) node);
        break;
      case INTEGER_NODE:
        visitIntegerNode((IntegerNode) node);
        break;
      case FLOAT_NODE:
        visitFloatNode((FloatNode) node);
        break;
      case STRING_NODE:
        visitStringNode((StringNode) node);
        break;
      case PROTO_ENUM_VALUE_NODE:
        visitProtoEnumValueNode((ProtoEnumValueNode) node);
        break;
      case TYPE_LITERAL_NODE:
        visitTypeLiteralNode((TypeLiteralNode) node);
        break;

      case LIST_LITERAL_NODE:
        visitListLiteralNode((ListLiteralNode) node);
        break;
      case LIST_COMPREHENSION_NODE:
        visitListComprehensionNode((ListComprehensionNode) node);
        break;
      case MAP_LITERAL_NODE:
        visitMapLiteralNode((MapLiteralNode) node);
        break;
      case MAP_LITERAL_FROM_LIST_NODE:
        visitMapLiteralFromListNode((MapLiteralFromListNode) node);
        break;
      case RECORD_LITERAL_NODE:
        visitRecordLiteralNode((RecordLiteralNode) node);
        break;

      case VAR_REF_NODE:
        visitVarRefNode((VarRefNode) node);
        break;
      case FIELD_ACCESS_NODE:
        visitFieldAccessNode((FieldAccessNode) node);
        break;
      case ITEM_ACCESS_NODE:
        visitItemAccessNode((ItemAccessNode) node);
        break;
      case METHOD_CALL_NODE:
        visitMethodCallNode((MethodCallNode) node);
        break;
      case NULL_SAFE_ACCESS_NODE:
        visitNullSafeAccessNode((NullSafeAccessNode) node);
        break;

      case GLOBAL_NODE:
        visitGlobalNode((GlobalNode) node);
        break;
      case GROUP_NODE:
        visitGroupNode((GroupNode) node);
        break;
      case NEGATIVE_OP_NODE:
        visitNegativeOpNode((NegativeOpNode) node);
        break;
      case NOT_OP_NODE:
        visitNotOpNode((NotOpNode) node);
        break;
      case TIMES_OP_NODE:
        visitTimesOpNode((TimesOpNode) node);
        break;
      case DIVIDE_BY_OP_NODE:
        visitDivideByOpNode((DivideByOpNode) node);
        break;
      case MOD_OP_NODE:
        visitModOpNode((ModOpNode) node);
        break;
      case PLUS_OP_NODE:
        visitPlusOpNode((PlusOpNode) node);
        break;
      case MINUS_OP_NODE:
        visitMinusOpNode((MinusOpNode) node);
        break;
      case LESS_THAN_OP_NODE:
        visitLessThanOpNode((LessThanOpNode) node);
        break;
      case GREATER_THAN_OP_NODE:
        visitGreaterThanOpNode((GreaterThanOpNode) node);
        break;
      case LESS_THAN_OR_EQUAL_OP_NODE:
        visitLessThanOrEqualOpNode((LessThanOrEqualOpNode) node);
        break;
      case GREATER_THAN_OR_EQUAL_OP_NODE:
        visitGreaterThanOrEqualOpNode((GreaterThanOrEqualOpNode) node);
        break;
      case EQUAL_OP_NODE:
        visitEqualOpNode((EqualOpNode) node);
        break;
      case NOT_EQUAL_OP_NODE:
        visitNotEqualOpNode((NotEqualOpNode) node);
        break;
      case TRIPLE_EQUAL_OP_NODE:
        visitTripleEqualOpNode((TripleEqualOpNode) node);
        break;
      case TRIPLE_NOT_EQUAL_OP_NODE:
        visitTripleNotEqualOpNode((TripleNotEqualOpNode) node);
        break;
      case AND_OP_NODE:
        visitAndOpNode((AndOpNode) node);
        break;
      case AMP_AMP_OP_NODE:
        visitAmpAmpOpNode((AmpAmpOpNode) node);
        break;
      case OR_OP_NODE:
        visitOrOpNode((OrOpNode) node);
        break;
      case BAR_BAR_OP_NODE:
        visitBarBarOpNode((BarBarOpNode) node);
        break;
      case NULL_COALESCING_OP_NODE:
        visitNullCoalescingOpNode((NullCoalescingOpNode) node);
        break;
      case CONDITIONAL_OP_NODE:
        visitConditionalOpNode((ConditionalOpNode) node);
        break;
      case SHIFT_LEFT_OP_NODE:
        visitShiftLeftOpNode((ShiftLeftOpNode) node);
        break;
      case SHIFT_RIGHT_OP_NODE:
        visitShiftRightOpNode((ShiftRightOpNode) node);
        break;
      case BITWISE_OR_OP_NODE:
        visitBitwiseOrOpNode((BitwiseOrOpNode) node);
        break;
      case BITWISE_XOR_OP_NODE:
        visitBitwiseXorOpNode((BitwiseXorOpNode) node);
        break;
      case BITWISE_AND_OP_NODE:
        visitBitwiseAndOpNode((BitwiseAndOpNode) node);
        break;
      case SPREAD_OP_NODE:
        visitSpreadOpNode((SpreadOpNode) node);
        break;
      case INSTANCE_OF_OP_NODE:
        visitInstanceOfOpNode((InstanceOfOpNode) node);
        break;
      case AS_OP_NODE:
        visitAsOpNode((AsOpNode) node);
        break;

      case FUNCTION_NODE:
        visitFunctionNode((FunctionNode) node);
        break;

      case TEMPLATE_LITERAL_NODE:
        visitTemplateLiteralNode((TemplateLiteralNode) node);
        break;

      case ASSERT_NON_NULL_OP_NODE:
        visitAssertNonNullOpNode((AssertNonNullOpNode) node);
        break;

    }
  }

  /**
   * Helper to visit all the children of a node, in order.
   *
   * @param node The parent node whose children to visit.
   * @see #visitChildrenAllowingConcurrentModification
   */
  protected void visitChildren(ParentExprNode node) {
    visitChildren((ParentNode<ExprNode>) node);
  }

  /**
   * Helper to visit all the children of a node, in order.
   *
   * <p>This method differs from {@code visitChildren} in that we are iterating through a copy of
   * the children. Thus, concurrent modification of the list of children is allowed.
   *
   * @param node The parent node whose children to visit.
   * @see #visitChildren
   */
  protected void visitChildrenAllowingConcurrentModification(ParentExprNode node) {
    visitChildrenAllowingConcurrentModification((ParentNode<ExprNode>) node);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for misc nodes.

  protected void visitExprRootNode(ExprRootNode node) {
    visitExprNode(node);
  }

  protected void visitAssertNonNullOpNode(AssertNonNullOpNode node) {
    visitExprNode(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for primitive nodes.

  protected void visitNullNode(NullNode node) {
    visitPrimitiveNode(node);
  }

  protected void visitUndefinedNode(UndefinedNode node) {
    visitPrimitiveNode(node);
  }

  protected void visitBooleanNode(BooleanNode node) {
    visitPrimitiveNode(node);
  }

  protected void visitIntegerNode(IntegerNode node) {
    visitPrimitiveNode(node);
  }

  protected void visitFloatNode(FloatNode node) {
    visitPrimitiveNode(node);
  }

  protected void visitStringNode(StringNode node) {
    visitPrimitiveNode(node);
  }

  protected void visitProtoEnumValueNode(ProtoEnumValueNode node) {
    visitPrimitiveNode(node);
  }

  protected void visitTypeLiteralNode(TypeLiteralNode node) {
    visitPrimitiveNode(node);
  }

  protected void visitPrimitiveNode(PrimitiveNode node) {
    visitExprNode(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for collection nodes.

  protected void visitListLiteralNode(ListLiteralNode node) {
    visitExprNode(node);
  }

  protected void visitListComprehensionNode(ListComprehensionNode node) {
    visitExprNode(node);
  }

  protected void visitMapLiteralNode(MapLiteralNode node) {
    visitExprNode(node);
  }

  protected void visitMapLiteralFromListNode(MapLiteralFromListNode node) {
    visitExprNode(node);
  }

  protected void visitRecordLiteralNode(RecordLiteralNode node) {
    visitExprNode(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for reference nodes.

  protected void visitVarRefNode(VarRefNode node) {
    visitExprNode(node);
  }

  protected void visitDataAccessNode(DataAccessNode node) {
    visitExprNode(node);
  }

  protected void visitFieldAccessNode(FieldAccessNode node) {
    visitDataAccessNode(node);
  }

  protected void visitItemAccessNode(ItemAccessNode node) {
    visitDataAccessNode(node);
  }

  protected void visitMethodCallNode(MethodCallNode node) {
    visitDataAccessNode(node);
  }

  protected void visitNullSafeAccessNode(NullSafeAccessNode node) {
    visitExprNode(node);
  }

  protected void visitGlobalNode(GlobalNode node) {
    visitExprNode(node);
  }

  protected void visitGroupNode(GroupNode node) {
    visitExprNode(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for operator nodes.

  protected void visitNegativeOpNode(NegativeOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitNotOpNode(NotOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitTimesOpNode(TimesOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitDivideByOpNode(DivideByOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitModOpNode(ModOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitPlusOpNode(PlusOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitMinusOpNode(MinusOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitLessThanOpNode(LessThanOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitGreaterThanOpNode(GreaterThanOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitLessThanOrEqualOpNode(LessThanOrEqualOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitGreaterThanOrEqualOpNode(GreaterThanOrEqualOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitEqualOpNode(EqualOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitNotEqualOpNode(NotEqualOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitTripleEqualOpNode(TripleEqualOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitTripleNotEqualOpNode(TripleNotEqualOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitAndOpNode(AndOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitAmpAmpOpNode(AmpAmpOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitOrOpNode(OrOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitBarBarOpNode(BarBarOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitNullCoalescingOpNode(NullCoalescingOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitConditionalOpNode(ConditionalOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitShiftLeftOpNode(ShiftLeftOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitShiftRightOpNode(ShiftRightOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitBitwiseOrOpNode(BitwiseOrOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitBitwiseXorOpNode(BitwiseXorOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitBitwiseAndOpNode(BitwiseAndOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitSpreadOpNode(SpreadOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitInstanceOfOpNode(InstanceOfOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitAsOpNode(AsOpNode node) {
    visitOperatorNode(node);
  }

  protected void visitOperatorNode(OperatorNode node) {
    visitExprNode(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for function nodes.

  protected void visitFunctionNode(FunctionNode node) {
    visitExprNode(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for template literal nodes.

  protected void visitTemplateLiteralNode(TemplateLiteralNode node) {
    visitExprNode(node);
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  /** @param node The node being visited. */
  protected void visitExprNode(ExprNode node) {
    throw new UnsupportedOperationException("no implementation for: " + node);
  }
}
