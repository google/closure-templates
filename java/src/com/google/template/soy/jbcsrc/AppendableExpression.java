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
import static com.google.template.soy.jbcsrc.BytecodeUtils.ADVISING_APPENDABLE_TYPE;
import static com.google.template.soy.jbcsrc.BytecodeUtils.ADVISING_BUILDER_TYPE;

import com.google.template.soy.jbcsrc.api.AdvisingAppendable;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

/** An expression for an {@link AdvisingAppendable}. */
final class AppendableExpression extends Expression {
  private static final MethodRef APPEND =
      MethodRef.create(AdvisingAppendable.class, "append", CharSequence.class).asNonNullable();

  private static final MethodRef APPEND_CHAR =
      MethodRef.create(AdvisingAppendable.class, "append", char.class).asNonNullable();

  private static final MethodRef SOFT_LIMITED =
      MethodRef.create(AdvisingAppendable.class, "softLimitReached").asCheap();

  static AppendableExpression forLocal(LocalVariable delegate) {
    return new AppendableExpression(
        delegate, false /* hasSideEffects*/, true /* supportsSoftLimiting */);
  }

  static AppendableExpression forStringBuilder(Expression delegate) {
    checkArgument(delegate.resultType().equals(ADVISING_BUILDER_TYPE));
    return new AppendableExpression(
        ADVISING_BUILDER_TYPE,
        delegate,
        false /* hasSideEffects*/,
        false /* supportsSoftLimiting */);
  }

  static AppendableExpression logger() {
    return new AppendableExpression(
        MethodRef.RUNTIME_LOGGER.invoke(),
        false /* hasSideEffects*/,
        false /* supportsSoftLimiting */);
  }

  private final Expression delegate;
  // Whether or not the expression contains operations with side effects (e.g. appends)
  private final boolean hasSideEffects;
  // Whether or not the appendable could ever return true from softLimitReached
  private final boolean supportsSoftLimiting;

  private AppendableExpression(
      Expression delegate, boolean hasSideEffects, boolean supportsSoftLimiting) {
    this(ADVISING_APPENDABLE_TYPE, delegate, hasSideEffects, supportsSoftLimiting);
  }

  private AppendableExpression(
      Type resultType, Expression delegate, boolean hasSideEffects, boolean supportsSoftLimiting) {
    super(resultType, delegate.features());
    delegate.checkAssignableTo(ADVISING_APPENDABLE_TYPE);
    checkArgument(
        delegate.isNonNullable(), "advising appendable expressions should always be non null");
    this.delegate = delegate;
    this.hasSideEffects = hasSideEffects;
    this.supportsSoftLimiting = supportsSoftLimiting;
  }

  @Override
  void doGen(CodeBuilder adapter) {
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

  @Override
  AppendableExpression labelStart(Label label) {
    return withNewDelegate(delegate.labelStart(label), this.hasSideEffects);
  }

  @Override
  Statement toStatement() {
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
   * calls to {@link AdvisingAppendable#softLimitReached()}.
   */
  boolean supportsSoftLimiting() {
    return supportsSoftLimiting;
  }
}
