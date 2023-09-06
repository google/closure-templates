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

import com.google.common.base.Preconditions;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;

/** Precedence levels as defined in the Soy language parser grammar. */
public enum SoyPrecedence {
  NONE,
  P1,
  P2,
  P3,
  P4,
  P5,
  P6,
  P7,
  P8,
  P9,
  P10,
  P11,
  P12,
  P13,
  PRIMARY;

  public boolean greaterThan(SoyPrecedence other) {
    return this.ordinal() > other.ordinal();
  }

  public boolean lessThan(SoyPrecedence other) {
    return this.ordinal() < other.ordinal();
  }

  public boolean lessThanOrEqual(SoyPrecedence other) {
    return this.ordinal() <= other.ordinal();
  }

  /** Enum for an operator's associativity. */
  public enum Associativity {
    /** Left-to-right. */
    LEFT,
    /** Right-to-left. */
    RIGHT,
    /** Unary, shouldn't matter. */
    NA
  }

  private enum OperandPosition {
    LEFT {
      @Override
      public boolean shouldParenthesize(Associativity operandAssociativity) {
        return operandAssociativity == Associativity.RIGHT;
      }
    },
    RIGHT {
      @Override
      public boolean shouldParenthesize(Associativity operandAssociativity) {
        return operandAssociativity == Associativity.LEFT;
      }
    };

    public abstract boolean shouldParenthesize(Associativity operandAssociativity);
  }

  public static boolean shouldGuard(ParentExprNode parent, ExprNode child) {
    int childIndex = parent.getChildIndex(child);
    Preconditions.checkArgument(childIndex >= 0);
    return shouldGuard(parent, child, childIndex);
  }

  public static boolean shouldGuard(ParentExprNode parent, ExprNode child, int childIndex) {
    switch (parent.getKind()) {
      case EXPR_ROOT_NODE:
      case GROUP_NODE:
      case FUNCTION_NODE:
      case LIST_LITERAL_NODE:
      case MAP_LITERAL_NODE:
      case MAP_LITERAL_FROM_LIST_NODE:
      case LIST_COMPREHENSION_NODE:
      case RECORD_LITERAL_NODE:
        return false;
      case METHOD_CALL_NODE:
      case ITEM_ACCESS_NODE:
        if (childIndex > 0) {
          return false;
        }
        break;
      default:
    }
    OperandPosition operandPosition = getOperandPosition(childIndex);
    return child.getPrecedence().lessThan(parent.getPrecedence())
        || (child.getPrecedence() == parent.getPrecedence()
            && operandPosition.shouldParenthesize(child.getAssociativity()));
  }

  private static OperandPosition getOperandPosition(int childIndex) {
    // Note that this means Soy's ternary is LEFT-RIGHT-RIGHT while JS's is LEFT-LEFT-RIGHT.
    return childIndex == 0 ? OperandPosition.LEFT : OperandPosition.RIGHT;
  }
}
