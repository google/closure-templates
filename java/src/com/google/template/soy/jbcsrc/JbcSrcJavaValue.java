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
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.types.SoyType;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import org.objectweb.asm.Type;

/**
 * Adapts an Expression to a JavaValue. By having an adapter instead of letting Expression
 * implementing JavaValue, we avoid mixing the concerns of JavaValue w/ Expression. JavaValue
 * special-cases SoyExpression a lot, although needs to also work with Expression in a few places.
 * In addition, it can optionally capture the Method that the Expression is calling (for use with
 * user-friendly error messages).
 */
final class JbcSrcJavaValue implements JavaValue {

  /**
   * A stub value that exists for error scenarios. This intentionally points to a method that
   * doesn't exist, so that if ever it leaks out, then compilation will fail.
   */
  static final JbcSrcJavaValue ERROR_VALUE =
      new JbcSrcJavaValue(
          MethodRef.createStaticMethod(
                  TypeInfo.create(
                      "if.you.see.this.please.report.a.bug.because.an.error.message.was.swallowed"),
                  new org.objectweb.asm.commons.Method("oops", Type.BOOLEAN_TYPE, new Type[0]))
              .invoke(),
          null);

  /** Constructs a JbcSrcJavaValue based on the Expression. */
  static JbcSrcJavaValue of(Expression expr) {
    return new JbcSrcJavaValue(checkNotNull(expr), null);
  }

  /**
   * Constructs a JbcSrcJavaValue based on the Expression. The method is used for validating the
   * return type of the expression (when translating it to a SoyExpression), and also to display
   * helpful error messages to the user if necessary. It is not invoked.
   */
  static JbcSrcJavaValue of(Expression expr, Method method) {
    return new JbcSrcJavaValue(checkNotNull(expr), checkNotNull(method));
  }

  private final Expression expr;
  @Nullable private final Method method;

  private JbcSrcJavaValue(Expression expr, Method method) {
    this.expr = expr;
    this.method = method;
  }

  Expression expr() {
    return expr;
  }

  /**
   * Returns the method that this expression will invoke at runtime. The return type is used for
   * additional validation, and the method signature is used to display helpful error messages to
   * the user, if necessary.
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
  public ValueSoyType soyType() {
    if (expr() instanceof SoyExpression) {
      SoyType soyType = ((SoyExpression) expr()).soyType();
      return JbcSrcValueFactory.valueForKind(soyType.getKind());
    }

    throw new UnsupportedOperationException(
        "soyType is only currently supported on the JavaValue parameters "
            + "of callStaticMethod or callRuntimeMethod");
  }

  @Override
  public String toString() {
    if (method != null) {
      return "JbcSrcJavaValue[expr=" + expr + ", method= " + method + "]";
    } else {
      return "JbcSrcJavaValue[expr=" + expr + "]";
    }
  }
}
