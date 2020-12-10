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
import com.google.template.soy.base.SourceLocation.Point;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.basetree.ParentNode;
import com.google.template.soy.types.SoyType;
import java.util.List;
import java.util.Optional;

/**
 * This class defines the base interface for a node in the Soy expression parse tree, as well as a
 * number of subinterfaces that extend the base interface in various aspects. Every concrete node
 * implements some subset of these interfaces.
 *
 * <p>The top level definition is the base ExprNode interface.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public interface ExprNode extends Node {

  /**
   * Enum of specific node kinds (corresponding to specific node types).
   *
   * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
   */
  enum Kind {
    EXPR_ROOT_NODE,

    NULL_NODE,
    BOOLEAN_NODE,
    INTEGER_NODE,
    FLOAT_NODE,
    STRING_NODE,
    PROTO_ENUM_VALUE_NODE,

    LIST_LITERAL_NODE,
    LIST_COMPREHENSION_NODE,
    MAP_LITERAL_NODE,
    RECORD_LITERAL_NODE,

    VAR_REF_NODE,
    FIELD_ACCESS_NODE,
    ITEM_ACCESS_NODE,
    METHOD_CALL_NODE,
    PROTO_EXTENSION_ID_NODE,
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
    EQUAL_OP_NODE,
    NOT_EQUAL_OP_NODE,
    AND_OP_NODE,
    OR_OP_NODE,
    NULL_COALESCING_OP_NODE,
    CONDITIONAL_OP_NODE,
    ASSERT_NON_NULL_OP_NODE,

    FUNCTION_NODE,

    VE_LITERAL_NODE,

    TEMPLATE_LITERAL_NODE,
  }

  /**
   * Gets this node's kind (corresponding to this node's specific type).
   *
   * @return This node's kind (corresponding to this node's specific type).
   */
  public Kind getKind();

  /** Gets the data type of this node. */
  public SoyType getType();

  @Override
  public ParentExprNode getParent();

  /** See {@link Node#copy(CopyState)} for a description of the copy contract. */
  @Override
  public ExprNode copy(CopyState copyState);

  // -----------------------------------------------------------------------------------------------

  /** A node in an expression parse tree that may be a parent. */
  interface ParentExprNode extends ExprNode, ParentNode<ExprNode> {}

  // -----------------------------------------------------------------------------------------------

  /** A node representing an operator (with operands as children). */
  interface OperatorNode extends ParentExprNode {

    public Operator getOperator();

    @Override
    public OperatorNode copy(CopyState copyState);
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
