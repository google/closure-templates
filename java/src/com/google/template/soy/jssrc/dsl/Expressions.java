/*
 * Copyright 2022 Google Inc.
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
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.template.soy.base.SourceLocation.ByteSpan;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.dsl.CodeChunk.Generator;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.JsExprUtils;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Static functions related to Expressions. */
public final class Expressions {

  public static final Expression LITERAL_ANY = id("any");
  public static final Expression LITERAL_TRUE = id("true");
  public static final Expression LITERAL_FALSE = id("false");
  public static final Expression LITERAL_NULL = id("null");
  public static final Expression LITERAL_UNDEFINED = id("undefined");
  public static final Expression LITERAL_EMPTY_STRING = stringLiteral("");
  public static final Expression LITERAL_EMPTY_LIST = arrayLiteral(ImmutableList.of());
  public static final Expression EMPTY_OBJECT_LITERAL = objectLiteral(ImmutableMap.of());
  public static final Expression THIS = id("this");
  public static final Expression EMPTY =
      new Expression() {
        @Override
        void doFormatOutputExpr(FormattingContext ctx) {}

        @Override
        Stream<? extends CodeChunk> childrenStream() {
          return Stream.empty();
        }
      };

  /** Exploding error expr. This will blow up if used to write gencode. */
  public static final Expression ERROR_EXPR =
      new Expression() {
        @Override
        void doFormatOutputExpr(FormattingContext ctx) {
          throw new IllegalStateException(
              "ERROR_EXPR should never be used to write gencode! This soy file had a problem, and"
                  + " the resulting js/ts will be invalid.");
        }

        @Override
        Stream<? extends CodeChunk> childrenStream() {
          return Stream.empty();
        }

        @Override
        public JsExpr singleExprOrName(FormatOptions formatOptions) {
          return new JsExpr("$$SOY_INTERNAL_ERROR_EXPR", Integer.MAX_VALUE);
        }
      };

  public static boolean isStringLiteral(Expression expr) {
    return expr instanceof StringLiteral;
  }

  private Expressions() {}

  static boolean isSpread(Expression expr) {
    return expr instanceof UnaryOperation && ((UnaryOperation) expr).operator().equals("...");
  }

