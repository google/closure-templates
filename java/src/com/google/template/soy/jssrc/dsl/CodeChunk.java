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
import com.google.errorprone.annotations.ForOverride;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.jssrc.restricted.JsExpr;

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

  /**
   * A trivial interface for {@link #collectRequires(RequiresCollector)} that can be used to collect
   * all required namespaces from a code chunk.
   */
  public interface RequiresCollector {
    /** Drops all requires. */
    final RequiresCollector NULL =
        new RequiresCollector() {
          @Override
          public void add(GoogRequire require) {}
        };

    /** Collects requires into an ImmutableSet that can be accessed via {@link #get} */
    final class IntoImmutableSet implements RequiresCollector {
      private final ImmutableSet.Builder<GoogRequire> builder = ImmutableSet.builder();

      @Override
      public void add(GoogRequire require) {
        builder.add(require);
      }

      public ImmutableSet<GoogRequire> get() {
        return builder.build();
      }
    }
    void add(GoogRequire require);
  }

  /** Adds all the 'goog.require' identifiers needed by this CodeChunk to the given collection. */
  public abstract void collectRequires(RequiresCollector collector);

  /**
   * Returns a sequence of JavaScript statements. In the special case that this chunk is
   * representable as a single expression, returns that expression followed by a semicolon.
   *
   * <p>This method is intended to be used at the end of codegen to emit the entire gencode. It
   * should not be used within the codegen system for intermediate representations.
   *
   * <p>Because the returned code is intended to be used at the end of codegen, it does not end
   * in a newline.
   */
  public final String getCode() {
    return getCode(0);
  }

  /**
   * {@link #doFormatInitialStatements} and {@link Expression#doFormatOutputExpr} are the main
   * methods subclasses should override to control their formatting. Subclasses should only override
   * this method in the special case that a code chunk needs to control its formatting when it is
   * the only chunk being serialized. TODO(brndn): only one override, can probably be declared
   * final.
   *
   * @param startingIndent The indent level of the foreign code into which this code will be
   *     inserted. This doesn't affect the correctness of the composed code, only its readability.
   */
  @ForOverride
  String getCode(int startingIndent) {
    FormattingContext initialStatements = new FormattingContext(startingIndent);
    initialStatements.appendInitialStatements(this);

    FormattingContext outputExprs = new FormattingContext(startingIndent);
    if (this instanceof Expression) {
      outputExprs.appendOutputExpression((Expression) this);
      outputExprs.append(';').endLine();
    }

    return initialStatements.concat(outputExprs).toString();
  }

  /**
   * Returns a sequence of JavaScript statements suitable for inserting into JS code that is not
   * managed by the CodeChunk DSL. The string is guaranteed to end in a newline.
   *
   * <p>Callers should use {@link #getCode()} when the CodeChunk DSL is managing the entire code
   * generation. getCode may drop variable declarations if there is no other code referencing those
   * variables.
   *
   * <p>By contrast, this method is provided for incremental migration to the CodeChunk DSL.
   * Variable declarations will not be dropped, since there may be gencode not managed by the
   * CodeChunk DSL that references them.
   *
   * <p>TODO(b/33382980): remove.
   *
   * @param startingIndent The indent level of the foreign code into which this code will be
   *     inserted. This doesn't affect the correctness of the composed code, only its readability.
   */
  public final String getStatementsForInsertingIntoForeignCodeAtIndent(int startingIndent) {
    String code = getCode(startingIndent);
    return code.endsWith("\n") ? code : code + "\n";
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
    RequiresCollector.IntoImmutableSet collector = new RequiresCollector.IntoImmutableSet();
    JsExpr expr = assertExprAndCollectRequires(collector);
    ImmutableSet<GoogRequire> requires = collector.get();
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
  public final JsExpr assertExprAndCollectRequires(RequiresCollector collector) {
    Expression expression = (Expression) this;
    if (!expression.isRepresentableAsSingleExpression()) {
      throw new IllegalStateException(String.format("Not an expr:\n%s", this.getCode()));
    }
    collectRequires(collector);
    return expression.singleExprOrName();
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

    private String newVarName() {
      return nameGenerator.generateName("$tmp");
    }

    /** Creates a code chunk declaring an automatically-named variable with no initializer. */
    public VariableDeclaration.Builder declarationBuilder() {
      return VariableDeclaration.builder(newVarName());
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
      return Expression.ifExpression(predicate, consequent).setElse(alternate).build(this);
    }
  }
}
