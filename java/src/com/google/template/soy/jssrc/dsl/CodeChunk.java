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

import static com.google.template.soy.jssrc.dsl.OutputContext.STATEMENT;
import static com.google.template.soy.jssrc.dsl.OutputContext.TRAILING_EXPRESSION;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.ForOverride;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.Operator.Associativity;
import com.google.template.soy.jssrc.restricted.JsExpr;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

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
 * </code> TODO(user): do all JS code generation with this DSL (that is, remove
 * {@link com.google.template.soy.jssrc.internal.JsCodeBuilder}).
 */
public abstract class CodeChunk {

  /**
   * Creates a new code chunk from the given expression. The expression's precedence is preserved.
   */
  public static WithValue fromExpr(JsExpr expr) {
    return Leaf.create(expr);
  }

  /**
   * Creates a code chunk representing a JavaScript identifier.
   *
   * @throws IllegalStateException if rawText is not a valid JavaScript identifier.
   */
  public static WithValue id(String id) {
    Preconditions.checkState(ID.matcher(id).matches(), "not a valid id: %s", id);
    return Leaf.create(id);
  }

  /**
   * Creates a code chunk representing a JavaScript "dotted identifier".
   *
   * <p>"Dotted identifiers" are really just sequences of dot-access operations off some
   * base identifier, so this method is just a convenience for
   * <code>id(...).dotAccess(...)...</code>. It's provided because working with raw dot-separated
   * strings is common.
   *
   * @throws IllegalStateException if dotSeparatedIdentifiers is not a dot-separated sequence of
   * valid JavaScript identifiers.
   */
  public static WithValue dottedId(String dotSeparatedIdentifiers) {
    List<String> ids = Splitter.on('.').splitToList(dotSeparatedIdentifiers);
    Preconditions.checkState(
        !ids.isEmpty(),
        "not a dot-separated sequence of JavaScript identifiers: %s",
        dotSeparatedIdentifiers);
    CodeChunk.WithValue tip = id(ids.get(0));
    for (int i = 1; i < ids.size(); ++i) {
      tip = tip.dotAccess(ids.get(i));
    }
    return tip;
  }

  /**
   * Creates a code chunk representing a JavaScript string literal.
   * @param contents The contents of the string literal. The contents will be escaped appropriately
   * and embedded inside single quotes.
   */
  public static WithValue stringLiteral(String contents) {
    // Escape non-ASCII characters since browsers are inconsistent in how they interpret utf-8 in
    // JS source files.
    String escaped = BaseUtils.escapeToSoyString(contents, true /* shouldEscapeToAscii */);

    // </script in a JavaScript string will end the current script tag in most browsers. Escape the
    // forward slash in the string to get around this issue.
    escaped = escaped.replace("</script", "<\\/script");

    return Leaf.create(escaped);
  }

  /** Creates a code chunk representing a JavaScript number literal. */
  public static WithValue number(long value) {
    Preconditions.checkArgument(
        IntegerNode.isInRange(value), "Number is outside JS safe integer range: %s", value);
    return Leaf.create(Long.toString(value));
  }

  /** Creates a code chunk representing a JavaScript number literal. */
  public static WithValue number(double value) {
    return Leaf.create(Double.toString(value));
  }

  /**
   * Creates a new code chunk declaring a variable with the given name, initialized to the
   * given value.
   *
   * <p>Most callers should use {@link CodeChunk.Generator#declare(WithValue)}. This method should
   * only be used when this chunk is being inserted into foreign code that requires a variable
   * of the given name to exist.
   */
  public static WithValue declare(String varName, WithValue rhs) {
    return declare(null, varName, rhs);
  }

  public static WithValue declare(
      @Nullable String closureCompilerTypeExpression, String varName, WithValue rhs) {
    return Declaration.create(closureCompilerTypeExpression, varName, rhs);
  }

  /** Creates a code chunk representing the logical negation {@code !} of the given chunk. */
  public static WithValue not(CodeChunk.WithValue arg) {
    return PrefixUnaryOperation.create(Operator.NOT, arg);
  }

  /**
   * Returns a code chunk representing the {@code new} operator applied to the given constructor.
   * If you need to call the constructor with arguments, call {@link WithValue#call}
   * on the returned chunk.
   */
  public static WithValue new_(WithValue ctor) {
    return New.create(ctor);
  }

