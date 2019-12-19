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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.Operator.Associativity;
import com.google.template.soy.jssrc.restricted.JsExpr;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

  public static final Expression LITERAL_TRUE = id("true");
  public static final Expression LITERAL_FALSE = id("false");
  public static final Expression LITERAL_NULL = id("null");
  public static final Expression LITERAL_UNDEFINED = id("undefined");
  public static final Expression LITERAL_EMPTY_STRING = Leaf.create("''", /* isCheap= */ true);
  public static final Expression LITERAL_EMPTY_LIST = Leaf.create("[]", /* isCheap= */ true);
  public static final Expression EMPTY_OBJECT_LITERAL = Leaf.create("{}", /* isCheap= */ false);
  public static final Expression THIS = id("this");

  // Do not put public static constants or methods on this class.  If you do then this can trigger
  // classloading deadlocks due to cyclic references between this class, CodeChunk and the
  // implementation class of the constant.

  Expression() {
    /* no subclasses outside this package */
  }

  /** Starts a conditional expression beginning with the given predicate and consequent chunks. */
  public static ConditionalExpressionBuilder ifExpression(
      Expression predicate, Expression consequent) {
    return new ConditionalExpressionBuilder(predicate, consequent);
  }

  /**
   * Creates a new code chunk from the given expression. The expression's precedence is preserved.
   */
  public static Expression fromExpr(JsExpr expr, Iterable<GoogRequire> requires) {
    return Leaf.create(expr, /* isCheap= */ false, requires);
  }

  /**
   * Creates a code chunk representing a JavaScript identifier.
   *
   * @throws IllegalArgumentException if {@code id} is not a valid JavaScript identifier.
   */
  public static Expression id(String id) {
    CodeChunkUtils.checkId(id);
    return Leaf.create(id, /* isCheap= */ true);
  }

  /**
   * Creates a code chunk representing a JavaScript identifier.
   *
   * @throws IllegalArgumentException if {@code id} is not a valid JavaScript identifier.
   */
  static Expression id(String id, Iterable<GoogRequire> requires) {
    CodeChunkUtils.checkId(id);
    return Leaf.create(id, /* isCheap= */ true, requires);
  }

  /**
   * Creates a code chunk representing a JavaScript "dotted identifier" which needs no {@code
   * goog.require} statements to be added.
   *
   * <p>"Dotted identifiers" are really just sequences of dot-access operations off some base
   * identifier, so this method is just a convenience for <code>id(...).dotAccess(...)...</code>.
   * It's provided because working with raw dot-separated strings is common.
   *
   * <p>Most dotted identifiers should be accessed via the {@link GoogRequire} api.
   */
  public static Expression dottedIdNoRequire(String dotSeparatedIdentifiers) {
    return dottedIdWithRequires(dotSeparatedIdentifiers, ImmutableSet.of());
  }

  static Expression dottedIdWithRequires(
      String dotSeparatedIdentifiers, Iterable<GoogRequire> requires) {
    List<String> ids = Splitter.on('.').splitToList(dotSeparatedIdentifiers);
    Preconditions.checkState(
        !ids.isEmpty(),
        "not a dot-separated sequence of JavaScript identifiers: %s",
        dotSeparatedIdentifiers);
    // Associate the requires with the base id for convenience.  It is arguable that they should
    // be instead associated with the last dot. Or perhaps with the 'whole' expression somehow.
    // This is a minor philosophical concern but it should be fine in practice because nothing would
    // ever split apart a code chunk into sub-chunks.  So the requires could really go anywhere.
    Expression tip = id(ids.get(0), requires);
    for (int i = 1; i < ids.size(); ++i) {
      tip = tip.dotAccess(ids.get(i));
    }
    return tip;
  }

  /**
   * Creates a code chunk representing a JavaScript string literal.
   *
   * @param contents The contents of the string literal. The contents will be escaped appropriately
   *     and embedded inside single quotes.
   */
  public static Expression stringLiteral(String contents) {
    return StringLiteral.create(contents);
  }

  /**
   * Creates a code chunk representing a JavaScript regular expression literal.
   *
   * @param contents The regex literal (including the opening and closing slashes).
   */
  public static Expression regexLiteral(String contents) {
    int firstSlash = contents.indexOf('/');
    int lastSlash = contents.lastIndexOf('/');
    checkArgument(
        firstSlash < lastSlash && firstSlash != -1,
        "expected regex to start with a '/' and have a second '/' near the end, got %s",
        contents);
    return Leaf.create(contents, /* isCheap= */ false);
  }

  /** Creates a code chunk representing a JavaScript number literal. */
  public static Expression number(long value) {
    Preconditions.checkArgument(
        IntegerNode.isInRange(value), "Number is outside JS safe integer range: %s", value);
    return Leaf.create(Long.toString(value), /* isCheap= */ true);
  }

  /** Creates a code chunk representing a JavaScript number literal. */
  public static Expression number(double value) {
    return Leaf.create(Double.toString(value), /* isCheap= */ true);
  }

  /** Creates a code chunk representing an anonymous function literal. */
  public static Expression function(JsDoc parameters, Statement body) {
    return FunctionDeclaration.create(parameters, body);
  }

  /** Creates a code chunk representing an arrow function. */
  public static Expression arrowFunction(JsDoc parameters, Statement body) {
    return FunctionDeclaration.createArrowFunction(parameters, body);
  }

  /** Creates a code chunk representing the logical negation {@code !} of the given chunk. */
  public static Expression not(Expression arg) {
    return PrefixUnaryOperation.create(Operator.NOT, arg);
  }

  /**
   * Creates a code chunk representing the {@code new} operator applied to the given constructor. If
   * you need to call the constructor with arguments, call {@link Expression#call} on the returned
   * chunk.
   */
  public static Expression construct(Expression ctor, Expression... args) {
    return New.create(ctor).call(args);
  }

  /**
   * Creates a code chunk representing the given Soy operator applied to the given operands.
   *
   * <p>Cannot be used for {@link Operator#AND}, {@link Operator#OR}, or {@link
   * Operator#CONDITIONAL}, as they require access to a {@link Generator} to generate temporary
   * variables for short-circuiting. Use {@link Expression#and}, {@link Expression#or}, and {@link
   * Generator#conditionalExpression} instead.
   */
  public static Expression operation(Operator op, List<Expression> operands) {
    Preconditions.checkArgument(operands.size() == op.getNumOperands());
    Preconditions.checkArgument(
        op != Operator.AND && op != Operator.OR && op != Operator.CONDITIONAL);
    switch (op.getNumOperands()) {
      case 1:
        return PrefixUnaryOperation.create(op, operands.get(0));
      case 2:
        return BinaryOperation.create(op, operands.get(0), operands.get(1));
      default:
        throw new AssertionError();
    }
  }

  /** Creates a code chunk representing a javascript array literal. */
  public static Expression arrayLiteral(Iterable<? extends Expression> elements) {
    return ArrayLiteral.create(ImmutableList.copyOf(elements));
  }

  /** Creates a code chunk representing a javascript array comprehension. */
  public static Expression arrayComprehension(
      Expression listExpr,
      Expression itemExpr,
      Expression iterVarDeclTranslation,
      Expression filterExpr) {
    return ArrayComprehension.create(listExpr, itemExpr, iterVarDeclTranslation, filterExpr);
  }

  /**
   * Creates a code chunk representing a javascript map literal: {@code {key1: value1, key2:
   * value2}}
   */
  public static Expression objectLiteral(Map<String, Expression> object) {
    return ObjectLiteral.create(object);
  }

  /**
   * Creates a code chunk representing a javascript map literal, where the keys are quoted: {@code
   * {'key1': value1, 'key2': value2}}
   */
  public static Expression objectLiteralWithQuotedKeys(Map<String, Expression> object) {
    return ObjectLiteral.createWithQuotedKeys(object);
  }

  /**
   * Wraps a {@link JsExpr} that could have incorrect precedence in parens.
   *
   * <p>The JsExpr constructor is inherently error-prone. It allows callers to pass a precedence
   * unrelated to the topmost operator in the text string. While JsExprs created in the Soy codebase
   * can be audited, JsExprs are also returned by {@link SoyJsSrcFunction functions} and {@link
   * SoyJsSrcPrintDirective print directives} owned by others. This method should be used to wrap
   * the results of those plugins.
   */
  public static Expression dontTrustPrecedenceOf(
      JsExpr couldHaveWrongPrecedence, Iterable<GoogRequire> requires) {
    return Group.create(fromExpr(couldHaveWrongPrecedence, requires));
  }

  /** Formats this expression as a statement. */
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
    return BinaryOperation.create(
        "+=",
        0, // the precedence of JS assignments (including +=) is lower than any Soy operator
        Associativity.RIGHT,
        this,
        rhs);
  }

  public final Expression doubleEquals(Expression rhs) {
    return BinaryOperation.create(Operator.EQUAL, this, rhs);
  }

  public final Expression doubleNotEquals(Expression rhs) {
    return BinaryOperation.create(Operator.NOT_EQUAL, this, rhs);
  }

  public final Expression tripleEquals(Expression rhs) {
    return BinaryOperation.create(
        "===", Operator.EQUAL.getPrecedence(), Operator.EQUAL.getAssociativity(), this, rhs);
  }

  public final Expression tripleNotEquals(Expression rhs) {
    return BinaryOperation.create(
        "!==", Operator.EQUAL.getPrecedence(), Operator.EQUAL.getAssociativity(), this, rhs);
  }

  public final Expression doubleEqualsNull() {
    return doubleEquals(LITERAL_NULL);
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
    return BinaryOperation.and(this, rhs, codeGenerator);
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
    return BinaryOperation.or(this, rhs, codeGenerator);
  }

  public final Expression op(Operator op, Expression rhs) {
    return operation(op, ImmutableList.of(this, rhs));
  }

  /** Takes in a String identifier for convenience, since that's what most use cases need. */
  public final Expression dotAccess(String identifier) {
    return Dot.create(this, id(identifier));
  }

  public final Expression bracketAccess(Expression arg) {
    return Bracket.create(this, arg);
  }

  public final Expression call(Expression... args) {
    return call(Arrays.asList(args));
  }

  public final Expression call(Iterable<? extends Expression> args) {
    return Call.create(this, ImmutableList.copyOf(args));
  }

  public final Expression castAs(String typeExpression) {
    return Cast.create(this, typeExpression);
  }

  public final Expression castAs(String typeExpression, ImmutableSet<GoogRequire> googRequires) {
    return Cast.create(this, typeExpression, googRequires);
  }

  public final Expression instanceOf(Expression identifier) {
    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Operators/Operator_Precedence
    // instanceof has the same precedence as LESS_THAN
    return BinaryOperation.create(
        "instanceof", Operator.LESS_THAN.getPrecedence(), Associativity.LEFT, this, identifier);
  }

  public final Expression typeof() {
    return PrefixUnaryOperation.create("typeof ", Operator.NOT.getPrecedence(), this);
  }

  public final Expression assign(Expression rhs) {
    return BinaryOperation.create(
        "=",
        0, // the precedence of JS assignments is lower than any Soy operator
        Associativity.RIGHT,
        this,
        rhs);
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
   * understand CodeChunks (e.g. {@link SoyJsSrcFunction}).
   */
  final boolean isRepresentableAsSingleExpression() {
    return initialStatements().isEmpty();
  }

  /**
   * If this chunk can be represented as a single expression, returns that expression. If this chunk
   * cannot be represented as a single expression, returns an expression containing references to a
   * variable defined by the corresponding {@link #doFormatInitialStatements initial statements}.
   *
   * <p>This method should rarely be used, but is needed when interoperating with parts of the
   * codegen system that do not yet understand CodeChunks (e.g. {@link SoyJsSrcFunction}).
   */
  public abstract JsExpr singleExprOrName();

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
   * Returns the initial statements associated with this value. The statements must be serialized
   * before this value (for example, they could contain declarations of variables referenced in this
   * value).
   *
   * <p>TODO(b/33382980): If we have this method, why do we need doFormatInitialStatements? should
   * doFormatInitialStatements be implemented in terms of this method? is this method supposed to
   * contain all initial statements? even from conditional branches?
   */
  public abstract ImmutableList<Statement> initialStatements();

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
}
