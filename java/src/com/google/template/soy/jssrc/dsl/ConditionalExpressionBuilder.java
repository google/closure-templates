/*
 * Copyright 2017 Google Inc.
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

import com.google.template.soy.jssrc.dsl.CodeChunk.Generator;
import javax.annotation.CheckReturnValue;

/**
 * Builds a single {@link Conditional conditional expression}.
 *
 * <p>In contrast with {@link ConditionalBuilder}, this class requires the whole conditional to
 * represent a value, and {@link #build(Generator)} returns a {@link CodeChunk.WithValue}
 * representing that value.
 */
public final class ConditionalExpressionBuilder {

  private final ConditionalBuilder underlying;

  ConditionalExpressionBuilder(CodeChunk.WithValue predicate, CodeChunk.WithValue consequent) {
    underlying = new ConditionalBuilder(predicate, consequent);
  }

  public ConditionalExpressionBuilder elseif_(
      CodeChunk.WithValue predicate, CodeChunk.WithValue consequent) {
    underlying.elseif_(predicate, consequent);
    return this;
  }

  public ConditionalExpressionBuilder else_(CodeChunk.WithValue trailingElse) {
    underlying.else_(trailingElse);
    return this;
  }

  /** Finishes building this conditional. */
  @CheckReturnValue
  public CodeChunk.WithValue build(CodeChunk.Generator codeGenerator) {
    CodeChunk chunk = underlying.build();
    return chunk instanceof Conditional
        ? ((Conditional) chunk).asConditionalExpression(codeGenerator)
        : (CodeChunk.WithValue) chunk;
  }
}