  /**
   * Returns a code chunk representing the given Soy operator applied to the given operands.
   *
   * @param codeGenerator Required in case the operator is {@link Operator#AND} or {@link
   *     Operator#OR} and temporary variables need to be allocated for short-circuiting behavior.
   *     Callers can pass null safely as long as the operator is neither AND nor OR, or if all of
   *     the operands are {@link WithValue#isRepresentableAsSingleExpression representable as single
   *     expressions}. TODO(brndn): if more than one caller needs to pass null, introduce an
   *     exploding code generator.
   */
  public static WithValue operation(
      Operator op, List<WithValue> operands, CodeChunk.Generator codeGenerator) {
    Preconditions.checkState(operands.size() == op.getNumOperands());
    switch (op.getNumOperands()) {
      case 1:
        return PrefixUnaryOperation.create(op, operands.get(0));
      case 2:
        // AND and OR have dedicated APIs to handle short-circuiting
        if (op == Operator.AND) {
          return operands.get(0).and(operands.get(1), codeGenerator);
        } else if (op == Operator.OR) {
          return operands.get(0).or(operands.get(1), codeGenerator);
        } else {
          return BinaryOperation.create(op, operands.get(0), operands.get(1));
        }
      case 3:
        Preconditions.checkArgument(op == Operator.CONDITIONAL);
        return Ternary.create(operands.get(0), operands.get(1), operands.get(2));
      default:
        throw new AssertionError();
    }
  }

  public static WithValue arrayLiteral(Iterable<? extends WithValue> elements) {
    return ArrayLiteral.create(ImmutableList.copyOf(elements));
  }

  public static WithValue mapLiteral(
      Iterable<? extends WithValue> keys,
      Iterable<? extends WithValue> values) {
    return MapLiteral.create(ImmutableList.copyOf(keys), ImmutableList.copyOf(values));
  }

  /** Returns a CodeChunk that represents a return statement returning the given value. */
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
  public static WithValue dontTrustPrecedenceOf(JsExpr couldHaveWrongPrecedence) {
    return Group.create(fromExpr(couldHaveWrongPrecedence));
  }

  /**
   * Creates a code chunk from the given text, treating it as a series of statements rather than an
   * expression.
   *
   * <p>The only valid use case for this method is for interoperability with existing codegen. Do
   * not use for new code.
   *
   * <p>TODO(user): remove.
   */
  public static CodeChunk treatRawStringAsStatementLegacyOnly(
      String rawString, Iterable<String> requires) {
    return LeafStatement.create(rawString.trim(), requires);
  }

  /**
   * See https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Grammar_and_types#Variables
   *
   * <p>This incorrectly allows keywords, but that's not a big problem because doing so would cause
   * JSCompiler to crash. This also incorrectly disallows Unicode in identifiers, but that's not a
   * big problem because the JS backend generally names identifiers after Soy identifiers, which
   * don't allow Unicode either.
   */
  private static final Pattern ID = Pattern.compile("[A-Za-z_$][\\w$]*");

  /**
   * Marker class for a chunk of code that represents a value.
   *
   * <p>Expressions represent values. Sequences of statements can represent a value
   * (for example, if the first statement declares a variable and subsequent statements
   * update the variable's state), but they are not required to.
   *
   * <p>Chunks representing values are required in certain contexts
   * (for example, the right-hand side of an {@link CodeChunk.Builder#assign assignment}).
   */
  public abstract static class WithValue extends CodeChunk {

    public static final WithValue LITERAL_TRUE = id("true");
    public static final WithValue LITERAL_FALSE = id("false");
    public static final WithValue LITERAL_NULL = id("null");
    public static final WithValue LITERAL_EMPTY_STRING = Leaf.create("''");

    WithValue() { /* no subclasses outside this package */ }

    public CodeChunk.WithValue plus(CodeChunk.WithValue rhs) {
      return BinaryOperation.create(Operator.PLUS, this, rhs);
    }

    public CodeChunk.WithValue minus(CodeChunk.WithValue rhs) {
      return BinaryOperation.create(Operator.MINUS, this, rhs);
    }

    public CodeChunk.WithValue plusEquals(CodeChunk.WithValue rhs) {
      return BinaryOperation.create(
          "+=",
          0, // the precedence of JS assignments (including +=) is lower than any Soy operator
          Associativity.RIGHT,
          this,
          rhs);
    }

    public CodeChunk.WithValue doubleEquals(CodeChunk.WithValue rhs) {
      return BinaryOperation.create(Operator.EQUAL, this, rhs);
    }

    public CodeChunk.WithValue doubleNotEquals(CodeChunk.WithValue rhs) {
      return BinaryOperation.create(Operator.NOT_EQUAL, this, rhs);
    }

