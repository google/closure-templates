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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.template.soy.exprtree.Operator.AMP_AMP;
import static com.google.template.soy.exprtree.Operator.BAR_BAR;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.base.SourceLocation.ByteSpan;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.internal.util.TreeStreams;
import com.google.template.soy.jssrc.dsl.Expressions.DecoratedExpression;
import com.google.template.soy.jssrc.dsl.Expressions.ExpressionWithSpan;
import com.google.template.soy.jssrc.dsl.Precedence.Associativity;
import com.google.template.soy.jssrc.restricted.JsExpr;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Marker class for a chunk of code that represents a value.
 *
 * <p>Expressions represent values. Sequences of statements can represent a value (for example, if
 * the first statement declares a variable and subsequent statements update the variable's state),
 * but they are not required to.
 *
 * <p>Chunks representing values are required in certain contexts (for example, the right-hand side
 * of an {@link Expression#assign assignment}).
 */
@Immutable
public abstract class Expression extends CodeChunk {

  // Do not put public static constants or methods on this class.  If you do then this can trigger
  // classloading deadlocks due to cyclic references between this class, CodeChunk and the
  // implementation class of the constant.

  Expression() {
    /* no subclasses outside this package */
  }

  /** Whether when formatted, this expression will begin with an object literal. */
  boolean initialExpressionIsObjectLiteral() {
    return false;
  }

  public boolean isDefinitelyNotNull() {
    return false;
  }

  /**
   * If this chunk can be represented as a single expression, returns that expression. If this chunk
   * cannot be represented as a single expression, returns an expression containing references to a
   * variable defined by the corresponding {@link #doFormatInitialStatements initial statements}.
   *
   * <p>This method should rarely be used, but is needed when interoperating with parts of the
   * codegen system that do not yet understand CodeChunks (e.g. {@link SoyJsSrcFunction}).
   */
  public JsExpr singleExprOrName(FormatOptions formatOptions) {
    FormattingContext ctx = new FormattingContext(formatOptions);
    doFormatOutputExpr(ctx);
    return new JsExpr(ctx.toString(), Integer.MAX_VALUE);
  }

  /**
   * If this chunk can be represented as a single expression, writes that single expression to the
   * buffer. If the chunk cannot be represented as a single expression, writes an expression to the
   * buffer containing references to a variable defined by the corresponding {@link
   * #doFormatInitialStatements initial statements}.
   *
   * <p>Must only be called by {@link FormattingContext#appendOutputExpression}.
   */
  abstract void doFormatOutputExpr(FormattingContext ctx);

  /**
   * Returns {@code true} if the expression represented by this code chunk is so trivial that it
   * isn't worth storing it in a temporary if it needs to be referenced multiple times.
   *
   * <p>The default is {@code false}, only certain special code chunks return {@code true}.
   */
  public boolean isCheap() {
    return false;
  }

  /** Returns the string literal value of this Expression if it is a string literal. */
  public Optional<String> asStringLiteral() {
    return Optional.empty();
  }

  /**
   * Defines a list of child code chunks that should be traversed for collecting require and initial
   * statements.
   */
  @Override
  abstract Stream<? extends CodeChunk> childrenStream();

  /** Creates a new expression by appending special tokens after this expression. */
  public Expression append(List<SpecialToken> tokens) {
    return DecoratedExpression.create(this, ImmutableList.of(), tokens);
  }

  /** Creates a new expression by prepending special tokens before this expression. */
  public Expression prepend(List<SpecialToken> tokens) {
    return DecoratedExpression.create(this, tokens, ImmutableList.of());
  }

  public Expression withByteSpan(@Nullable ByteSpan byteSpan) {
    if (byteSpan == null) {
      return this;
    }
    return ExpressionWithSpan.create(this, byteSpan);
  }

  public final Expression prepend(SpecialToken... tokens) {
    return this.prepend(ImmutableList.copyOf(tokens));
  }

  /**
   * If the expression has any initial statements, wraps it in a lambda so the expression can be
   * written inline (i.e. without a semicolon).
   */
  public final Expression asInlineExpr() {
    // If there were no initial statements, just return the expr string.
    if (!this.hasInitialStatements()) {
      return this;
    }

    // Otherwise wrap in a lambda expression so we can include the initial statements (e.g. () -> {
    // x = 5; return x + 1;}).
    return Expressions.tsArrowFunction(ParamDecls.EMPTY, this);
  }

  /** Formats this expression as a statement. */
  @Override
  public final Statement asStatement() {
    return ExpressionStatement.of(this);
  }

  /** Formats this expression as a statement with JsDoc. */
  public final Statement asStatement(JsDoc jsDoc) {
    return ExpressionStatement.of(this, jsDoc);
  }

  public final Expression plus(Expression rhs) {
    return BinaryOperation.create(Operator.PLUS, this, rhs);
  }

  public final Expression minus(Expression rhs) {
    return BinaryOperation.create(Operator.MINUS, this, rhs);
  }

  public final Expression plusEquals(Expression rhs) {
    return BinaryOperation.create("+=", Precedence.P2, Associativity.RIGHT, this, rhs);
  }

  public final Expression doubleEquals(Expression rhs) {
    return BinaryOperation.create(Operator.EQUAL, this, rhs);
  }

  public final Expression doubleNotEquals(Expression rhs) {
    return BinaryOperation.create(Operator.NOT_EQUAL, this, rhs);
  }

  public final Expression nullishCoalesce(Expression rhs, Generator codeGenerator) {
    return shortCircuiting(
        rhs, codeGenerator, Operator.NULL_COALESCING, Expression::doubleEqualsNull);
  }

  public final Expression tripleEquals(Expression rhs) {
    return BinaryOperation.create(
        "===",
        Precedence.forSoyOperator(Operator.EQUAL),
        Precedence.getAssociativity(Operator.EQUAL),
        this,
        rhs);
  }

  public final Expression tripleNotEquals(Expression rhs) {
    return BinaryOperation.create(
        "!==",
        Precedence.forSoyOperator(Operator.EQUAL),
        Precedence.getAssociativity(Operator.EQUAL),
        this,
        rhs);
  }

  public final Expression doubleEqualsNull() {
    return doubleEquals(Expressions.LITERAL_NULL);
  }

  public final Expression times(Expression rhs) {
    return BinaryOperation.create(Operator.TIMES, this, rhs);
  }

  public final Expression divideBy(Expression rhs) {
    return BinaryOperation.create(Operator.DIVIDE_BY, this, rhs);
  }

  /**
   * Returns a code chunk representing the logical and ({@code &&}) of this chunk with the given
   * chunk.
   *
   * @param codeGenerator Required in case temporary variables need to be allocated for
   *     short-circuiting behavior ({@code rhs} should be evaluated only if the current chunk
   *     evaluates as true).
   */
  public final Expression and(Expression rhs, Generator codeGenerator) {
    return shortCircuiting(rhs, codeGenerator, AMP_AMP, e -> e);
  }

  /**
   * Returns a code chunk representing the logical or ({@code ||}) of this chunk with the given
   * chunk.
   *
   * @param codeGenerator Required in case temporary variables need to be allocated for
   *     short-circuiting behavior ({@code rhs} should be evaluated only if the current chunk
   *     evaluates as false).
   */
  public final Expression or(Expression rhs, Generator codeGenerator) {
    return shortCircuiting(rhs, codeGenerator, BAR_BAR, Expressions::not);
  }

  private Expression shortCircuiting(
      Expression rhs,
      Generator codeGenerator,
      Operator nativeOp,
      Function<Expression, Expression> lhsTest) {
    // If rhs has no initial statements, use the JS operator directly.
    // It's already short-circuiting.
    if (this.hasEquivalentInitialStatements(rhs)) {
      return BinaryOperation.create(nativeOp, this, rhs);
    }
    // Otherwise, generate explicit short-circuiting code.
    // rhs should be evaluated only if testing the lhs is true.
    Expression tmp = codeGenerator.declarationBuilder().setMutable().setRhs(this).build().ref();
    return Composite.create(
        ImmutableList.of(
            Statements.ifStatement(lhsTest.apply(tmp), tmp.assign(rhs).asStatement()).build()),
        tmp);
  }

  public final Expression op(Operator op, Expression rhs) {
    return Expressions.operation(op, ImmutableList.of(this, rhs));
  }

  /** Takes in a String identifier for convenience, since that's what most use cases need. */
  public final Expression dotAccess(String identifier) {
    return dotAccess(identifier, false);
  }

  public final Expression dotAccess(Expression identifier) {
    return Dot.create(this, identifier);
  }

  public Expression dotAccess(String identifier, boolean nullSafe) {
    return nullSafe
        ? Dot.createNullSafe(this, Expressions.id(identifier))
        : Dot.create(this, Expressions.id(identifier));
  }

  public final Expression bracketAccess(Expression arg) {
    return bracketAccess(arg, false);
  }

  public Expression bracketAccess(Expression arg, boolean nullSafe) {
    return nullSafe ? Bracket.createNullSafe(this, arg) : Bracket.create(this, arg);
  }

  public final Expression call(Expression... args) {
    return call(Arrays.asList(args));
  }

  public final Expression call(Iterable<? extends Expression> args) {
    return Call.create(this, ImmutableList.copyOf(args));
  }

  public final boolean hasOuterCast() {
    return this instanceof Cast;
  }

  public final Expression castAsUnknown() {
    return Cast.create(this, "?");
  }

  public final Expression castAsNoRequire(String typeExpression) {
    return Cast.create(this, typeExpression);
  }

  public final Expression castAs(String typeExpression, ImmutableSet<GoogRequire> googRequires) {
    return Cast.create(this, typeExpression, googRequires);
  }

  public final Expression tsCast(Expression type) {
    return TsCast.create(this, type);
  }

  public final Expression instanceOf(Expression identifier) {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Operators/Operator_Precedence
    // instanceof has the same precedence as LESS_THAN
    return BinaryOperation.create(
        "instanceof", Precedence.P9, Associativity.LEFT, this, identifier);
  }

  public final Expression typeOf() {
    return UnaryOperation.create("typeof ", Precedence.P14, this, /* isPrefix= */ true);
  }

  public Expression assign(Expression rhs) {
    return BinaryOperation.create("=", Precedence.P2, Associativity.RIGHT, this, rhs);
  }

  /**
   * Returns a chunk whose output expression is the same as this chunk's, but which includes the
   * given initial statements.
   *
   * <p>This method is designed for interoperability with parts of the JS codegen system that do not
   * understand code chunks. For example, when applying plugin functions, {@link
   * com.google.template.soy.jssrc.internal.TranslateExprNodeVisitor#visitFunctionNode} needs to
   * downgrade the plugin arguments from CodeChunk.WithValues to {@link JsExpr}s for the plugin API
   * to process. The result (a JsExpr) needs to be upgraded back to a CodeChunk.Expression that
   * includes the initial statements from the original arguments.
   */
  public final Expression withInitialStatements(Iterable<? extends Statement> initialStatements) {
    // If there are no new initial statements, return the current chunk.
    if (Iterables.isEmpty(initialStatements)) {
      return this;
    }
    // Otherwise, return a code chunk that includes all of the dependent code.
    return Composite.create(ImmutableList.copyOf(initialStatements), this);
  }

  /** Convenience method for {@code withInitialStatements(ImmutableList.of(statement))}. */
  public final Expression withInitialStatement(Statement initialStatement) {
    return withInitialStatements(ImmutableList.of(initialStatement));
  }

  /**
   * Returns true if this chunk can be represented as a single expression. This method should be
   * rarely used, but is needed when interoperating with parts of the codegen system that do not yet
   * understand CodeChunks (e.g. {@link com.google.template.soy.jssrc.restricted.SoyJsSrcFunction}).
   */
  final boolean isRepresentableAsSingleExpression() {
    return !hasInitialStatements();
  }

  @Override
  final void doFormatInitialStatements(FormattingContext ctx) {
    if (this instanceof HasInitialStatements) {
      ((HasInitialStatements) this).initialStatements().forEach(ctx::appendInitialStatements);
    }
    // Do not traverse into a new scope since any initial statements therein should appear inside
    // the scope.
    if (this instanceof InitialStatementsScope) {
      return;
    }
    childrenStream()
        // Do not traverse into child statements since this prints the entire statement.
        .filter(c -> !(c instanceof Statement))
        .forEach(ctx::appendInitialStatements);
  }

  private Stream<Statement> initialStatementsStream() {
    return TreeStreams.<CodeChunk>breadthFirstWithStream(
            this,
            c -> {
              if (c instanceof Expression && !(c instanceof InitialStatementsScope)) {
                return c.childrenStream();
              }
              return Stream.of();
            })
        .filter(HasInitialStatements.class::isInstance)
        .map(HasInitialStatements.class::cast)
        .flatMap(e -> e.initialStatements().stream());
  }

  private boolean hasInitialStatements() {
    return initialStatementsStream().iterator().hasNext();
  }

  public final boolean hasEquivalentInitialStatements(Expression other) {
    ImmutableList<Statement> s1 = allInitialStatementsInTopScope();
    ImmutableList<Statement> s2 = other.allInitialStatementsInTopScope();
    return s1.containsAll(s2);
  }

  /**
   * Returns the transitive list of all initial statements in the expression tree starting at this
   * expression node, with traversal terminating at a leaf or InitialStatementsScope.
   */
  public final ImmutableList<Statement> allInitialStatementsInTopScope() {
    return initialStatementsStream().collect(toImmutableList());
  }

  /** An expression that requires initial statements in order to be valid. */
  interface HasInitialStatements {
    ImmutableList<Statement> initialStatements();
  }

  /**
   * An expression inside which initial statements may be printed, usually an expression that
   * contains a block inside. For example, a function declaration.
   */
  interface InitialStatementsScope {}
}
