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
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.LOGGING_ADVISING_APPENDABLE_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.LOGGING_ADVISING_BUILDER_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.Dir;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingFunctionInvocation;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.Statement;
import java.util.List;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

/**
 * An expression for an {@link
 * com.google.template.soy.jbcsrc.api.AdvisingAppendable.AdvisingAppendable}.
 */
final class AppendableExpression extends Expression {
  private static final MethodRef APPEND =
      MethodRef.create(LoggingAdvisingAppendable.class, "append", CharSequence.class)
          .asNonNullable();

  private static final MethodRef APPEND_CHAR =
      MethodRef.create(LoggingAdvisingAppendable.class, "append", char.class).asNonNullable();

  private static final MethodRef SOFT_LIMITED =
      MethodRef.create(LoggingAdvisingAppendable.class, "softLimitReached").asCheap();

  static final MethodRef ENTER_LOGGABLE_STATEMENT =
      MethodRef.create(LoggingAdvisingAppendable.class, "enterLoggableElement", LogStatement.class)
          .asNonNullable();

  private static final MethodRef EXIT_LOGGABLE_STATEMENT =
      MethodRef.create(LoggingAdvisingAppendable.class, "exitLoggableElement").asNonNullable();

  private static final MethodRef APPEND_LOGGING_FUNCTION_INVOCATION =
      MethodRef.create(
              LoggingAdvisingAppendable.class,
              "appendLoggingFunctionInvocation",
              LoggingFunctionInvocation.class,
              ImmutableList.class)
          .asNonNullable();

  private static final MethodRef LOGGING_FUNCTION_INVOCATION_CREATE =
      MethodRef.create(
              LoggingFunctionInvocation.class, "create", String.class, String.class, List.class)
          .asNonNullable();

  private static final MethodRef SET_SANITIZED_CONTENT_KIND =
      MethodRef.create(
              LoggingAdvisingAppendable.class, "setSanitizedContentKind", ContentKind.class)
          .asNonNullable()
          .asCheap();

  private static final MethodRef SET_SANITIZED_CONTENT_DIRECTIONALITY =
      MethodRef.create(
              LoggingAdvisingAppendable.class, "setSanitizedContentDirectionality", Dir.class)
          .asNonNullable()
          .asCheap();

  private static final MethodRef FLUSH_BUFFERS =
      MethodRef.create(LoggingAdvisingAppendable.class, "flushBuffers", int.class);

  static AppendableExpression forExpression(Expression delegate) {
    return new AppendableExpression(
        delegate, /* hasSideEffects= */ false, /* supportsSoftLimiting= */ true);
  }

  static AppendableExpression forStringBuilder(Expression delegate) {
    checkArgument(delegate.resultType().equals(LOGGING_ADVISING_BUILDER_TYPE));
    return new AppendableExpression(
        BytecodeUtils.LOGGING_ADVISING_BUILDER_TYPE,
        delegate,
        /* hasSideEffects= */ false,
        /* supportsSoftLimiting= */ false);
  }

  static AppendableExpression logger() {
    return new AppendableExpression(
        MethodRef.RUNTIME_LOGGER.invoke(),
        /* hasSideEffects= */ false,
        /* supportsSoftLimiting= */ false);
  }

  private final Expression delegate;
  // Whether or not the expression contains operations with side effects (e.g. appends)
  private final boolean hasSideEffects;
  // Whether or not the appendable could ever return true from softLimitReached
  private final boolean supportsSoftLimiting;

  private AppendableExpression(
      Expression delegate, boolean hasSideEffects, boolean supportsSoftLimiting) {
    this(LOGGING_ADVISING_APPENDABLE_TYPE, delegate, hasSideEffects, supportsSoftLimiting);
  }

  private AppendableExpression(
      Type resultType, Expression delegate, boolean hasSideEffects, boolean supportsSoftLimiting) {
    super(resultType, delegate.features());
    delegate.checkAssignableTo(LOGGING_ADVISING_APPENDABLE_TYPE);
    checkArgument(
        delegate.isNonNullable(),
        "advising appendable expressions should always be non nullable: %s",
        delegate);
    this.delegate = delegate;
    this.hasSideEffects = hasSideEffects;
    this.supportsSoftLimiting = supportsSoftLimiting;
  }

  @Override
  protected void doGen(CodeBuilder adapter) {
    delegate.gen(adapter);
  }

  /**
   * Returns a similar {@link AppendableExpression} but with the given (string valued) expression
   * appended to it.
   */
  AppendableExpression appendString(Expression exp) {
    return withNewDelegate(delegate.invoke(APPEND, exp), true);
  }

  /**
   * Returns a similar {@link AppendableExpression} but with the given (char valued) expression
   * appended to it.
   */
  AppendableExpression appendChar(Expression exp) {
    return withNewDelegate(delegate.invoke(APPEND_CHAR, exp), true);
  }

  /** Returns an expression with the result of {@link AppendableExpression#softLimitReached}. */
  Expression softLimitReached() {
    checkArgument(supportsSoftLimiting);
    return delegate.invoke(SOFT_LIMITED);
  }

  /** Invokes {@link LoggingAdvisingAppendable#enterLoggableElement} on the appendable. */
  AppendableExpression enterLoggableElement(Expression logStatement) {
    return withNewDelegate(delegate.invoke(ENTER_LOGGABLE_STATEMENT, logStatement), true);
  }

  /** Invokes {@link LoggingAdvisingAppendable#enterLoggableElement} on the appendable. */
  AppendableExpression exitLoggableElement() {
    return withNewDelegate(delegate.invoke(EXIT_LOGGABLE_STATEMENT), true);
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
        delegate.invoke(
            APPEND_LOGGING_FUNCTION_INVOCATION,
            LOGGING_FUNCTION_INVOCATION_CREATE.invoke(
                constant(functionName),
                constant(placeholderValue),
                SoyExpression.asBoxedList(args)),
            BytecodeUtils.asImmutableList(escapingDirectives)),
        true);
  }

  /** Invokes {@link LoggingAdvisingAppendable#setSanitizedContentKind} on the appendable. */
  AppendableExpression setSanitizedContentKind(SanitizedContentKind kind) {
    return withNewDelegate(
        delegate.invoke(
            SET_SANITIZED_CONTENT_KIND,
            BytecodeUtils.constantSanitizedContentKindAsContentKind(kind)),
        true);
  }

  /**
   * Invokes {@link LoggingAdvisingAppendable#setSanitizedContentDirectionality} on the appendable.
   */
  AppendableExpression setSanitizedContentDirectionality(Dir contentDir) {
    return withNewDelegate(
        delegate.invoke(SET_SANITIZED_CONTENT_DIRECTIONALITY, BytecodeUtils.constant(contentDir)),
        true);
  }

  Statement flushBuffers(int depth) {
    return delegate.invokeVoid(FLUSH_BUFFERS, constant(depth));
  }

  @Override
  public AppendableExpression labelStart(Label label) {
    return withNewDelegate(delegate.labelStart(label), this.hasSideEffects);
  }

  @Override
  public Statement toStatement() {
    // .toStatement() by default just generates the expression and adds a 'POP' instruction
    // to clear the stack. However, this is only neccesary when the expression in question has a
    // side effect worth preserving.  If we know that it does not we can just return the empty
    // statement
    if (hasSideEffects) {
      return super.toStatement();
    }
    return Statement.NULL_STATEMENT;
  }

  private AppendableExpression withNewDelegate(Expression newDelegate, boolean hasSideEffects) {
    return new AppendableExpression(newDelegate, hasSideEffects, supportsSoftLimiting);
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