    public CodeChunk.WithValue tripleEquals(CodeChunk.WithValue rhs) {
      return BinaryOperation.create(
          "===",
          Operator.EQUAL.getPrecedence(),
          Operator.EQUAL.getAssociativity(),
          this,
          rhs);
    }

    public CodeChunk.WithValue doubleEqualsNull() {
      return doubleEquals(LITERAL_NULL);
    }

    public CodeChunk.WithValue times(CodeChunk.WithValue rhs) {
      return BinaryOperation.create(Operator.TIMES, this, rhs);
    }

    public CodeChunk.WithValue divideBy(CodeChunk.WithValue rhs) {
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
    public CodeChunk.WithValue and(CodeChunk.WithValue rhs, CodeChunk.Generator codeGenerator) {
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
    public CodeChunk.WithValue or(CodeChunk.WithValue rhs, CodeChunk.Generator codeGenerator) {
      return BinaryOperation.or(this, rhs, codeGenerator);
    }

    CodeChunk.WithValue mod(CodeChunk.WithValue rhs) {
      return BinaryOperation.create(Operator.MOD, this, rhs);
    }

    /** Takes in a String identifier for convenience, since that's what most use cases need. */
    public CodeChunk.WithValue dotAccess(String identifier) {
      return Dot.create(this, id(identifier));
    }

    public CodeChunk.WithValue bracketAccess(CodeChunk.WithValue arg) {
      return Bracket.create(this, arg);
    }

    public CodeChunk.WithValue call(CodeChunk.WithValue... args) {
      return call(Arrays.asList(args));
    }

    public CodeChunk.WithValue call(Iterable<? extends CodeChunk.WithValue> args) {
      return Call.create(this, ImmutableList.copyOf(args));
    }

    public CodeChunk.WithValue instanceof_(String typeString) {
      // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Operators/Operator_Precedence
      // instanceof has the same precedence as LESS_THAN
      return BinaryOperation.create(
          "instanceof",
          Operator.LESS_THAN.getPrecedence(),
          Associativity.LEFT,
          this,
          dottedId(typeString));
    }

    public CodeChunk.WithValue assign(CodeChunk.WithValue rhs) {
      return BinaryOperation.create(
          "=",
          0, // the precedence of JS assignments is lower than any Soy operator
          Associativity.RIGHT,
          this,
          rhs);
    }

    /**
     * Returns true if this chunk can be represented as a single expression. This method should be
     * rarely used, but is needed when interoperating with parts of the codegen system that do not
     * yet understand CodeChunks (e.g. {@link SoyJsSrcFunction}).
     */
    public abstract boolean isRepresentableAsSingleExpression();

    /**
     * If this chunk can be represented as a single expression, returns that expression. If this
     * chunk cannot be represented as a single expression, returns an expression containing
     * references to variables defined by the corresponding {@link #formatInitialStatements initial
     * statements}.
     *
     * <p>This method should rarely be used, but is needed when interoperating with parts of the
     * codegen system that do not yet understand CodeChunks (e.g. {@link SoyJsSrcFunction}).
     */
    public abstract JsExpr singleExprOrName();

    /**
     * If this chunk can be represented as a single expression, writes that single expression to the
     * buffer. If the chunk cannot be represented as a single expression, writes an expression to
     * the buffer containing references to variables defined by the corresponding {@link
     * #formatInitialStatements initial statements}.
     *
     * <p>Must only be called by {@link #formatOutputExpr}.
     *
     * @param outputContext The surrounding context where the expression is inserted.
     */
    @ForOverride
    abstract void doFormatOutputExpr(FormattingContext ctx, OutputContext outputContext);

    /**
     * Layer of indirection to check if the output expression should end in a semicolon+newline.
     * Subclasses must call this, and not {@link #doFormatOutputExpr}.
     *
     * @param outputContext The surrounding context where the expression is inserted.
     */
    final void formatOutputExpr(FormattingContext ctx, OutputContext outputContext) {
      doFormatOutputExpr(ctx, outputContext);
      // If the expression will appear as its own statement, add a trailing semicolon and newline.
      // The exception is Composites. Composites are sequences of statements that have a variable
      // allocated to represent them when they appear in other code chunks. They should not produce
      // any expression output when formatted by themselves.
      if (outputContext == STATEMENT && !(this instanceof Composite)) {
        ctx.append(';').endLine();
      }
    }

    @Override
    final void formatAllStatements(FormattingContext ctx, boolean moreToCome) {
      super.formatAllStatements(ctx, true);
      formatOutputExpr(ctx, OutputContext.STATEMENT);
    }
  }

  /**
   * A trivial interface for {@link #collectRequires(RequiresCollector)} that can be used to collect
   * all required namespaces from a code chunk.
   */
  public interface RequiresCollector {
    void add(String require);
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
    return getCode(0, OutputContext.STATEMENT, false /* moreToCome */);
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
    String code = getCode(startingIndent, STATEMENT, true /* moreToCome */);
    return code.endsWith("\n") ? code : code + "\n";
  }

  /**
   * Returns a sequence of JavaScript statements. In the special case that this chunk is
   * representable as a single expression, returns that expression
   * <em>without</em> a trailing semicolon. (By contrast, {@link #getCode()} does send
   * the trailing semicolon in such cases.)
   *
   * <p>This method is generally not safe, since concatenating statements that do not end
   * in semicolons can cause arbitrary lexical errors. It's intended for use by unit tests
   * whose assertions are currently written without trailing semicolons.
   * TODO: migrate the unit tests and delete this method.
   */
  @VisibleForTesting
  public final String getExpressionTestOnly() {
    return getCode(0, TRAILING_EXPRESSION, false /* moreToCome */);
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
    WithValue withValue = (WithValue) this;
    Preconditions.checkState(withValue.isRepresentableAsSingleExpression(),
        "Not an expr:\n%s", this.getCode());
    return withValue.singleExprOrName();
  }

  /**
   * {@link #doFormatInitialStatements} and {@link CodeChunk.WithValue#doFormatOutputExpr}
   * are the main methods subclasses should override to control their formatting.
   * Subclasses should only override this method in the special case that a code chunk
   * needs to control its formatting when it is the only chunk being serialized.
   * TODO(brndn): only one override, can probably be declared final.
   *
   * @param startingIndent The indent level of the foreign code into which this code
   *     will be inserted. This doesn't affect the correctness of the composed code,
   *     only its readability.
   * @param outputContext The grammatical context into which the output expression generated by
   *     this chunk (if any) will be inserted.
   *     <ul>
   *       <li>{@link OutputContext#STATEMENT}: the output expression will appear as its own
   *           statement. Include a trailing semicolon and newline.
   *       <li>{@link OutputContext#EXPRESSION}: the output expression is being inserted into
   *           another expression. Omit the trailing semicolon and newline.
   *       <li>{@link OutputContext#TRAILING_EXPRESSION}: the output expression is being inserted
   *           into another expression, but it is the last component of the entire unit of code
   *           that is being {@link CodeChunk#getCode() formatted}. There is therefore no need
   *           to serialize the name of variable that holds this expression's value, if any
   *           (since there is no following code that could reference it).
   *     </ul>
   */
  @ForOverride
  String getCode(int startingIndent, OutputContext outputContext, boolean moreToCome) {
    // Format the code backwards (output expression, then initial statements)
    // so the initial statements know whether anything is following them.

    FormattingContext outputExprs = new FormattingContext(startingIndent);
    if (this instanceof WithValue) {
      ((WithValue) this).formatOutputExpr(outputExprs, outputContext);
    }

    FormattingContext initialStatements = new FormattingContext(startingIndent);
    formatInitialStatements(initialStatements, !outputExprs.isEmpty());

    // Now put them back into the right order.
    return initialStatements
        .concat(outputExprs)
        .toString();
  }

  /**
   * Layer of indirection to check that the initial statements of a code chunk are executed at most
   * once. Subclasses must call this, and not {@link #doFormatInitialStatements}.
   *
   * @param moreToCome True if this is not the last code chunk in its sequence.
   */
  final void formatInitialStatements(FormattingContext ctx, boolean moreToCome) {
    if (ctx.shouldFormat(this)) {
      doFormatInitialStatements(ctx, moreToCome);
    }
  }

  /**
   * If this chunk can be represented as a single expression, does nothing. If this chunk cannot be
   * represented as a single expression, writes everything except the final expression to the
   * buffer. Must only be called by {@link #formatInitialStatements}.
   *
   * @param moreToCome True if this is not the last code chunk in its sequence.
   */
  @ForOverride
  abstract void doFormatInitialStatements(FormattingContext ctx, boolean moreToCome);

  /**
   * Writes zero or more JavaScript statements representing this code chunk to the given buffer.
   *
   * @param moreToCome True if this is not the last code chunk in its sequence.
   */
  void formatAllStatements(FormattingContext ctx, boolean moreToCome) {
    formatInitialStatements(ctx, moreToCome);
    // overridden by CodeChunk.WithValue to format the output expression too
  }

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
     * Creates a new code chunk declaring an automatically-named variable initialized to the given
     * value.
     */
    public CodeChunk.WithValue declare(CodeChunk.WithValue rhs) {
      return CodeChunk.declare(newVarName(), rhs);
    }

    /** Returns a builder for a new code chunk that is not initialized to any value. */
    public Builder newChunk() {
      return new Builder(this);
    }

    /** Returns a builder for a new code chunk whose value is initially the given value. */
    public Builder newChunk(CodeChunk.WithValue initialValue) {
      return new Builder(this).assign(initialValue);
    }
  }

