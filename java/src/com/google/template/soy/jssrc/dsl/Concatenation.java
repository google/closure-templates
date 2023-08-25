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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    ImmutableList.Builder<Expression> partsBuilder = ImmutableList.builder();
    for (Expression part : parts) {
      if (part instanceof Concatenation) {
        partsBuilder.addAll(((Concatenation) part).parts());
      } else if (part.equals(TsxPrintNode.NIL)) {
        // Skip it.
      } else {
        partsBuilder.add(part);
      }
    }
    return new AutoValue_Concatenation(partsBuilder.build());
  }

  abstract ImmutableList<Expression> parts();

  /** Returns a new concatenation by mapping each part of this instance with {@code mapper}. */
  public Concatenation map1to1(Function<Expression, Expression> mapper) {
    boolean diff = false;
    List<Expression> mappedParts = new ArrayList<>(parts().size());
    for (Expression part : parts()) {
      Expression mapped = mapper.apply(part);
      if (mapped != part) {
        diff = true;
      }
      mappedParts.add(mapped);
    }
    return diff ? create(mappedParts) : this;
  }

  /**
   * Returns a new concatenation by mapping each part of this instance with {@code mapper}. The
   * mapping function can return 0-n replacements for each part.
   */
  public Concatenation map1toN(Function<Expression, Stream<CodeChunk>> mapper) {
    boolean diff = false;
    List<Expression> mappedParts = new ArrayList<>(parts().size());
    for (Expression part : parts()) {
      List<CodeChunk> chunks = mapper.apply(part).collect(Collectors.toList());
      if (chunks.size() != 1 || chunks.get(0) != part) {
        diff = true;
      }
      for (CodeChunk chunk : chunks) {
        mappedParts.add((Expression) chunk);
      }
    }
    return diff ? create(mappedParts) : this;
  }

  @Override
  public Precedence precedence() {
    return Precedence.P11;
  }

  @Override
  public Associativity associativity() {
    return Operator.PLUS.getAssociativity();
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return parts().stream();
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
        ctx.appendUnlessEmpty(ctx.getConcatenationOperator());
        formatOperand(parts().get(i), OperandPosition.RIGHT, ctx);
      }
    }
  }
}
