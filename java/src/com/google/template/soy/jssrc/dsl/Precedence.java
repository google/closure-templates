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

package com.google.template.soy.jssrc.dsl;

import com.google.template.soy.exprtree.Operator;

/**
 * JavaScript operator precedence as documented in:
 * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Operators/Operator_Precedence#table
 */
public enum Precedence {
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
  P14,
  P15,
  P16,
  P17,
  P18;

  public static Precedence forSoyOperator(Operator soyOperator) {
    switch (soyOperator) {
      case ASSERT_NON_NULL:
        return P17;
      case NEGATIVE:
      case NOT:
        return P14;
      case TIMES:
      case DIVIDE_BY:
      case MOD:
        return P12;
      case PLUS:
      case MINUS:
        return P11;
      case SHIFT_LEFT:
      case SHIFT_RIGHT:
        return P10;
      case LESS_THAN:
      case GREATER_THAN:
      case LESS_THAN_OR_EQUAL:
      case GREATER_THAN_OR_EQUAL:
        return P9;
      case EQUAL:
      case NOT_EQUAL:
        return P8;
      case BITWISE_AND:
        return P7;
      case BITWISE_XOR:
        return P6;
      case BITWISE_OR:
        return P5;
      case AND:
        return P4;
      case OR:
        return P3;
      case NULL_COALESCING:
      case CONDITIONAL:
        return P2;
    }
    throw new AssertionError();
  }

  public boolean greaterThan(Precedence other) {
    return this.ordinal() > other.ordinal();
  }

  public boolean lessThan(Precedence other) {
    return this.ordinal() < other.ordinal();
  }

  public int toInt() {
    return ordinal() + 1;
  }

  Precedence() {}
}
