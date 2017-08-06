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

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.ForOverride;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.Operator.Associativity;
import com.google.template.soy.jssrc.restricted.JsExpr;
import java.util.Arrays;
import java.util.List;

/**
 * DSL for constructing sequences of JavaScript code. Unlike {@link JsExpr}, it can handle code that
 * cannot be represented as single expressions.
 *
 * <p>Sample usage: <code>
 * CodeChunk.WithValue fraction = cg.declare(
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
 * </code> TODO(user): do all JS code generation with this DSL (that is, remove {@link
 * com.google.template.soy.jssrc.internal.JsCodeBuilder}).
 */
@Immutable
public abstract class CodeChunk {
  public static final WithValue LITERAL_TRUE = id("true");
  public static final WithValue LITERAL_FALSE = id("false");
  public static final WithValue LITERAL_NULL = id("null");
  public static final WithValue LITERAL_EMPTY_STRING = Leaf.create("''", /* isCheap= */ true);
  public static final WithValue EMPTY_OBJECT_LITERAL = Leaf.create("{}", /* isCheap= */ false);

  /** Creates a new code chunk representing the concatenation of the given chunks. */
  public static CodeChunk statements(CodeChunk first, CodeChunk... rest) {
    return statements(ImmutableList.<CodeChunk>builder().add(first).add(rest).build());
  }

  /** Creates a new code chunk representing the concatenation of the given chunks. */
  public static CodeChunk statements(Iterable<CodeChunk> stmts) {
    ImmutableList<CodeChunk> copy = ImmutableList.copyOf(stmts);
    return copy.size() == 1 ? copy.get(0) : StatementList.of(copy);
  }

  /** Starts a conditional statement beginning with the given predicate and consequent chunks. */
  public static ConditionalBuilder ifStatement(
      CodeChunk.WithValue predicate, CodeChunk consequent) {
    return new ConditionalBuilder(predicate, consequent);
  }

  /** Starts a conditional expression beginning with the given predicate and consequent chunks. */
  public static ConditionalExpressionBuilder ifExpression(
      CodeChunk.WithValue predicate, CodeChunk.WithValue consequent) {
    return new ConditionalExpressionBuilder(predicate, consequent);
  }

  /**
   * Creates a new code chunk from the given expression. The expression's precedence is preserved.
   */
  public static WithValue fromExpr(JsExpr expr, Iterable<GoogRequire> requires) {
    return Leaf.create(expr, /* isCheap= */ false, requires);
  }

