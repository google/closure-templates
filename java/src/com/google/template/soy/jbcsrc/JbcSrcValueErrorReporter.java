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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Throwables;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.types.SoyType;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import org.objectweb.asm.Type;

/** A wrapper over ErrorReporter for dealing with errors in JbcSrcValueFactory */
final class JbcSrcValueErrorReporter {

  private static final SoyErrorKind INVALID_RETURN_TYPE_WITH_METHOD =
      SoyErrorKind.of(
          formatWithExpectedAndActual("Return type cannot be represented in Soy.\nMethod: {4}."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind INVALID_RETURN_TYPE_NO_METHOD =
      SoyErrorKind.of(
          formatWithExpectedAndActual("Return type cannot be represented in Soy."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind INCOMPATIBLE_RETURN_TYPE_WITH_METHOD =
      SoyErrorKind.of(
          formatWithExpectedAndActual("Type mismatch on return type of {4}."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind INCOMPATIBLE_RETURN_TYPE_NO_METHOD =
      SoyErrorKind.of(
          formatWithExpectedAndActual("Type mismatch on return type."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind PARAMETER_LENGTH_MISMATCH =
      SoyErrorKind.of(
          formatWithExpectedAndActual("Parameter length mismatch calling {4}."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind PARAM_MISMATCH =
      SoyErrorKind.of(
          formatWithExpectedAndActual("Type mismatch on the {4} parameter to {5}."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind NULL_PARAM =
      SoyErrorKind.of(
          formatWithExpectedAndActual("Passed null to the {4} parameter of {5}."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind NULL_RETURN =
      SoyErrorKind.of(
          formatPlain("{2}.applyForJavaSource returned null."), StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind NULL_METHOD =
      SoyErrorKind.of(
          formatPlain("Passed a null method to JavaValueFactory.{2}."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind NULL_VALUES =
      SoyErrorKind.of(
          formatPlain(
              "Passed a null JavaValue[] to JavaValueFactory.{2} "
                  + "while trying to call method: {3}."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind INCOMPATIBLE_TYPES =
      SoyErrorKind.of(
          formatPlain("Invalid call to {2}, {3} is incompatible with {4}."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind VE_PARAM_NOT_SUPPORTED =
      SoyErrorKind.of(
          formatPlain("The ve and ve_data types cannot be passed to plugins."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind UNEXPECTED_ERROR =
      SoyErrorKind.of(formatPlain("{2}"), StyleAllowance.NO_PUNCTUATION);

  private final ErrorReporter reporter;
  private final FunctionNode fnNode;

  JbcSrcValueErrorReporter(ErrorReporter reporter, FunctionNode fnNode) {
    this.reporter = reporter;
    this.fnNode = fnNode;
  }

  private void report(SoyErrorKind error, Object... additionalArgs) {
    Object[] args = new Object[additionalArgs.length + 2];
    args[0] = fnNode.getFunctionName();
    args[1] = fnNode.getSoyFunction().getClass().getName();
    for (int i = 0; i < additionalArgs.length; i++) {
      args[2 + i] = additionalArgs[i];
    }
    reporter.report(fnNode.getSourceLocation(), error, args);
  }

  ErrorReporter.Checkpoint checkpoint() {
    return reporter.checkpoint();
  }

  boolean errorsSince(ErrorReporter.Checkpoint checkpoint) {
    return reporter.errorsSince(checkpoint);
  }

  void invalidReturnType(Class<?> returnType, @Nullable Method method) {
    if (method == null) {
      report(
          INVALID_RETURN_TYPE_NO_METHOD,
          "soy type of '" + fnNode.getType() + "'",
          "java type of '" + returnType.getName() + "'");
    } else {
      report(
          INVALID_RETURN_TYPE_WITH_METHOD,
          "soy type of '" + fnNode.getType() + "'",
          "java type of '" + returnType.getName() + "'",
          simpleMethodName(method));
    }
  }

  void incompatibleReturnType(SoyType actualType, @Nullable Method method) {
    if (method == null) {
      report(
          INCOMPATIBLE_RETURN_TYPE_NO_METHOD,
          "soy type of '" + fnNode.getType() + "'",
          "soy type of '" + actualType + "'");
    } else {
      report(
          INCOMPATIBLE_RETURN_TYPE_WITH_METHOD,
          "soy type of '" + fnNode.getType() + "'",
          "soy type of '" + actualType + "'",
          simpleMethodName(method));
    }
  }

  void incompatibleReturnType(Class<?> actualJavaType, @Nullable Method method) {
    if (method == null) {
      report(
          INCOMPATIBLE_RETURN_TYPE_NO_METHOD,
          "soy type of '" + fnNode.getType() + "'",
          "java type of '" + actualJavaType.getName() + "'");
    } else {
      report(
          INCOMPATIBLE_RETURN_TYPE_WITH_METHOD,
          "soy type of '" + fnNode.getType() + "'",
          "java type of '" + actualJavaType.getName() + "'",
          simpleMethodName(method));
    }
  }

  void invalidParameterLength(Method method, JavaValue[] actualParams) {
    String expected =
        method.getParameterTypes().length == 1
            ? "1 parameter"
            : method.getParameterTypes().length + " parameters";
    String actual = actualParams.length == 1 ? "1 parameter" : actualParams.length + " parameters";
    report(PARAMETER_LENGTH_MISMATCH, expected, actual, simpleMethodName(method));
  }

  void invalidParameterType(
      Method method, int paramIdx, Class<?> expectedType, Expression actualExpr) {
    Type actualType =
        actualExpr instanceof SoyExpression
            ? ((SoyExpression) actualExpr).soyRuntimeType().runtimeType()
            : actualExpr.resultType();
    Class<?> actualClass = BytecodeUtils.classFromAsmType(actualType);
    report(
        PARAM_MISMATCH,
        "'" + expectedType.getName() + "'",
        "'" + actualClass.getName() + "'",
        paramIdx + getOrdinalSuffix(paramIdx),
        simpleMethodName(method));
  }

  void invalidParameterType(
      Method method, int paramIdx, Class<?> expectedType, SoyType allowedSoyType) {
    report(
        PARAM_MISMATCH,
        "java type of '" + expectedType.getName() + "'",
        "soy type of '" + allowedSoyType + "'",
        paramIdx + getOrdinalSuffix(paramIdx),
        simpleMethodName(method));
  }

  void nonSoyExpressionNotConvertible(Expression expr, SoyType newType, String methodName) {
    Class<?> actualClass = BytecodeUtils.classFromAsmType(expr.resultType());
    report(
        INCOMPATIBLE_TYPES,
        methodName,
        "java type of '" + actualClass.getName() + "'",
        "soy type of '" + newType + "'");
  }

  void nonSoyExpressionNotCoercible(Expression expr, SoyType newType, String methodName) {
    Class<?> actualClass = BytecodeUtils.classFromAsmType(expr.resultType());
    report(
        INCOMPATIBLE_TYPES,
        methodName,
        "java type of '" + actualClass.getName() + "'",
        "soy type of '" + newType + "'");
  }

  void incompatibleSoyType(SoyType allowedType, SoyType newType, String methodName) {
    report(
        INCOMPATIBLE_TYPES,
        methodName,
        "soy type of '" + allowedType + "'",
        "soy type of '" + newType + "'");
  }

  void nullReturn() {
    report(NULL_RETURN, fnNode.getSoyFunction().getClass().getSimpleName());
  }

  void nullMethod(String methodName) {
    report(NULL_METHOD, methodName);
  }

  void nullParamArray(Method method, String methodName) {
    report(NULL_VALUES, methodName, simpleMethodName(method));
  }

  void nullParam(Method method, int paramIdx, Class<?> expectedType) {
    report(
        NULL_PARAM,
        "'" + expectedType.getName() + "'",
        "null",
        paramIdx + getOrdinalSuffix(paramIdx),
        simpleMethodName(method));
  }

  void unexpectedError(Throwable t) {
    report(UNEXPECTED_ERROR, Throwables.getStackTraceAsString(t));
  }

  void veParam() {
    report(VE_PARAM_NOT_SUPPORTED);
  }

  private static String formatPlain(String innerFmt) {
    return "Error in plugin implementation for function ''{0}''."
        + "\n"
        + innerFmt
        + "\nPlugin implementation: {1}";
  }

  private static String formatWithExpectedAndActual(String innerFmt) {
    return "Error in plugin implementation for function ''{0}''."
        + "\n"
        + innerFmt
        + "\n  expected: {2}, actual: {3}"
        + "\nPlugin implementation: {1}";
  }

  private static String simpleMethodName(Method method) {
    return "'" + method.getDeclaringClass().getName() + "." + method.getName() + "'";
  }

  /**
   * Maps {@code 1} to the string {@code "1st"} ditto for all non-negative numbers
   *
   * @see <a href="https://en.wikipedia.org/wiki/English_numerals#Ordinal_numbers">
   *     https://en.wikipedia.org/wiki/English_numerals#Ordinal_numbers</a>
   */
  private static String getOrdinalSuffix(int ordinal) {
    // negative ordinals don't make sense, we allow zero though because we are programmers
    checkArgument(ordinal >= 0);
    if ((ordinal / 10) % 10 == 1) {
      // all the 'teens' are weird
      return "th";
    } else {
      // could use a lookup table? any better?
      switch (ordinal % 10) {
        case 1:
          return "st";
        case 2:
          return "nd";
        case 3:
          return "rd";
        default:
          return "th";
      }
    }
  }
}
