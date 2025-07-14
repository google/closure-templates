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
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SourceLocation.Point;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.basetree.ParentNode;
import com.google.template.soy.exprtree.SoyPrecedence.Associativity;
import com.google.template.soy.types.SoyType;
import java.util.List;
import java.util.Optional;

/**
 * This class defines the base interface for a node in the Soy expression parse tree, as well as a
 * number of subinterfaces that extend the base interface in various aspects. Every concrete node
 * implements some subset of these interfaces.
 *
 * <p>The top level definition is the base ExprNode interface.
 */
public interface ExprNode extends Node {

  /** Enum of specific node kinds (corresponding to specific node types). */
  enum Kind {
    EXPR_ROOT_NODE,

    NULL_NODE,
    UNDEFINED_NODE,
    BOOLEAN_NODE,
    NUMBER_NODE,
    STRING_NODE,
    PROTO_ENUM_VALUE_NODE,
    TYPE_LITERAL_NODE,

    LIST_LITERAL_NODE,
    LIST_COMPREHENSION_NODE,
    MAP_LITERAL_NODE,
    MAP_LITERAL_FROM_LIST_NODE,
    RECORD_LITERAL_NODE,

    VAR_REF_NODE,
    FIELD_ACCESS_NODE,
    ITEM_ACCESS_NODE,
    METHOD_CALL_NODE,
    NULL_SAFE_ACCESS_NODE,

    GLOBAL_NODE,
    GROUP_NODE,

    NEGATIVE_OP_NODE,
    NOT_OP_NODE,
    TIMES_OP_NODE,
    DIVIDE_BY_OP_NODE,
    MOD_OP_NODE,
    PLUS_OP_NODE,
    MINUS_OP_NODE,
    LESS_THAN_OP_NODE,
    GREATER_THAN_OP_NODE,
    LESS_THAN_OR_EQUAL_OP_NODE,
    GREATER_THAN_OR_EQUAL_OP_NODE,
    INSTANCE_OF_OP_NODE,
    EQUAL_OP_NODE,
    NOT_EQUAL_OP_NODE,
    TRIPLE_EQUAL_OP_NODE,
    TRIPLE_NOT_EQUAL_OP_NODE,
    AMP_AMP_OP_NODE,
    BAR_BAR_OP_NODE,
    NULL_COALESCING_OP_NODE,
    CONDITIONAL_OP_NODE,
    ASSERT_NON_NULL_OP_NODE,
    SHIFT_LEFT_OP_NODE,
    SHIFT_RIGHT_OP_NODE,
    BITWISE_OR_OP_NODE,
    BITWISE_XOR_OP_NODE,
    BITWISE_AND_OP_NODE,
    SPREAD_OP_NODE,
    AS_OP_NODE,

    FUNCTION_NODE,

    TEMPLATE_LITERAL_NODE,
  }

  /**
   * Gets this node's kind (corresponding to this node's specific type).
   *
   * @return This node's kind (corresponding to this node's specific type).
   */
  @Override
  Kind getKind();

  /**
   * Returns the type of the node as it was authored (before calling {@link
   * SoyType#getEffectiveType()}.
   */
  SoyType getType();

  @Override
  ParentExprNode getParent();

  /** See {@link Node#copy(CopyState)} for a description of the copy contract. */
  @Override
  ExprNode copy(CopyState copyState);

  /** Returns the Soy operator precedence of this node. */
  default SoyPrecedence getPrecedence() {
    return SoyPrecedence.PRIMARY;
  }

  /** Returns the Soy operator associativity of this node. */
  default SoyPrecedence.Associativity getAssociativity() {
    return Associativity.LEFT;
  }

  /** Returns true if this node was inside a {@link GroupNode} in the original source. */
  boolean isDesugaredGroup();

  /** A "set-once" value called by {@link com.google.template.soy.passes.DesugarGroupNodesPass}. */
  void setDesugaredGroup(boolean groupedInSource);

  // -----------------------------------------------------------------------------------------------

  /** A node in an expression parse tree that may be a parent. */
  interface ParentExprNode extends ExprNode, ParentNode<ExprNode> {}

  // -----------------------------------------------------------------------------------------------

  /** A node representing an operator (with operands as children). */
  interface OperatorNode extends ParentExprNode {

    Operator getOperator();

    @Override
    OperatorNode copy(CopyState copyState);

    @Override
    default SoyPrecedence getPrecedence() {
      return getOperator().getPrecedence();
    }

    @Override
    default Associativity getAssociativity() {
      return getOperator().getAssociativity();
    }

    SourceLocation getOperatorLocation();
  }

  // -----------------------------------------------------------------------------------------------

  /** A node representing a primitive literal. */
  interface PrimitiveNode extends ExprNode {
    @Override
    PrimitiveNode copy(CopyState copyState);
  }

  /** A marker interface for nodes that can be part of access chains, like {@code $r.a[b]!}. */
  interface AccessChainComponentNode extends ParentExprNode {}

  /** Common interface for nodes that represent a function or method call. */
  interface CallableExpr extends ExprNode {

    /** The source positions of commas. Sometimes available to aid the formatter. */
    Optional<ImmutableList<Point>> getCommaLocations();

    /** The ordered list of parameter values. */
    List<ExprNode> getParams();

    default ExprNode getParam(int index) {
      return getParams().get(index);
    }

    /** The number of parameter values. */
    int numParams();

    /** The call style used. */
    ParamsStyle getParamsStyle();

    /** The name of the function/method called. */
    Identifier getIdentifier();

    /** The ordered list of parameter names, if call style is NAMED. */
    ImmutableList<Identifier> getParamNames();

    default Identifier getParamName(int i) {
      return getParamNames().get(i);
    }

    /** How parameters are passed to the call. */
    enum ParamsStyle {
      NONE,
      POSITIONAL,
      NAMED
    }
  }
}
