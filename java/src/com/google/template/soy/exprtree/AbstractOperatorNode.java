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

import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.Operator.Associativity;
import com.google.template.soy.exprtree.Operator.Operand;
import com.google.template.soy.exprtree.Operator.Spacer;
import com.google.template.soy.exprtree.Operator.SyntaxElement;
import com.google.template.soy.exprtree.Operator.Token;

import java.util.List;


/**
 * Abstract implementation of an OperatorNode.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public abstract class AbstractOperatorNode extends AbstractParentExprNode implements OperatorNode {


  @Override public String toSourceString() {

    Operator op = getOperator();
    boolean isLeftAssociative = op.getAssociativity() == Associativity.LEFT;
    StringBuilder sourceSb = new StringBuilder();

    List<SyntaxElement> syntax = op.getSyntax();
    for (int i = 0, n = syntax.size(); i < n; ++i) {
      SyntaxElement syntaxEl = syntax.get(i);

      if (syntaxEl instanceof Operand) {
        Operand operand = (Operand) syntaxEl;
        // If left (right) associative, first (last) operand doesn't need protection if it's an
        // operator of equal precedence to this one.
        if (i == (isLeftAssociative ? 0 : n-1)) {
          sourceSb.append(getOperandProtectedForLowerPrec(operand.getIndex()));
        } else {
          sourceSb.append(getOperandProtectedForLowerOrEqualPrec(operand.getIndex()));
        }

      } else if (syntaxEl instanceof Token) {
        sourceSb.append(((Token) syntaxEl).getValue());

      } else if (syntaxEl instanceof Spacer) {
        sourceSb.append(' ');

      } else {
        throw new AssertionError();
      }
    }

    return sourceSb.toString();
  }


  /**
   * Gets the source string for the operand at the given index, possibly protected by surrounding
   * parentheses if the operand is an operator with lower precedence than this operator.
   *
   * @param index The index of the operand to get.
   * @return The source string for the operand at the given index, possibly protected by surrounding
   *     parentheses if the operand is an operator with lower precedence than this operator.
   */
  private String getOperandProtectedForLowerPrec(int index) {
    return getOperandProtectedForPrecHelper(index, false);
  }


  /**
   * Gets the source string for the operand at the given index, possibly protected by surrounding
   * parentheses if the operand is an operator with lower or equal precedence to this operator.
   *
   * @param index The index of the operand to get.
   * @return The source string for the operand at the given index, possibly protected by surrounding
   *     parentheses if the operand is an operator with lower or equal precedence to this operator.
   */
  private String getOperandProtectedForLowerOrEqualPrec(int index) {
    return getOperandProtectedForPrecHelper(index, true);
  }


  /**
   * Helper for getOperandProtectedForLowerPrec() and getOperandProtectedForLowerOrEqualPrec().
   * @param index The index of the operand to get.
   * @param shouldProtectEqualPrec Whether to proect the operand if it is an operator with equal
   *     precedence to this operator.
   * @return The source string for the operand at the given index, possibly protected by surrounding
   *     parentheses.
   */
  private String getOperandProtectedForPrecHelper(int index, boolean shouldProtectEqualPrec) {

    int thisOpPrec = this.getOperator().getPrecedence();

    ExprNode child = getChild(index);

    boolean shouldProtect;
    if (child instanceof OperatorNode) {
      int childOpPrec = ((OperatorNode) child).getOperator().getPrecedence();
      shouldProtect = shouldProtectEqualPrec ? childOpPrec <= thisOpPrec
                                             : childOpPrec <  thisOpPrec;
    } else {
      shouldProtect = false;
    }

    if (shouldProtect) {
      return "(" + child.toSourceString() + ")";
    } else {
      return child.toSourceString();
    }
  }

}
