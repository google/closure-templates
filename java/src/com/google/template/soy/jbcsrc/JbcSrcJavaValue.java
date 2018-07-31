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

package com.google.template.soy.jbcsrc;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.FloatType;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.StringType;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

/** Adapts an Expression to a JavaValue. */
final class JbcSrcJavaValue implements JavaValue {

  /** Constructs a JbcSrcJavaValue that represents an error. */
  static JbcSrcJavaValue error(Expression expr, JbcSrcValueErrorReporter reporter) {
    return new JbcSrcJavaValue(
        expr, /* method= */ null, /* allowedType= */ null, /* error= */ true, reporter);
  }

  /** Constructs a JbcSrcJavaValue based on the Expression. */
  static JbcSrcJavaValue of(Expression expr, JbcSrcValueErrorReporter reporter) {
    if (expr instanceof SoyExpression) {
      return new JbcSrcJavaValue(
          expr, /* method= */ null, ((SoyExpression) expr).soyType(), /* error= */ false, reporter);
    }
    return new JbcSrcJavaValue(
        expr, /* method= */ null, /* allowedType= */ null, /* error= */ false, reporter);
  }

  /**
   * Constructs a JbcSrcJavaValue based on the Expression. The method is used to display helpful
   * error messages to the user if necessary. It is not invoked.
   */
  static JbcSrcJavaValue of(Expression expr, Method method, JbcSrcValueErrorReporter reporter) {
    checkNotNull(method);
    if (expr instanceof SoyExpression) {
      return new JbcSrcJavaValue(
          expr, method, ((SoyExpression) expr).soyType(), /* error= */ false, reporter);
    }
    return new JbcSrcJavaValue(expr, method, /* allowedType= */ null, /* error= */ false, reporter);
  }

  /**
   * Constructs a JbcSrcJavaValue based on the Expression, with the given SoyType as allowed types.
   * The SoyType is expected to be based on the function signature, so can be broader than the soy
   * type of the expression itself. The expression is expected to be assignable to the type.
   */
  static JbcSrcJavaValue of(
      SoyExpression expr, SoyType allowedType, JbcSrcValueErrorReporter reporter) {
    return new JbcSrcJavaValue(
        expr, /* method= */ null, checkNotNull(allowedType), /* error= */ false, reporter);
  }

  private final Expression expr;
  private final JbcSrcValueErrorReporter reporter;
  @Nullable private final SoyType allowedType;
  @Nullable private final Method method;
  private final boolean error;

  private JbcSrcJavaValue(
      Expression expr,
      Method method,
      SoyType allowedType,
      boolean error,
      JbcSrcValueErrorReporter reporter) {
    this.expr = checkNotNull(expr);
    this.reporter = checkNotNull(reporter);
    this.method = method;
    this.allowedType = allowedType;
    this.error = error;
  }

  boolean isError() {
    return error;
  }

  Expression expr() {
    return expr;
  }

  /**
   * Returns the SoyType of the parameter at this value's index that the function's signature allows
   * (if the expression comes from a function parameter), or the type of the SoyExpression (if the
   * expression comes from something else).
   */
  @Nullable
  SoyType getAllowedType() {
    return allowedType;
  }

  /**
   * Returns the method that this expression is delegating to. The method signature is used to
   * display helpful error messages to the user, if necessary.
   */
  @Nullable
  Method methodInfo() {
    return method;
  }

  @Override
  public JbcSrcJavaValue isNonNull() {
    return of(BytecodeUtils.isNonNull(expr), reporter);
  }

  @Override
  public JbcSrcJavaValue isNull() {
    return of(BytecodeUtils.isNull(expr), reporter);
  }

  @Override
  public JbcSrcJavaValue asSoyBoolean() {
    return asSoyType(BoolType.getInstance(), boolean.class, "asSoyBoolean");
  }

  @Override
  public JbcSrcJavaValue asSoyFloat() {
    return asSoyType(FloatType.getInstance(), double.class, "asSoyFloat");
  }

  @Override
  public JbcSrcJavaValue asSoyInt() {
    return asSoyType(IntType.getInstance(), long.class, "asSoyInt");
  }

  @Override
  public JbcSrcJavaValue asSoyString() {
    return asSoyType(StringType.getInstance(), String.class, "asSoyString");
  }

  private JbcSrcJavaValue asSoyType(SoyType newType, Class<?> newClass, String methodName) {
    if (allowedType == null) {
      reporter.nonSoyExpressionNotConvertible(expr, newType, methodName);
      return error(JbcSrcValueFactory.stubExpression(newClass), reporter);
    }
    if (!allowedType.isAssignableFrom(newType)) {
      reporter.incompatibleSoyType(allowedType, newType, methodName);
      return error(JbcSrcValueFactory.stubExpression(newClass), reporter);
    }
    return new JbcSrcJavaValue(expr, method, checkNotNull(newType), /* error= */ false, reporter);
  }

  @Override
  public JbcSrcJavaValue coerceToSoyBoolean() {
    if (!(expr instanceof SoyExpression)) {
      reporter.nonSoyExpressionNotCoercible(expr, BoolType.getInstance(), "coerceToSoyBoolean");
      return error(JbcSrcValueFactory.stubExpression(boolean.class), reporter);
    }
    return new JbcSrcJavaValue(
        ((SoyExpression) expr).coerceToBoolean(),
        method,
        BoolType.getInstance(),
        /* error= */ false,
        reporter);
  }

  @Override
  public JbcSrcJavaValue coerceToSoyString() {
    if (!(expr instanceof SoyExpression)) {
      reporter.nonSoyExpressionNotCoercible(expr, StringType.getInstance(), "coerceToSoyString");
      return error(JbcSrcValueFactory.stubExpression(String.class), reporter);
    }
    return new JbcSrcJavaValue(
        ((SoyExpression) expr).coerceToString(),
        method,
        StringType.getInstance(),
        /* error= */ false,
        reporter);
  }

  @Override
  public String toString() {
    String typeStr = allowedType == null ? "" : (", allowedType= " + allowedType);
    String methodStr = method == null ? "" : (", method= " + method);
    return "JbcSrcJavaValue[expr=" + expr + typeStr + methodStr + "]";
  }
}
