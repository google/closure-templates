/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.jbcsrc;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.BUFFERING_APPENDABLE_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.LOGGING_ADVISING_APPENDABLE_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.MULTIPLEXING_APPENDABLE_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;
import static java.util.Arrays.asList;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingFunctionInvocation;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.MethodRefs;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.objectweb.asm.Label;

/** An expression for an {@link com.google.template.soy.jbcsrc.api.AdvisingAppendable}. */
final class AppendableExpression extends Expression {

  static final class AppendableStatement extends Statement {
    private final AppendableExpression expression;

    AppendableStatement(AppendableExpression expression) {
      this.expression = expression;
    }

    @Override
    protected void doGen(CodeBuilder adapter) {
      expression.gen(adapter);
      adapter.pop();
    }

    @Override
    public AppendableStatement withSourceLocation(SourceLocation sourceLocation) {
      return new AppendableStatement(expression.withSourceLocation(sourceLocation));
    }

    @Override
    public AppendableStatement labelEnd(Label label) {
      return new AppendableStatement(expression.labelEnd(label));
    }

    public Expression thenInvoke(Expression delegate, MethodRef method) {
      if (delegate == expression.delegate) {
        Expression resolved = expression.resolved;
        // LoggingAdvisingAppednable methods always 'return this' but it isn't always statically
        // typed. So we may need to insert a cast operator.
        if (!expression.resolved.resultType().equals(delegate.resultType())) {
          resolved = resolved.checkedCast(delegate.resultType());
        }
        return resolved.invoke(method);
      }
      return super.then(delegate.invoke(method));
    }
  }

  static Statement concat(Statement... statements) {
    return concat(asList(statements));
  }

  static Statement concat(List<Statement> statements) {
    List<Statement> output = new ArrayList<>();
    AppendableStatement prev = null;
    for (Statement maybeConcat : statements) {
      for (Statement stmt : maybeConcat.asStatements()) {
        if (stmt.equals(Statement.NULL_STATEMENT)) {
          continue;
        }
        if (stmt instanceof AppendableStatement) {
          var appendableStmt = (AppendableStatement) stmt;
          if (prev == null) {
            prev = appendableStmt;
            continue;
          }
          var prevExpession = prev.expression;
          var expr = appendableStmt.expression;
          if (prevExpession.delegate == expr.delegate) {
            // Fuse the two append operations together.
            prev = new AppendableStatement(prevExpession.withNewDelegate(expr.op));
            continue;
          }
          output.add(prev);
          prev = appendableStmt;
        } else {
          if (prev != null) {
            output.add(prev);
            prev = null;
          }
          output.add(stmt);
        }
      }
    }
    // This means we are ending with an appendable statement, merge the prefix but keep the end
    if (prev != null) {
      if (output.isEmpty()) {
        return prev;
      }
      output.add(prev);
    }

    return Statement.concat(output);
  }

  private static final MethodRef APPEND =
      MethodRef.createNonPure(LoggingAdvisingAppendable.class, "append", CharSequence.class);
  private static final MethodRef APPEND_CHAR =
      MethodRef.createNonPure(LoggingAdvisingAppendable.class, "append", char.class);

  private static final MethodRef SOFT_LIMITED =
      MethodRef.createNonPure(LoggingAdvisingAppendable.class, "softLimitReached").asCheap();

  static final MethodRef ENTER_LOGGABLE_STATEMENT =
      MethodRef.createNonPure(
          LoggingAdvisingAppendable.class, "enterLoggableElement", LogStatement.class);

  private static final MethodRef EXIT_LOGGABLE_STATEMENT =
      MethodRef.createNonPure(LoggingAdvisingAppendable.class, "exitLoggableElement");

  private static final MethodRef APPEND_LOGGING_FUNCTION_INVOCATION =
      MethodRef.createNonPure(
          LoggingAdvisingAppendable.class,
          "appendLoggingFunctionInvocation",
          LoggingFunctionInvocation.class,
          ImmutableList.class);

  private static final MethodRef LOGGING_FUNCTION_INVOCATION_CREATE =
      MethodRef.createPure(
          LoggingFunctionInvocation.class, "create", String.class, String.class, List.class);

  private static final MethodRef SET_SANITIZED_CONTENT_KIND_AND_DIRECTIONALITY =
      MethodRef.createNonPure(
              LoggingAdvisingAppendable.class, "setKindAndDirectionality", ContentKind.class)
          .asCheap();

  private static final MethodRef FLUSH_BUFFERS =
      MethodRef.createNonPure(LoggingAdvisingAppendable.class, "flushBuffers", int.class);

  private static final MethodRef FLUSH_PENDING_LOGGING_ATTRBIUTES =
      MethodRef.createNonPure(
          LoggingAdvisingAppendable.class, "flushPendingLoggingAttributes", boolean.class);

  static AppendableExpression forExpression(Expression delegate) {
    return new AppendableExpression(delegate, e -> e, /* supportsSoftLimiting= */ true);
  }

  static AppendableExpression forStringBuilder(Expression delegate) {
    checkArgument(
        delegate.resultType().equals(BUFFERING_APPENDABLE_TYPE)
            || delegate.resultType().equals(MULTIPLEXING_APPENDABLE_TYPE));
    return new AppendableExpression(delegate, e -> e, /* supportsSoftLimiting= */ false);
  }

