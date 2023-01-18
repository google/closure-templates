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

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.jssrc.restricted.JsExpr;
import java.util.function.Consumer;

/**
 * DSL for constructing sequences of JavaScript code. Unlike {@link JsExpr}, it can handle code that
 * cannot be represented as single expressions.
 *
 * <p>Sample usage: <code>
 * CodeChunk.Expression fraction = cg.declare(
 *     number(3)
 *         .divideBy(number(4)));
 * cg
 *     .newChunk(fraction)
 *     .if_(
 *         fraction.doubleEqualsNull(),
 *         id("someFunction").call())
 *     .endif()
 *     .assign(fraction.times(number(5)))
 *     .build()
 *     .getCode();
 * </code> produces <code>
 *   var $$tmp0 = 3 / 4;
 *   if ($$tmp0 == null) {
 *     someFunction();
 *   }
 *   $$tmp0 = $$tmp0 * 5;
 * </code> TODO(b/33382980): do all JS code generation with this DSL (that is, remove {@link
 * com.google.template.soy.jssrc.internal.JsCodeBuilder}).
 */
@Immutable
public abstract class CodeChunk {

  /** Adds all the 'goog.require' identifiers needed by this CodeChunk to the given collection. */
  public abstract void collectRequires(Consumer<GoogRequire> collector);

  /**
   * Returns a sequence of JavaScript statements. In the special case that this chunk is
   * representable as a single expression, returns that expression followed by a semicolon.
   *
   * <p>This method is intended to be used at the end of codegen to emit the entire gencode. It
   * should not be used within the codegen system for intermediate representations.
   *
   * <p>Because the returned code is intended to be used at the end of codegen, it does not end in a
   * newline.
   */
  public final String getCode(FormatOptions formatOptions) {
    FormattingContext initialStatements = new FormattingContext(formatOptions);
    initialStatements.appendInitialStatements(this);

    if (this instanceof Expression) {
      FormattingContext outputExprs = new FormattingContext(formatOptions);
      outputExprs.appendOutputExpression((Expression) this);
      outputExprs.append(';').endLine();
      return initialStatements.concat(outputExprs).toString();
    } else {
      return initialStatements.toString();
    }
  }

  /**
   * Temporary method to ease migration to the CodeChunk DSL.
   *
   * <p>Because of the recursive nature of the JS codegen system, it is generally not possible to
   * convert one codegen method at a time to use the CodeChunk DSL. However, the business logic
   * inside those methods can be migrated incrementally. Methods that do not yet use the CodeChunk
   * DSL can "unwrap" inputs using this method and "wrap" results using {@link
   * CodeChunk#fromExpr(JsExpr)}. This is safe as long as each CodeChunk generated for production
   * code is {@link Expression#isRepresentableAsSingleExpression}.
   *
   * <p>TODO(b/32224284): remove.
   */
  public final JsExpr assertExpr() {
    ImmutableSet.Builder<GoogRequire> requiresBuilder = ImmutableSet.builder();
    JsExpr expr = assertExprAndCollectRequires(requiresBuilder::add);
    ImmutableSet<GoogRequire> requires = requiresBuilder.build();
    if (!requires.isEmpty()) {
      throw new IllegalStateException("calling assertExpr() would drop requires!: " + requires);
    }
    return expr;
  }

  /**
   * Temporary method to ease migration to the CodeChunk DSL.
   *
   * <p>Because of the recursive nature of the JS codegen system, it is generally not possible to
   * convert one codegen method at a time to use the CodeChunk DSL. However, the business logic
   * inside those methods can be migrated incrementally. Methods that do not yet use the CodeChunk
   * DSL can "unwrap" inputs using this method and "wrap" results using {@link
   * CodeChunk#fromExpr(JsExpr)}. This is safe as long as each CodeChunk generated for production
   * code is {@link Expression#isRepresentableAsSingleExpression}.
   *
   * <p>TODO(b/32224284): remove.
   */
  public final JsExpr assertExprAndCollectRequires(Consumer<GoogRequire> collector) {
    Expression expression = (Expression) this;
    if (!expression.isRepresentableAsSingleExpression()) {
      throw new IllegalStateException(
          String.format("Not an expr:\n%s", this.getCode(FormatOptions.JSSRC)));
    }
    collectRequires(collector);
    return expression.singleExprOrName(FormatOptions.JSSRC);
  }

  /**
   * If this chunk can be represented as a single expression, does nothing. If this chunk cannot be
   * represented as a single expression, writes everything except the final expression to the
   * buffer. Must only be called by {@link FormattingContext#appendInitialStatements}.
   */
  abstract void doFormatInitialStatements(FormattingContext ctx);

  CodeChunk() {}

  /**
   * Code chunks in a single Soy template emit code into a shared JavaScript lexical scope, so they
   * must use distinct variable names. This class enforces that.
   */
  public static final class Generator {

    private final UniqueNameGenerator nameGenerator;

    private Generator(UniqueNameGenerator nameGenerator) {
      this.nameGenerator = nameGenerator;
    }

    /** Returns an object that can be used to build code chunks. */
    public static Generator create(UniqueNameGenerator nameGenerator) {
      return new Generator(nameGenerator);
    }

    private String newVarName(String prefix) {
      return nameGenerator.generate(prefix);
    }

    private String newVarName() {
      return newVarName("$tmp");
    }

    /** Creates a code chunk declaring an automatically-named variable with no initializer. */
    public VariableDeclaration.Builder declarationBuilder() {
      return VariableDeclaration.builder(newVarName());
    }
    /** Creates a code chunk declaring an automatically-named variable with no initializer. */
    public VariableDeclaration.Builder declarationBuilder(String prefix) {
      return VariableDeclaration.builder(newVarName(prefix));
    }

    /**
     * Returns a code chunk representing an if-then-else condition.
     *
     * <p>If all the parameters are {@link Expression#isRepresentableAsSingleExpression
     * representable as single expressions}, the returned chunk will use the JavaScript ternary
     * syntax ({@code predicate ? consequent : alternate}). Otherwise, the returned chunk will use
     * JavaScript conditional statement syntax: <code>
     *   var $tmp = null;
     *   if (predicate) {
     *     $tmp = consequent;
     *   } else {
     *     $tmp = alternate;
     *   }
     * </code>
     */
    public Expression conditionalExpression(
        Expression predicate, Expression consequent, Expression alternate) {
      return Expressions.ifExpression(predicate, consequent).setElse(alternate).build(this);
    }
  }
}
