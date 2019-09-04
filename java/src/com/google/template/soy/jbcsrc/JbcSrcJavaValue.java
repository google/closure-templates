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
import com.google.template.soy.plugin.java.restricted.MethodSignature;
import javax.annotation.Nullable;

/** Adapts an Expression to a JavaValue. */
final class JbcSrcJavaValue implements JavaValue {

  /** Constructs a JbcSrcJavaValue based on the Expression. */
  static JbcSrcJavaValue of(Expression expr) {
    return new JbcSrcJavaValue(expr, /* methodSignature= */ null);
  }

  /**
   * Constructs a JbcSrcJavaValue based on the Expression. The method is used to display helpful
   * error messages to the user if necessary. It is not invoked.
   */
  static JbcSrcJavaValue of(Expression expr, MethodSignature methodSignature) {
    checkNotNull(methodSignature);
    return new JbcSrcJavaValue(expr, methodSignature);
  }

  private final Expression expr;
  @Nullable private final MethodSignature methodSignature;

  private JbcSrcJavaValue(Expression expr, MethodSignature methodSignature) {
    this.expr = checkNotNull(expr);
    this.methodSignature = methodSignature;
  }

  Expression expr() {
    return expr;
  }

  /**
   * Returns the method that this expression is delegating to. The method signature is used to
   * display helpful error messages to the user, if necessary.
   */
  @Nullable
  MethodSignature methodInfo() {
    return methodSignature;
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
    return new JbcSrcJavaValue(((SoyExpression) expr).coerceToBoolean(), methodSignature);
  }

  @Override
  public JbcSrcJavaValue coerceToSoyString() {
    return new JbcSrcJavaValue(((SoyExpression) expr).coerceToString(), methodSignature);
  }

  @Override
  public String toString() {
    String methodStr = methodSignature == null ? "" : (", methodSignature= " + methodSignature);
    return "JbcSrcJavaValue[expr=" + expr + methodStr + "]";
  }
}