  static AppendableExpression logger() {
    return new AppendableExpression(
        MethodRefs.RUNTIME_LOGGER.invoke(), e -> e, /* supportsSoftLimiting= */ false);
  }

  private final Expression delegate;
  private final Expression resolved;
  private final Function<Expression, Expression> op;
  // Whether or not the appendable could ever return true from softLimitReached
  private final boolean supportsSoftLimiting;

  private AppendableExpression(
      Expression delegate, Function<Expression, Expression> op, boolean supportsSoftLimiting) {
    super(delegate.resultType(), delegate.features());
    delegate.checkAssignableTo(LOGGING_ADVISING_APPENDABLE_TYPE);
    checkArgument(
        delegate.isNonJavaNullable(),
        "advising appendable expressions should always be non nullable: %s",
        delegate);
    this.delegate = delegate;
    this.resolved = op.apply(delegate);
    this.op = op;
    this.supportsSoftLimiting = supportsSoftLimiting;
  }

  @Override
  protected void doGen(CodeBuilder adapter) {
    resolved.gen(adapter);
  }

  /**
   * Returns a similar {@link AppendableExpression} but with the given (string valued) expression
   * appended to it.
   */
  AppendableExpression appendString(Expression exp) {
    return withNewDelegate(e -> e.invoke(APPEND, exp));
  }

  /**
   * Returns a similar {@link AppendableExpression} but with the given (char valued) expression
   * appended to it.
   */
  AppendableExpression appendChar(Expression exp) {
    return withNewDelegate(e -> e.invoke(APPEND_CHAR, exp));
  }

  /** Returns an expression with the result of {@link AppendableExpression#softLimitReached}. */
  Expression softLimitReached() {
    checkArgument(supportsSoftLimiting);
    return delegate.invoke(SOFT_LIMITED);
  }

  /** Invokes {@link LoggingAdvisingAppendable#enterLoggableElement} on the appendable. */
  AppendableExpression enterLoggableElement(Expression logStatement) {
    return withNewDelegate(e -> e.invoke(ENTER_LOGGABLE_STATEMENT, logStatement));
  }

  /** Invokes {@link LoggingAdvisingAppendable#enterLoggableElement} on the appendable. */
  AppendableExpression exitLoggableElement() {
    return withNewDelegate(e -> e.invoke(EXIT_LOGGABLE_STATEMENT));
  }

  /**
   * Invokes {@link LoggingAdvisingAppendable#appendLoggingFunctionInvocation} on the appendable.
   */
  AppendableExpression appendLoggingFunctionInvocation(
      String functionName,
      String placeholderValue,
      List<SoyExpression> args,
      List<Expression> escapingDirectives) {
    return withNewDelegate(
        e ->
            e.invoke(
                APPEND_LOGGING_FUNCTION_INVOCATION,
                LOGGING_FUNCTION_INVOCATION_CREATE
                    .invoke(
                        constant(functionName),
                        constant(placeholderValue),
                        // TODO(lukes): nearly all implementations don't want `null`, change them to
                        // accept NullData and perform a regular boxing conversion here.
                        SoyExpression.boxListWithSoyNullishAsJavaNull(args))
                    .toMaybeConstant(),
                BytecodeUtils.asImmutableList(escapingDirectives)));
  }

  /** Invokes {@link LoggingAdvisingAppendable#setSanitizedContentKind} on the appendable. */
  AppendableExpression setSanitizedContentKindAndDirectionality(SanitizedContentKind kind) {
    return withNewDelegate(
        e ->
            e.invoke(
                SET_SANITIZED_CONTENT_KIND_AND_DIRECTIONALITY,
                BytecodeUtils.constantSanitizedContentKindAsContentKind(kind)));
  }

  AppendableExpression flushPendingLoggingAttributes(boolean isAnchorTag) {
    return withNewDelegate(e -> e.invoke(FLUSH_PENDING_LOGGING_ATTRBIUTES, constant(isAnchorTag)));
  }

  Statement flushBuffers(int depth) {
    return delegate.invokeVoid(FLUSH_BUFFERS, constant(depth));
  }

  @Override
  public Expression labelStart(Label label) {
    // We cannot return an AppendableExpression in this case.  Because we intend to fuse the 'base'
    // expression with other appends, the 'start' will change.
    return super.labelStart(label);
  }

  @Override
  public AppendableExpression labelEnd(Label label) {
    return withNewDelegate(e -> e.labelEnd(label));
  }

  @Override
  public AppendableExpression withSourceLocation(SourceLocation location) {
    return withNewDelegate(e -> e.withSourceLocation(location));
  }

  @Override
  public AppendableStatement toStatement() {
    return new AppendableStatement(this);
  }

  private AppendableExpression withNewDelegate(Function<Expression, Expression> op) {
    return new AppendableExpression(delegate, this.op.andThen(op), supportsSoftLimiting);
  }

  /**
   * Returns {@code true} if this expression requires detach logic to be generated based on runtime
   * calls to {@link
   * com.google.template.soy.jbcsrc.api.AdvisingAppendableAdvisingAppendable#softLimitReached()}.
   */
  boolean supportsSoftLimiting() {
    return supportsSoftLimiting;
  }
}
