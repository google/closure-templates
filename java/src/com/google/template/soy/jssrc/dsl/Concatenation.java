/*
 * Copyright 2018 Google Inc.
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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.Operator.Associativity;

/**
 * Represents the concatenation of many chunks via the {@code +} operator.
 *
 * <p>This could be represented as a nested sequence of {@link BinaryOperation} chunks, but the
 * compiler tends to create very large concatenations (thousands of nodes) and the naive recursive
 * formatting algorithm can cause stack overflow errors.
 */
@AutoValue
@Immutable
abstract class Concatenation extends Operation {

  static Concatenation create(Iterable<? extends Expression> parts) {
    ImmutableList.Builder<Statement> initialStatements = ImmutableList.builder();
    ImmutableList.Builder<Expression> partsBuilder = ImmutableList.builder();
    for (Expression part : parts) {
      initialStatements.addAll(part.initialStatements());
      if (part instanceof Concatenation) {
        partsBuilder.addAll(((Concatenation) part).parts());
      } else if (part instanceof BinaryOperation) {
        BinaryOperation binaryOp = (BinaryOperation) part;
        if (binaryOp.operator().equals(Operator.PLUS.getTokenString())) {
          partsBuilder.add(binaryOp.arg1());
          partsBuilder.add(binaryOp.arg2());
        } else {
          partsBuilder.add(part);
        }
      } else {
        partsBuilder.add(part);
      }
    }
    return new AutoValue_Concatenation(initialStatements.build(), partsBuilder.build());
  }

  abstract ImmutableList<Expression> parts();

  @Override
  int precedence() {
    return Operator.PLUS.getPrecedence();
  }

  @Override
  Associativity associativity() {
    return Operator.PLUS.getAssociativity();
  }

  @Override
  public void collectRequires(RequiresCollector collector) {
    for (Expression part : parts()) {
      part.collectRequires(collector);
    }
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    if (parts().isEmpty()) {
      ctx.append("''");
    } else if (parts().size() == 1) {
      ctx.appendOutputExpression(parts().get(0));
    } else {
      formatOperand(parts().get(0), OperandPosition.LEFT, ctx);
      for (int i = 1; i < parts().size(); i++) {
        ctx.append(" + ");
        formatOperand(parts().get(i), OperandPosition.RIGHT, ctx);
      }
    }
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    for (Expression part : parts()) {
      ctx.appendInitialStatements(part);
    }
  }
}