  /** Builds a single {@link CodeChunk}. */
  public static final class Builder {
    private final Generator owner;
    private final ImmutableList.Builder<CodeChunk> children;

    /**
     * The first {@link #assign assignment} to this chunk should also create a declaration. This
     * variable keeps track of that.
     */
    @Nullable private String varName;

    private Builder(Generator owner) {
      this.owner = owner;
      this.children = ImmutableList.builder();
    }

    /** Adds the statement represented by the given chunk to this sequence. */
    public Builder statement(CodeChunk chunk) {
      children.add(Statement.create(chunk));
      return this;
    }

    /** Adds the statements represented by the given chunks to this sequence. */
    public Builder statements(Iterable<? extends CodeChunk> statements) {
      for (CodeChunk statement : statements) {
        this.statement(statement);
      }
      return this;
    }

    /** Starts a conditional statement beginning with the given predicate and consequent chunks. */
    public ConditionalBuilder if_(CodeChunk.WithValue predicate, CodeChunk consequent) {
      return new ConditionalBuilder(predicate, consequent, this);
    }

    /** Sets the value represented by this code chunk to be the given chunk.*/
    public Builder assign(CodeChunk.WithValue rhs) {
      if (varName != null) {
        children.add(Assignment.create(varName(), rhs));
      } else if (rhs instanceof Declaration) {
        // If this is the first assignment to this code chunk, reuse the declaration's variable name
        // instead of making an alias.
        children.add(rhs);
        varName = ((Declaration) rhs).varName();
      } else {
        children.add(Declaration.create(null, varName(), rhs));
      }
      return this;
    }

