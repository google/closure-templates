/*
 * Copyright 2016 Google Inc.
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

import com.google.template.soy.exprtree.Operator.Associativity;

/**
 * The position of an operand expression with respect to its parent operator. This is combined with
 * the operand's {@link Operation#associativity} to determine whether it needs to be parenthesized.
 */
enum OperandPosition {
  LEFT {
    @Override
    boolean shouldParenthesize(Associativity operandAssociativity) {
      return operandAssociativity == Associativity.RIGHT;
    }
  },
  RIGHT {
    @Override
    boolean shouldParenthesize(Associativity operandAssociativity) {
      return operandAssociativity == Associativity.LEFT;
    }
  };

  abstract boolean shouldParenthesize(Associativity operandAssociativity);
}
