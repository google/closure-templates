/*
 * Copyright 2023 Google Inc.
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

import com.google.template.soy.exprtree.ExprNode.Kind;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;

/** Utility methods related to expression nodes. */
public final class ExprNodes {
  private ExprNodes() {}

  public static boolean isNullishLiteral(ExprNode node) {
    return node.getKind() == ExprNode.Kind.NULL_NODE
        || node.getKind() == ExprNode.Kind.UNDEFINED_NODE;
  }

  public static boolean isNonUndefinedLiteral(ExprNode node) {
    return node.getKind() != Kind.UNDEFINED_NODE && isNonNullishLiteral(node);
  }

  public static boolean isNonNullishLiteral(ExprNode node) {
    switch (node.getKind()) {
        // Actual nullish
      case NULL_NODE:
      case UNDEFINED_NODE:
        // No guarantees
      case VAR_REF_NODE:
      case METHOD_CALL_NODE:
      case FUNCTION_NODE:
      case ITEM_ACCESS_NODE:
      case FIELD_ACCESS_NODE:
      case GLOBAL_NODE:
      case NULL_SAFE_ACCESS_NODE:
        return false;
        // Actual literals
      case FLOAT_NODE:
      case INTEGER_NODE:
      case STRING_NODE:
      case BOOLEAN_NODE:
      case MAP_LITERAL_NODE:
      case LIST_LITERAL_NODE:
      case LIST_COMPREHENSION_NODE:
      case PROTO_ENUM_VALUE_NODE:
      case RECORD_LITERAL_NODE:
        // Expressions that can't evaluate to null
      case BITWISE_AND_OP_NODE:
      case BITWISE_OR_OP_NODE:
      case BITWISE_XOR_OP_NODE:
      case SHIFT_LEFT_OP_NODE:
      case SHIFT_RIGHT_OP_NODE:
      case GREATER_THAN_OP_NODE:
      case GREATER_THAN_OR_EQUAL_OP_NODE:
      case LESS_THAN_OP_NODE:
      case LESS_THAN_OR_EQUAL_OP_NODE:
      case EQUAL_OP_NODE:
      case NOT_EQUAL_OP_NODE:
      case TRIPLE_EQUAL_OP_NODE:
      case TRIPLE_NOT_EQUAL_OP_NODE:
      case NOT_OP_NODE:
        return true;
      case NULL_COALESCING_OP_NODE:
        return isNonNullishLiteral(((ParentExprNode) node).getChild(1));
      default:
        return false;
    }
  }

  public static boolean isNonFalsyLiteral(ExprNode node) {
    switch (node.getKind()) {
      case INTEGER_NODE:
        return ((IntegerNode) node).getValue() != 0;
      case FLOAT_NODE:
        return ((FloatNode) node).getValue() != 0.0;
      case BOOLEAN_NODE:
        return ((BooleanNode) node).getValue();
      case STRING_NODE:
        return !((StringNode) node).getValue().isEmpty();
      default:
        return isNonNullishLiteral(node);
    }
  }
}