  public static Expression spread(Expression expr) {
    return UnaryOperation.create("...", Precedence.P2, expr, /* isPrefix= */ true);
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

  public static Expression tsFunction(
      ParamDecls params, Expression returnType, List<Statement> bodyStmts) {
    return TsFunction.anonymous(params, returnType, bodyStmts);
  }

  public static Expression tsArrowFunction(List<Statement> bodyStmts) {
    return tsArrowFunction(ParamDecls.EMPTY, bodyStmts);
  }

  public static Expression tsArrowFunction(ParamDecls params, List<Statement> bodyStmts) {
    return TsFunction.arrow(params, bodyStmts);
  }

  public static Expression tsArrowFunction(
      ParamDecls params, Expression returnType, List<Statement> bodyStmts) {
    return TsFunction.arrow(params, returnType, bodyStmts);
  }

  public static Expression tsArrowFunction(Expression lambda) {
    return tsArrowFunction(ParamDecls.EMPTY, ImmutableList.of(Return.create(lambda)));
  }

  public static Expression tsArrowFunction(ParamDecls params, Expression lambda) {
    return tsArrowFunction(params, ImmutableList.of(Return.create(lambda)));
  }

  public static Expression genericType(Expression className, ImmutableList<Expression> generics) {
    return GenericType.create(className, generics);
  }

  public static Expression genericType(Expression className, Expression... generics) {
    return GenericType.create(className, ImmutableList.copyOf(generics));
  }

  public static Expression functionType(Expression returnType, List<ParamDecl> params) {
    return FunctionType.create(returnType, params);
  }

  public static Expression functionType(Expression returnType, ParamDecls params) {
    return FunctionType.create(returnType, params);
  }

  public static Expression arrayType(Expression simpleType, boolean readonly) {
    return ArrayType.create(readonly, simpleType);
  }

  public static Expression unionType(List<Expression> members) {
    return UnionType.create(members);
  }

  public static Expression recordType(List<ParamDecl> params) {
    return RecordType.create(params);
  }

  /**
   * Creates a code chunk representing a JavaScript identifier.
   *
   * @throws IllegalArgumentException if {@code id} is not a valid JavaScript identifier.
   */
  public static Expression id(String id) {
    return Id.create(id);
  }

  /**
   * Creates a code chunk representing a JavaScript identifier.
   *
   * @throws IllegalArgumentException if {@code id} is not a valid JavaScript identifier.
   */
  public static Expression id(String id, Iterable<GoogRequire> requires) {
    return Id.builder(id).setGoogRequires(ImmutableSet.copyOf(requires)).build();
  }

  public static Expression id(String id, GoogRequire... requires) {
    return Id.builder(id).setGoogRequires(ImmutableSet.copyOf(requires)).build();
  }

  public static Expression importedId(String id, String path) {
    return id(id, GoogRequire.createImport(id, path));
  }

  public static Expression importedId(String id, String alias, String path) {
    return id(alias, GoogRequire.createImport(id, alias, path));
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

  public static Expression dottedIdNoRequire(String dotSeparatedIdentifiers, ByteSpan byteSpan) {
    return dottedIdWithRequires(dotSeparatedIdentifiers, ImmutableSet.of(), byteSpan);
  }

  public static Expression dottedIdWithRequires(
      String dotSeparatedIdentifiers, Iterable<GoogRequire> requires) {
    return dottedIdWithRequires(dotSeparatedIdentifiers, requires, null);
  }

  private static Expression dottedIdWithRequires(
      String dotSeparatedIdentifiers, Iterable<GoogRequire> requires, @Nullable ByteSpan byteSpan) {
    List<String> ids = Splitter.on('.').splitToList(dotSeparatedIdentifiers);
    Preconditions.checkState(
        !ids.isEmpty(),
        "not a dot-separated sequence of JavaScript identifiers: %s",
        dotSeparatedIdentifiers);
    // Associate the requires with the base id for convenience.  It is arguable that they should
    // be instead associated with the last dot. Or perhaps with the 'whole' expression somehow.
    // This is a minor philosophical concern but it should be fine in practice because nothing would
    // ever split apart a code chunk into sub-chunks.  So the requires could really go anywhere.
    Id tail = (Id) id(Iterables.getLast(ids), requires);
    if (byteSpan != null) {
      tail = tail.toBuilder().setSpan(byteSpan).build();
    }

    if (ids.size() == 1) {
      return tail;
    } else {
      Expression tip = id(ids.get(0), requires);
      for (int i = 1; i < ids.size() - 1; ++i) {
        tip = tip.dotAccess(ids.get(i));
      }
      return Dot.create(tip, tail);
    }
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

  public static Expression stringLiteral(String contents, QuoteStyle quoteStyle) {
    return StringLiteral.builder(contents).setQuoteStyle(quoteStyle).build();
  }

  /**
   * Creates a code chunk representing a JavaScript regular expression literal.
   *
   * @param contents The regex literal (including the opening and closing slashes).
   */
  public static Expression regexLiteral(String contents) {
    int firstSlash = contents.indexOf('/');
    int lastSlash = contents.lastIndexOf('/');
    Preconditions.checkArgument(
        firstSlash < lastSlash && firstSlash != -1,
        "expected regex to start with a '/' and have a second '/' near the end, got %s",
        contents);
    return Leaf.createNonNull(contents, /* isCheap= */ false);
  }

  /** Creates a code chunk representing a JavaScript number literal. */
  public static Expression number(long value) {
    Preconditions.checkArgument(
        IntegerNode.isInRange(value), "Number is outside JS safe integer range: %s", value);
    return Leaf.createNonNull(Long.toString(value), /* isCheap= */ true);
  }

  /** Creates a code chunk representing a JavaScript number literal. */
  public static Expression number(double value) {
    return Leaf.createNonNull(Double.toString(value), /* isCheap= */ true);
  }

  /** Creates a code chunk representing an anonymous function literal. */
  public static Expression function(JsDoc parameters, Statement body) {
    return FunctionDeclaration.create(parameters, body);
  }

  /** Creates a code chunk representing an arrow function. */
  public static Expression arrowFunction(JsDoc parameters, Statement body) {
    return JsArrowFunction.create(parameters, body);
  }

  /** Creates a code chunk representing an arrow function. */
  public static Expression arrowFunction(JsDoc parameters, Expression body) {
    return arrowFunction(parameters, Statements.returnValue(body));
  }

  /** Creates a code chunk representing the logical negation {@code !} of the given chunk. */
  public static Expression not(Expression arg) {
    return UnaryOperation.create(Operator.NOT, arg);
  }

  public static Expression assertNonNull(Expression arg) {
    return UnaryOperation.create(Operator.ASSERT_NON_NULL, arg);
  }

  public static Expression assertNonNullForGenTsxTypeNarrowing(Expression arg) {
    return UnaryOperation.createForGenTsxTypeNarrowing(Operator.ASSERT_NON_NULL, arg);
  }

  /**
   * Creates a code chunk representing the {@code new} operator applied to the given constructor. If
   * you need to call the constructor with arguments, call {@link Expression#call} on the returned
   * chunk.
   */
  public static Expression construct(Expression ctor, Expression... args) {
    return New.create(ctor).call(args);
  }

  public static Expression construct(Expression ctor, Iterable<? extends Expression> args) {
    return New.create(ctor).call(args);
  }

  public static Expression constructMap(Expression... initializers) {
    return New.create(id("Map", GoogRequire.create("soy.map"))).call(initializers);
  }

  /**
   * Creates a code chunk representing the given Soy operator applied to the given operands.
   *
   * <p>Cannot be used for {@link Operator#AND}, {@link Operator#OR}, or {@link
   * Operator#CONDITIONAL}, as they require access to a {@link Generator} to generate temporary
   * variables for short-circuiting. Use {@link Expression#and}, {@link Expression#or}, and {@link
   * Generator#conditionalExpression} instead.
   */
  public static Expression operation(Operator op, Expression... operands) {
    return operation(op, ImmutableList.copyOf(operands));
  }

  public static Expression operation(Operator op, List<Expression> operands) {
    Preconditions.checkArgument(operands.size() == op.getNumOperands());
    Preconditions.checkArgument(op != Operator.CONDITIONAL);
    switch (op.getNumOperands()) {
      case 1:
        return UnaryOperation.create(op, operands.get(0));
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

  public static Expression arrayLiteral(Expression... elements) {
    return ArrayLiteral.create(ImmutableList.copyOf(elements));
  }

  /**
   * Creates a code chunk representing a javascript map literal: {@code {key1: value1, key2:
   * value2}}
   */
  public static Expression objectLiteral(Map<String, Expression> object) {
    return ObjectLiteral.create(object);
  }

  /** Creates a code chunk representing an object literal with the specified keys/values. */
  public static Expression objectLiteralWithKeys(Map<Expression, Expression> object) {
    return ObjectLiteral.createWithKeys(object);
  }

  /**
   * Returns a unique key that can be used in the parameter passed to {@link #objectLiteral} to
   * cause the corresponding value to be interpreted as an object spread.
   */
  public static String objectLiteralSpreadKey() {
    return ObjectLiteral.newSpread();
  }

  /**
   * Creates a code chunk representing a javascript map literal, where the keys are quoted: {@code
   * {'key1': value1, 'key2': value2}}
   */
  public static Expression objectLiteralWithQuotedKeys(Map<String, Expression> object) {
    return ObjectLiteral.createWithQuotedKeys(object);
  }

  public static Expression ternary(
      Expression predicate, Expression consequent, Expression alternate) {
    return Ternary.create(predicate, consequent, alternate);
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

  public static Expression group(Expression e) {
    return Group.create(e);
  }

  /**
   * Builds a {@link Expression} that represents the concatenation of the given code chunks. The
   * {@code +} operator is used for concatenation.
   *
   * <p>The resulting chunk is not guaranteed to be string-valued if the first two operands do not
   * produce strings when combined with the plus operator; e.g. 2+2 might be 4 instead of '22'.
   *
   * <p>This is a port of {@link JsExprUtils#concatJsExprs}, which should eventually go away.
   * TODO(b/32224284): make that go away.
   */
  public static Expression concat(List<? extends Expression> chunks) {
    return Concatenation.create(chunks);
  }

  public static Expression concat(Expression... chunks) {
    return Concatenation.create(ImmutableList.copyOf(chunks));
  }

  /**
   * Builds a {@link Expression} that represents the concatenation of the given code chunks. This
   * doesn't assume the values represented by the inputs are necessarily strings, but guarantees
   * that the value represented by the output is a string.
   */
  public static Expression concatForceString(List<? extends Expression> chunks) {
    if (!chunks.isEmpty()
        && chunks.get(0).isRepresentableAsSingleExpression()
        && isStringLiteral(chunks.get(0))) {
      return concat(chunks);
    } else if (chunks.size() > 1
        && chunks.get(1).isRepresentableAsSingleExpression()
        && isStringLiteral(chunks.get(1))) {
      return concat(chunks);
    } else {
      return concat(
          ImmutableList.<Expression>builder().add(LITERAL_EMPTY_STRING).addAll(chunks).build());
    }
  }

  @Nullable
  static String getLeafText(Expression expr) {
    if (expr instanceof Id) {
      return ((Id) expr).id();
    }
    if (expr instanceof Leaf) {
      return ((Leaf) expr).value().getText();
    }
    return null;
  }

  /**
   * Returns the base expression, if the passed in expression is a method call with matching name.
   */
  @Nullable
  public static Expression baseForMethodCall(Expression expr, String name) {
    if (expr instanceof Call) {
      Expression receiver = ((Call) expr).receiver();
      if (receiver instanceof Dot) {
        String leafText = getLeafText(((Dot) receiver).key());
        if (name.equals(leafText)) {
          return ((Dot) receiver).receiver();
        }
      }
    }
    return null;
  }

  @AutoValue
  abstract static class DecoratedExpression extends Expression {

    public static Expression create(
        Expression expr, List<SpecialToken> before, List<SpecialToken> after) {
      if (before.isEmpty() && after.isEmpty()) {
        return expr;
      }
      return new AutoValue_Expressions_DecoratedExpression(
          ImmutableList.copyOf(before), expr, ImmutableList.copyOf(after));
    }

    abstract ImmutableList<SpecialToken> beforeTokens();

    abstract Expression expr();

    abstract ImmutableList<SpecialToken> afterTokens();

    @Override
    void doFormatOutputExpr(FormattingContext ctx) {
      for (CodeChunk chunk : beforeTokens()) {
        ctx.appendAll(chunk);
      }
      ctx.appendOutputExpression(expr());
      for (CodeChunk chunk : afterTokens()) {
        ctx.appendAll(chunk);
      }
    }

    @Override
    Stream<? extends CodeChunk> childrenStream() {
      return Streams.concat(beforeTokens().stream(), Stream.of(expr()), afterTokens().stream());
    }

    @Override
    public Expression append(List<SpecialToken> tokens) {
      if (tokens.isEmpty()) {
        return this;
      }
      return create(
          expr(),
          beforeTokens(),
          ImmutableList.<SpecialToken>builder().addAll(tokens).addAll(afterTokens()).build());
    }

    @Override
    public Expression prepend(List<SpecialToken> tokens) {
      if (tokens.isEmpty()) {
        return this;
      }
      return create(
          expr(),
          ImmutableList.<SpecialToken>builder().addAll(tokens).addAll(beforeTokens()).build(),
          afterTokens());
    }
  }

  public static NullSafeAccumulatorReceiver nullSafeAccumulatorReceiver(
      Expression delegate, boolean nullSafe) {
    return new NullSafeAccumulatorReceiver(delegate, nullSafe);
  }

  /**
   * @see com.google.template.soy.jssrc.internal.NullSafeAccumulator.FunctionCall
   */
  @SuppressWarnings("Immutable")
  public static class NullSafeAccumulatorReceiver extends DelegatingExpression {

    private final boolean nullSafe;
    private boolean dereferenced = false;

    public NullSafeAccumulatorReceiver(Expression delegate, boolean nullSafe) {
      super(delegate);
      this.nullSafe = nullSafe;
    }

    @Override
    public Expression dotAccess(String identifier, boolean nullSafe) {
      dereferenced = true;
      return delegate.dotAccess(identifier, this.nullSafe || nullSafe);
    }

    @Override
    public Expression bracketAccess(Expression arg, boolean nullSafe) {
      dereferenced = true;
      return delegate.bracketAccess(arg, this.nullSafe || nullSafe);
    }

    @Override
    void doFormatOutputExpr(FormattingContext ctx) {
      delegate.doFormatOutputExpr(ctx);
    }

    public boolean wasDereferenced() {
      return dereferenced;
    }
  }
}