  /**
   * Creates a code chunk representing a JavaScript identifier.
   *
   * @throws IllegalArgumentException if {@code id} is not a valid JavaScript identifier.
   */
  public static WithValue id(String id) {
    CodeChunkUtils.checkId(id);
    return Leaf.create(id, /* isCheap= */ true);
  }
  /**
   * Creates a code chunk representing a JavaScript identifier.
   *
   * @throws IllegalArgumentException if {@code id} is not a valid JavaScript identifier.
   */
  static WithValue id(String id, Iterable<GoogRequire> requires) {
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
  public static WithValue dottedIdNoRequire(String dotSeparatedIdentifiers) {
    return dottedIdWithRequires(dotSeparatedIdentifiers, ImmutableSet.<GoogRequire>of());
  }

  static WithValue dottedIdWithRequires(
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
    CodeChunk.WithValue tip = id(ids.get(0), requires);
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
  public static WithValue stringLiteral(String contents) {
    // Escape non-ASCII characters since browsers are inconsistent in how they interpret utf-8 in
    // JS source files.
    String escaped = BaseUtils.escapeToSoyString(contents, true /* shouldEscapeToAscii */);

    // </script in a JavaScript string will end the current script tag in most browsers. Escape the
    // forward slash in the string to get around this issue.
    escaped = escaped.replace("</script", "<\\/script");

    return Leaf.create(escaped, /* isCheap= */ true);
  }

  /** Creates a code chunk representing a JavaScript number literal. */
  public static WithValue number(long value) {
    Preconditions.checkArgument(
        IntegerNode.isInRange(value), "Number is outside JS safe integer range: %s", value);
    return Leaf.create(Long.toString(value), /* isCheap= */ true);
  }

  /** Creates a code chunk representing a JavaScript number literal. */
  public static WithValue number(double value) {
    return Leaf.create(Double.toString(value), /* isCheap= */ true);
  }

  /** Creates a code chunk that assigns value to a preexisting variable with the given name. */
  public static CodeChunk assign(String varName, CodeChunk.WithValue rhs) {
    return Assignment.create(varName, rhs);
  }

  /** Creates a code chunk that declares a new variable and assigns a value to it. */
  public static VariableDeclaration declare(String varName, CodeChunk.WithValue rhs) {
    return VariableDeclaration.create(varName, rhs);
  }

  /** Creates a code chunk representing an anonymous function literal. */
  public static CodeChunk.WithValue function(Iterable<String> parameters, CodeChunk body) {
    return FunctionDeclaration.create(parameters, body);
  }

  public static VariableDeclaration declare(
      String varName, CodeChunk.WithValue value, String typeExpr, Iterable<GoogRequire> requires) {
    return VariableDeclaration.create(varName, value, typeExpr, requires);
  }

  /** Creates a code chunk representing the logical negation {@code !} of the given chunk. */
  public static WithValue not(CodeChunk.WithValue arg) {
    return PrefixUnaryOperation.create(Operator.NOT, arg);
  }

  /** Starts a {@code switch} statement dispatching on the given chunk. */
  public static SwitchBuilder switch_(CodeChunk.WithValue switchOn) {
    return new SwitchBuilder(switchOn);
  }

  /**
   * Creates a code chunk representing the {@code new} operator applied to the given constructor. If
   * you need to call the constructor with arguments, call {@link WithValue#call} on the returned
   * chunk.
   */
  public static WithValue new_(WithValue ctor) {
    return New.create(ctor);
  }

  /**
   * Creates a code chunk representing the given Soy operator applied to the given operands.
   *
   * <p>Cannot be used for {@link Operator#AND}, {@link Operator#OR}, or {@link
   * Operator#CONDITIONAL}, as they require access to a {@link CodeChunk.Generator} to generate
   * temporary variables for short-circuiting. Use {@link CodeChunk.WithValue#and}, {@link
   * CodeChunk.WithValue#or}, and {@link CodeChunk.Generator#conditionalExpression} instead.
   */
  public static WithValue operation(Operator op, List<WithValue> operands) {
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
  public static WithValue arrayLiteral(Iterable<? extends WithValue> elements) {
    return ArrayLiteral.create(ImmutableList.copyOf(elements));
  }

  /** Creates a code chunk representing a javascript map literal. */
  public static WithValue mapLiteral(
      Iterable<? extends WithValue> keys, Iterable<? extends WithValue> values) {
    return MapLiteral.create(ImmutableList.copyOf(keys), ImmutableList.copyOf(values));
  }

  /** Creates a code chunk representing a for loop. */
  public static CodeChunk forLoop(
      String localVar,
      CodeChunk.WithValue initial,
      CodeChunk.WithValue limit,
      CodeChunk.WithValue increment,
      CodeChunk body) {
    return For.create(localVar, initial, limit, increment, body);
  }

  /** Creates a code chunk representing a for loop, with default values for initial & increment. */
  public static CodeChunk forLoop(String localVar, CodeChunk.WithValue limit, CodeChunk body) {
    return For.create(localVar, number(0), limit, number(1), body);
  }

  /** Creates a code chunk that represents a return statement returning the given value. */
  public static CodeChunk return_(CodeChunk.WithValue returnValue) {
    return Return.create(returnValue);
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
  public static WithValue dontTrustPrecedenceOf(
      JsExpr couldHaveWrongPrecedence, Iterable<GoogRequire> requires) {
    return Group.create(fromExpr(couldHaveWrongPrecedence, requires));
  }

  /**
   * Creates a code chunk from the given text, treating it as a series of statements rather than an
   * expression. For use only by {@link
   * com.google.template.soy.jssrc.internal.GenJsCodeVisitor#visitReturningCodeChunk}.
   *
   * <p>TODO(user): remove.
   */
  public static CodeChunk treatRawStringAsStatementLegacyOnly(
      String rawString, Iterable<GoogRequire> requires) {
    return LeafStatement.create(rawString, requires);
  }


  /**
   * Marker class for a chunk of code that represents a value.
   *
   * <p>Expressions represent values. Sequences of statements can represent a value (for example, if
   * the first statement declares a variable and subsequent statements update the variable's state),
   * but they are not required to.
   *
   * <p>Chunks representing values are required in certain contexts (for example, the right-hand
   * side of an {@link CodeChunk.WithValue#assign assignment}).
   */
  @Immutable
  public abstract static class WithValue extends CodeChunk {
    // Do not put public static constants or methods on this class.  If you do then this can trigger
    // classloading deadlocks due to cyclic references between this class, CodeChunk and the
    // implementation class of the constant.

    WithValue() {
      /* no subclasses outside this package */
    }

    public final CodeChunk.WithValue plus(CodeChunk.WithValue rhs) {
      return BinaryOperation.create(Operator.PLUS, this, rhs);
    }

    public final CodeChunk.WithValue minus(CodeChunk.WithValue rhs) {
      return BinaryOperation.create(Operator.MINUS, this, rhs);
    }

    public final CodeChunk.WithValue plusEquals(CodeChunk.WithValue rhs) {
      return BinaryOperation.create(
          "+=",
          0, // the precedence of JS assignments (including +=) is lower than any Soy operator
          Associativity.RIGHT,
          this,
          rhs);
    }

    public final CodeChunk.WithValue doubleEquals(CodeChunk.WithValue rhs) {
      return BinaryOperation.create(Operator.EQUAL, this, rhs);
    }

    public final CodeChunk.WithValue doubleNotEquals(CodeChunk.WithValue rhs) {
      return BinaryOperation.create(Operator.NOT_EQUAL, this, rhs);
    }

    public final CodeChunk.WithValue tripleEquals(CodeChunk.WithValue rhs) {
      return BinaryOperation.create(
          "===",
          Operator.EQUAL.getPrecedence(),
          Operator.EQUAL.getAssociativity(),
          this,
          rhs);
    }

    public final CodeChunk.WithValue doubleEqualsNull() {
      return doubleEquals(LITERAL_NULL);
    }

    public final CodeChunk.WithValue times(CodeChunk.WithValue rhs) {
      return BinaryOperation.create(Operator.TIMES, this, rhs);
    }

    public final CodeChunk.WithValue divideBy(CodeChunk.WithValue rhs) {
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
    public final CodeChunk.WithValue and(
        CodeChunk.WithValue rhs, CodeChunk.Generator codeGenerator) {
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
    public final CodeChunk.WithValue or(
        CodeChunk.WithValue rhs, CodeChunk.Generator codeGenerator) {
      return BinaryOperation.or(this, rhs, codeGenerator);
    }

    public final CodeChunk.WithValue op(Operator op, CodeChunk.WithValue rhs) {
      return BinaryOperation.operation(op, ImmutableList.of(this, rhs));
    }

    /** Takes in a String identifier for convenience, since that's what most use cases need. */
    public final CodeChunk.WithValue dotAccess(String identifier) {
      return Dot.create(this, id(identifier));
    }

    public final CodeChunk.WithValue bracketAccess(CodeChunk.WithValue arg) {
      return Bracket.create(this, arg);
    }

    public final CodeChunk.WithValue call(CodeChunk.WithValue... args) {
      return call(Arrays.asList(args));
    }

    public final CodeChunk.WithValue call(Iterable<? extends CodeChunk.WithValue> args) {
      return Call.create(this, ImmutableList.copyOf(args));
    }

    public final CodeChunk.WithValue instanceof_(CodeChunk.WithValue identifier) {
      // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Operators/Operator_Precedence
      // instanceof has the same precedence as LESS_THAN
      return BinaryOperation.create(
          "instanceof", Operator.LESS_THAN.getPrecedence(), Associativity.LEFT, this, identifier);
    }

    public final CodeChunk.WithValue assign(CodeChunk.WithValue rhs) {
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
     * <p>This method is designed for interoperability with parts of the JS codegen system that do
     * not understand code chunks. For example, when applying plugin functions, {@link
     * com.google.template.soy.jssrc.internal.TranslateExprNodeVisitor#visitFunctionNode} needs to
     * downgrade the plugin arguments from CodeChunk.WithValues to {@link JsExpr}s for the plugin
     * API to process. The result (a JsExpr) needs to be upgraded back to a CodeChunk.WithValue that
     * includes the initial statements from the original arguments.
     */
    public final CodeChunk.WithValue withInitialStatements(
        Iterable<? extends CodeChunk> initialStatements) {
      // If there are no new initial statements, return the current chunk.
      if (Iterables.isEmpty(initialStatements)) {
        return this;
      }
      // Otherwise, return a code chunk that includes all of the dependent code.
      return Composite.create(ImmutableList.copyOf(initialStatements), this);
    }

    /** Convenience method for {@code withInitialStatements(ImmutableList.of(statement))}. */
    public final CodeChunk.WithValue withInitialStatement(CodeChunk initialStatement) {
      return withInitialStatements(ImmutableList.of(initialStatement));
    }

    /**
     * Returns true if this chunk can be represented as a single expression. This method should be
     * rarely used, but is needed when interoperating with parts of the codegen system that do not
     * yet understand CodeChunks (e.g. {@link SoyJsSrcFunction}).
     */
    final boolean isRepresentableAsSingleExpression() {
      return Iterables.isEmpty(initialStatements());
    }

    /**
     * If this chunk can be represented as a single expression, returns that expression. If this
     * chunk cannot be represented as a single expression, returns an expression containing
     * references to a variable defined by the corresponding {@link #doFormatInitialStatements
     * initial statements}.
     *
     * <p>This method should rarely be used, but is needed when interoperating with parts of the
     * codegen system that do not yet understand CodeChunks (e.g. {@link SoyJsSrcFunction}).
     */
    public abstract JsExpr singleExprOrName();

    /**
     * If this chunk can be represented as a single expression, writes that single expression to the
     * buffer. If the chunk cannot be represented as a single expression, writes an expression to
     * the buffer containing references to a variable defined by the corresponding {@link
     * #doFormatInitialStatements initial statements}.
     *
     * <p>Must only be called by {@link FormattingContext#appendOutputExpression}.
     */
    abstract void doFormatOutputExpr(FormattingContext ctx);

    /**
     * Returns the initial statements associated with this value. The statements must be serialized
     * before this value (for example, they could contain declarations of variables referenced in
     * this value).
     *
     * <p>These are direct dependencies only, not transitive.
     */
    public abstract ImmutableSet<CodeChunk> initialStatements();

    /**
     * Returns {@code true} if the expression represented by this code chunk is so trivial that it
     * isn't worth storing it in a temporary if it needs to be referenced multiple times.
     *
     * <p>The default is {@code false}, only certain special code chunks return {@code true}.
     */
    public boolean isCheap() {
      return false;
    }
  }

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
   * Returns a sequence of JavaScript statements suitable for inserting into JS code
   * that is not managed by the CodeChunk DSL. The string is guaranteed to end in a newline.
   *
   * <p>Callers should use {@link #getCode()} when the CodeChunk DSL is managing the entire
   * code generation. getCode may drop variable declarations if there is no other code referencing
   * those variables.
   *
   * <p>By contrast, this method is provided for incremental migration to the CodeChunk DSL.
   * Variable declarations will not be dropped, since there may be gencode not managed by the
   * CodeChunk DSL that references them.
   *
   * TODO(user): remove.
   *
   * @param startingIndent The indent level of the foreign code into which this code
   *     will be inserted. This doesn't affect the correctness of the composed code,
   *     only its readability.
   *
   */
  public final String getStatementsForInsertingIntoForeignCodeAtIndent(int startingIndent) {
    String code = getCode(startingIndent);
    return code.endsWith("\n") ? code : code + "\n";
  }

  /**
   * Temporary method to ease migration to the CodeChunk DSL.
   *
   * <p>Because of the recursive nature of the JS codegen system, it is generally not possible
   * to convert one codegen method at a time to use the CodeChunk DSL.
   * However, the business logic inside those methods can be migrated incrementally.
   * Methods that do not yet use the CodeChunk DSL can "unwrap" inputs using this method
   * and "wrap" results using {@link CodeChunk#fromExpr(JsExpr)}. This is safe as long as
   * each CodeChunk generated for production code is
   * {@link CodeChunk.WithValue#isRepresentableAsSingleExpression}.
   *
   * TODO(user): remove.
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
   * code is {@link CodeChunk.WithValue#isRepresentableAsSingleExpression}.
   *
   * <p>TODO(user): remove.
   */
  public final JsExpr assertExprAndCollectRequires(RequiresCollector collector) {
    WithValue withValue = (WithValue) this;
    if (!withValue.isRepresentableAsSingleExpression()) {
      throw new IllegalStateException(String.format("Not an expr:\n%s", this.getCode()));
    }
    collectRequires(collector);
    return withValue.singleExprOrName();
  }

  /**
   * {@link #doFormatInitialStatements} and {@link CodeChunk.WithValue#doFormatOutputExpr} are the
   * main methods subclasses should override to control their formatting. Subclasses should only
   * override this method in the special case that a code chunk needs to control its formatting when
   * it is the only chunk being serialized. TODO(brndn): only one override, can probably be declared
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
    if (this instanceof WithValue) {
      outputExprs.appendOutputExpression((WithValue) this);
      outputExprs.append(';').endLine();
    }

    return initialStatements.concat(outputExprs).toString();
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

    /**
     * Creates a code chunk declaring an automatically-named variable initialized to the given
     * value.
     */
    public VariableDeclaration declare(CodeChunk.WithValue rhs) {
      return CodeChunk.declare(newVarName(), rhs);
    }

    /** Creates a code chunk declaring an automatically-named variable with no initializer. */
    public VariableDeclaration declare() {
      return VariableDeclaration.create(newVarName(), /*initializer=*/ null);
    }

    /**
     * Returns a code chunk representing an if-then-else condition.
     *
     * <p>If all the parameters are {@link WithValue#isRepresentableAsSingleExpression representable
     * as single expressions}, the returned chunk will use the JavaScript ternary syntax ({@code
     * predicate ? consequent : alternate}). Otherwise, the returned chunk will use JavaScript
     * conditional statement syntax: <code>
     *   var $tmp = null;
     *   if (predicate) {
     *     $tmp = consequent;
     *   } else {
     *     $tmp = alternate;
     *   }
     * </code>
     */
    public CodeChunk.WithValue conditionalExpression(
        CodeChunk.WithValue predicate,
        CodeChunk.WithValue consequent,
        CodeChunk.WithValue alternate) {
      if (predicate.initialStatements().containsAll(consequent.initialStatements())
          && predicate.initialStatements().containsAll(alternate.initialStatements())) {
        return Ternary.create(predicate, consequent, alternate);
      }
      return ifExpression(predicate, consequent).else_(alternate).build(this);
    }
  }
}
