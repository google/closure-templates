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

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.dsl.CodeChunk.Generator;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.JsExprUtils;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Static functions related to Expressions. */
public final class Expressions {

  public static final Expression LITERAL_TRUE = id("true");
  public static final Expression LITERAL_FALSE = id("false");
  public static final Expression LITERAL_NULL = id("null");
  public static final Expression LITERAL_UNDEFINED = id("undefined");
  public static final Expression LITERAL_EMPTY_STRING = stringLiteral("");
  public static final Expression LITERAL_EMPTY_LIST = arrayLiteral(ImmutableList.of());
  public static final Expression EMPTY_OBJECT_LITERAL = objectLiteral(ImmutableMap.of());
  public static final Expression THIS = id("this");

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

  public static Expression spread(Expression expr) {
    return UnaryOperation.create("...", Integer.MAX_VALUE, expr, /* isPrefix= */ true);
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

  /** Arrow function with implicit return type. */
  public static Expression tsArrowFunction(ParamDecls params, ImmutableList<Statement> bodyStmts) {
    return new TsArrowFunction(params, bodyStmts);
  }

  /** Arrow function with explicit return type. */
  public static Expression tsArrowFunction(
      ParamDecls params, Expression returnType, ImmutableList<Statement> bodyStmts) {
    return new TsArrowFunction(params, returnType, bodyStmts);
  }

  public static Expression genericType(Expression className, ImmutableList<Expression> generics) {
    return new GenericType(className, generics);
  }

  public static Expression genericType(Expression className, Expression... generics) {
    return new GenericType(className, ImmutableList.copyOf(generics));
  }

  public static Expression functionType(Expression returnType, List<ParamDecl> params) {
    return new FunctionType(returnType, params);
  }

  public static Expression arrayType(Expression simpleType, boolean readonly) {
    return new ArrayType(readonly, simpleType);
  }

  public static Expression unionType(List<Expression> members) {
    return new UnionType(members);
  }

  public static Expression recordType(List<ParamDecl> params) {
    return new RecordType(params);
  }

  /**
   * Creates a code chunk representing a JavaScript identifier.
   *
   * @throws IllegalArgumentException if {@code id} is not a valid JavaScript identifier.
   */
  public static Expression id(String id) {
    CodeChunks.checkId(id);
    return Leaf.create(id, /* isCheap= */ true);
  }

  /**
   * Creates a code chunk representing a JavaScript identifier.
   *
   * @throws IllegalArgumentException if {@code id} is not a valid JavaScript identifier.
   */
  public static Expression id(String id, Iterable<GoogRequire> requires) {
    CodeChunks.checkId(id);
    return Leaf.create(id, /* isCheap= */ true, requires);
  }

  public static Expression id(String id, GoogRequire... requires) {
    CodeChunks.checkId(id);
    return Leaf.create(id, /* isCheap= */ true, ImmutableList.copyOf(requires));
  }

  public static Expression importedId(String id, String path) {
    return id(id, GoogRequire.createImport(id, path));
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

  public static Expression dottedIdWithRequires(
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
    Preconditions.checkArgument(
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

  /** Creates a code chunk representing an arrow function. */
  public static Expression arrowFunction(JsDoc parameters, Expression body) {
    return FunctionDeclaration.createArrowFunction(parameters, body);
  }

  /** Creates a code chunk representing an immediately invoked function expression. */
  public static Expression iife(Expression expr) {
    return Group.create(FunctionDeclaration.createArrowFunction(expr)).call();
  }

  /** Creates a code chunk representing the logical negation {@code !} of the given chunk. */
  public static Expression not(Expression arg) {
    return UnaryOperation.create(Operator.NOT, arg);
  }

  public static Expression assertNonNull(Expression arg) {
    return UnaryOperation.create(Operator.ASSERT_NON_NULL, arg);
  }

  /**
   * Creates a code chunk representing the {@code new} operator applied to the given constructor. If
   * you need to call the constructor with arguments, call {@link Expression#call} on the returned
   * chunk.
   */
  public static Expression construct(Expression ctor, Expression... args) {
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
    Preconditions.checkArgument(
        op != Operator.AND && op != Operator.OR && op != Operator.CONDITIONAL);
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
}
