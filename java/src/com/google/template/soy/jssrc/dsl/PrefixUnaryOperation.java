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
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.Operator.Associativity;

/** Represents a JavaScript unary operation. */
@AutoValue
@Immutable
abstract class PrefixUnaryOperation extends Operation {
  abstract String operator();

  abstract Expression arg();

  static PrefixUnaryOperation create(Operator operator, Expression arg) {
    // Operator.NOT is the only unary Soy operator whose text differs from its JS counterpart.
    // Patch things up here.
    String operatorString = (operator == Operator.NOT ? "!" : operator.getTokenString());
    return new AutoValue_PrefixUnaryOperation(operator.getPrecedence(), operatorString, arg);
  }

  static PrefixUnaryOperation create(String operatorString, int precedence, Expression arg) {
    return new AutoValue_PrefixUnaryOperation(precedence, operatorString, arg);
  }

  @Override
  Associativity associativity() {
    return LEFT; // it's unary, doesn't matter
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    ctx.appendInitialStatements(arg());
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.append(operator());
    formatOperand(arg(), OperandPosition.LEFT /* it's unary, doesn't matter */, ctx);
  }
  
  @Override
  public void collectRequires(RequiresCollector collector) {
    arg().collectRequires(collector);
  }

  @Override
  public ImmutableList<Statement> initialStatements() {
    return arg().initialStatements();
  }
}
