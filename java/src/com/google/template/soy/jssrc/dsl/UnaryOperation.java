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

import static com.google.template.soy.exprtree.Operator.Associativity.LEFT;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.Operator.Associativity;
import java.util.stream.Stream;

/** Represents a JavaScript unary operation. */
@AutoValue
@Immutable
abstract class UnaryOperation extends Operation {
  abstract String operator();

  abstract Expression arg();

  abstract boolean isPrefix();

  static UnaryOperation create(Operator operator, Expression arg) {
    // Operator.NOT is the only unary Soy operator whose text differs from its JS counterpart.
    // Patch things up here.
    String operatorString = (operator == Operator.NOT ? "!" : operator.getTokenString());
    return new AutoValue_UnaryOperation(
        operator.getPrecedence(), operatorString, arg, operator != Operator.ASSERT_NON_NULL);
  }

  static UnaryOperation create(
      String operatorString, int precedence, Expression arg, boolean isPrefix) {
    return new AutoValue_UnaryOperation(precedence, operatorString, arg, isPrefix);
  }

  @Override
  Associativity associativity() {
    return LEFT; // it's unary, doesn't matter
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.of(arg());
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    if (isPrefix()) {
      ctx.append(operator());
      formatOperand(arg(), OperandPosition.LEFT /* it's unary, doesn't matter */, ctx);
    } else {
      formatOperand(arg(), OperandPosition.LEFT /* it's unary, doesn't matter */, ctx);
      ctx.append(operator());
    }
  }
}