    private String varName() {
      if (varName == null) {
        varName = owner.newVarName();
      }
      return varName;
    }

    /** Returns a {@link CodeChunk} built from this builder's state. */
    public CodeChunk build() {
      ImmutableList<CodeChunk> chunks = children.build();
      Preconditions.checkState(!chunks.isEmpty(),
          "CodeChunk.Builder with no chunks makes no sense");

      return chunks.size() > 1
          ? Composite.create(chunks, varName())
          : chunks.get(0); // no point in returning a composite with 1 child. return child itself.
    }

    /**
     * Returns a {@link CodeChunk.WithValue} built from this builder's state.
     * @throws ClassCastException if the code does not actually represent a value.
     *     Such an exception indicates a programmer error.
     */
    public CodeChunk.WithValue buildAsValue() {
      CodeChunk chunk = build();
      return chunk instanceof Conditional
          ? createConditionalExpression((Conditional) chunk)
          : (CodeChunk.WithValue) chunk;
    }

    void addChild(CodeChunk child) {
      children.add(child);
    }

    /**
     * If every branch in an {@code if}-{@code else if}-{@code else} statement represents a value,
     * the whole statement represents a value, namely that of the taken branch. Make this explicit
     * by declaring a variable before the statement and assigning into it in every branch.
     */
    private CodeChunk.WithValue createConditionalExpression(Conditional conditional) {
      Preconditions.checkState(conditional.everyBranchHasAValue());
      CodeChunk.WithValue var = owner.declare(WithValue.LITERAL_NULL);
      ConditionalBuilder builder = null;
      for (IfThenPair oldCondition : conditional.conditions()) {
        CodeChunk.WithValue newConsequent =
            var.assign((CodeChunk.WithValue) oldCondition.consequent);
        if (builder == null) {
          builder = owner
              .newChunk()
              .assign(var)
              .if_(oldCondition.predicate, newConsequent);
        } else {
          builder.elseif_(oldCondition.predicate, newConsequent);
        }
      }
      return (CodeChunk.WithValue) builder
          .else_(var.assign((CodeChunk.WithValue) conditional.trailingElse()))
          .endif()
          .build();
    }
  }
}
