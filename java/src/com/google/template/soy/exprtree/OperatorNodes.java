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


/**
 * Container of nodes representing operators.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class OperatorNodes {

  private OperatorNodes() {}


  /**
   * Node representing the unary '-' (negative) operator.
   */
  public static class NegativeOpNode extends AbstractOperatorNode {
    @Override public Operator getOperator() {
      return Operator.NEGATIVE;
    }
  }


  /**
   * Node representing the 'not' operator.
   */
  public static class NotOpNode extends AbstractOperatorNode {
    @Override public Operator getOperator() {
      return Operator.NOT;
    }
  }


  /**
   * Node representing the '*' (times) operator.
   */
  public static class TimesOpNode extends AbstractOperatorNode {
    @Override public Operator getOperator() {
      return Operator.TIMES;
    }
  }


  /**
   * Node representing the '/' (divde by) operator.
   */
  public static class DivideByOpNode extends AbstractOperatorNode {
    @Override public Operator getOperator() {
      return Operator.DIVIDE_BY;
    }
  }


  /**
   * Node representing the '%' (mod) operator.
   */
  public static class ModOpNode extends AbstractOperatorNode {
    @Override public Operator getOperator() {
      return Operator.MOD;
    }
  }


  /**
   * Node representing the '+' (plus) operator.
   */
  public static class PlusOpNode extends AbstractOperatorNode {
    @Override public Operator getOperator() {
      return Operator.PLUS;
    }
  }


  /**
   * Node representing the binary '-' (minus) operator.
   */
  public static class MinusOpNode extends AbstractOperatorNode {
    @Override public Operator getOperator() {
      return Operator.MINUS;
    }
  }


  /**
   * Node representing the '&lt;' (less than) operator.
   */
  public static class LessThanOpNode extends AbstractOperatorNode {
    @Override public Operator getOperator() {
      return Operator.LESS_THAN;
    }
  }


  /**
   * Node representing the '&gt;' (greater than) operator.
   */
  public static class GreaterThanOpNode extends AbstractOperatorNode {
    @Override public Operator getOperator() {
      return Operator.GREATER_THAN;
    }
  }


  /**
   * Node representing the '&lt;=' (less than or equal) operator.
   */
  public static class LessThanOrEqualOpNode extends AbstractOperatorNode {
    @Override public Operator getOperator() {
      return Operator.LESS_THAN_OR_EQUAL;
    }
  }


  /**
   * Node representing the '&gt;=' (greater than or equal) operator.
   */
  public static class GreaterThanOrEqualOpNode extends AbstractOperatorNode {
    @Override public Operator getOperator() {
      return Operator.GREATER_THAN_OR_EQUAL;
    }
  }


  /**
   * Node representing the '==' (equal) operator.
   */
  public static class EqualOpNode extends AbstractOperatorNode {
    @Override public Operator getOperator() {
      return Operator.EQUAL;
    }
  }


  /**
   * Node representing the '!=' (not equal) operator.
   */
  public static class NotEqualOpNode extends AbstractOperatorNode {
    @Override public Operator getOperator() {
      return Operator.NOT_EQUAL;
    }
  }


  /**
   * Node representing the 'and' operator.
   */
  public static class AndOpNode extends AbstractOperatorNode {
    @Override public Operator getOperator() {
      return Operator.AND;
    }
  }


  /**
   * Node representing the 'or' operator.
   */
  public static class OrOpNode extends AbstractOperatorNode {
    @Override public Operator getOperator() {
      return Operator.OR;
    }
  }


  /**
   * Node representing the ternary '? :' (conditional) operator.
   */
  public static class ConditionalOpNode extends AbstractOperatorNode {
    @Override public Operator getOperator() {
      return Operator.CONDITIONAL;
    }
  }

}
