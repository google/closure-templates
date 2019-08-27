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
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

/** Adapts an Expression to a JavaValue. */
final class JbcSrcJavaValue implements JavaValue {

  /** Constructs a JbcSrcJavaValue based on the Expression. */
  static JbcSrcJavaValue of(Expression expr) {
    return new JbcSrcJavaValue(expr, /* method= */ null, /* constantNull= */ false);
  }

  /**
   * Constructs a JbcSrcJavaValue based on the Expression. The method is used to display helpful
   * error messages to the user if necessary. It is not invoked.
   */
  static JbcSrcJavaValue of(Expression expr, Method method) {
    checkNotNull(method);
    return new JbcSrcJavaValue(expr, method, /* constantNull= */ false);
  }

  /**
   * Constructs a JbcSrcJavaValue specifically for 'constantNull'. There's no SoyType we can use to
   * indicate this, and we can't construct our own SoyType because the cxtor is package-private, so
   * we have a separate bool to indicate it.
   */
  static JbcSrcJavaValue ofConstantNull() {
    return new JbcSrcJavaValue(SoyExpression.NULL, /* method= */ null, /* constantNull= */ true);
  }

  private final Expression expr;
  @Nullable private final Method method;
  private final boolean constantNull;

  private JbcSrcJavaValue(Expression expr, Method method, boolean constantNull) {
    this.expr = checkNotNull(expr);
    this.method = method;
    this.constantNull = constantNull;
  }

  Expression expr() {
    return expr;
  }

  /** Returns true if this is the JbcSrcValue for a {@link JavaValueFactory#constantNull} call. */
  public boolean isConstantNull() {
    return constantNull;
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
    return of(BytecodeUtils.isNonNull(expr));
  }

  @Override
  public JbcSrcJavaValue isNull() {
    return of(BytecodeUtils.isNull(expr));
  }

  @Override
  public JbcSrcJavaValue asSoyBoolean() {
    return this;
  }

  @Override
  public JbcSrcJavaValue asSoyFloat() {
    return this;
  }

  @Override
  public JbcSrcJavaValue asSoyInt() {
    return this;
  }

  @Override
  public JbcSrcJavaValue asSoyString() {
    return this;
  }

  @Override
  public JbcSrcJavaValue coerceToSoyBoolean() {
    return new JbcSrcJavaValue(
        ((SoyExpression) expr).coerceToBoolean(), method, /* constantNull= */ false);
  }

  @Override
  public JbcSrcJavaValue coerceToSoyString() {
    return new JbcSrcJavaValue(
        ((SoyExpression) expr).coerceToString(), method, /* constantNull= */ false);
  }

  @Override
  public String toString() {
    String methodStr = method == null ? "" : (", method= " + method);
    return "JbcSrcJavaValue[expr=" + expr + methodStr + "]";
  }
}
