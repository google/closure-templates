/*
 * Copyright 2019 Google Inc.
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

package com.google.template.soy.plugin.java.internal;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.plugin.java.internal.ValidatorFactory.ValidationResult;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.types.SoyType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** A wrapper over ErrorReporter for dealing with errors in plugin functions. */
final class ValidatorErrorReporter {

  private static final SoyErrorKind INVALID_RETURN_TYPE_WITH_METHOD =
      SoyErrorKind.of(
          formatWithExpectedAndActual("Return type cannot be represented in Soy.\nMethod: {5}."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind INVALID_RETURN_TYPE_NO_METHOD =
      SoyErrorKind.of(
          formatWithExpectedAndActual("Return type cannot be represented in Soy."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind INCOMPATIBLE_RETURN_TYPE_WITH_METHOD =
      SoyErrorKind.of(
          formatWithExpectedAndActual("Type mismatch on return type of {5}."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind INCOMPATIBLE_RETURN_TYPE_NO_METHOD =
      SoyErrorKind.of(
          formatWithExpectedAndActual("Type mismatch on return type."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind PARAMETER_LENGTH_MISMATCH =
      SoyErrorKind.of(
          formatWithExpectedAndActual("Parameter length mismatch calling {5}."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind PARAM_MISMATCH_ONE =
      SoyErrorKind.of(
          formatWithExpectedAndActual("Type mismatch on the {5} parameter of {6}."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind PARAM_MISMATCH_MANY =
      SoyErrorKind.of(
          formatWithExpectedListAndActual("Type mismatch on the {5} parameter of {6}."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind VE_PARAM_NOT_SUPPORTED =
      SoyErrorKind.of(
          formatPlain(
              "Invalid type passed to the {4} parameter of {5}, "
                  + "ve and ve_data types cannot be used by plugins."
                  + "\n  passed: {3}"),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind NULL_PARAM =
      SoyErrorKind.of(
          formatWithExpectedAndActual("Passed null to the {5} parameter of {6}."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind NULL_RETURN =
      SoyErrorKind.of(
          formatPlain("{3}.applyForJavaSource returned null."), StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind NULL_METHOD =
      SoyErrorKind.of(
          formatPlain("Passed a null method to JavaValueFactory.{3}."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind NULL_VALUES =
      SoyErrorKind.of(
          formatPlain(
              "Passed a null JavaValue[] to JavaValueFactory.{3} "
                  + "while trying to call method: {4}."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind INCOMPATIBLE_TYPES =
      SoyErrorKind.of(
          formatPlain("Invalid call to {3}, {4} is incompatible with {5}."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind STATIC_MISMATCH =
      SoyErrorKind.of(
          formatPlain("{3} method {4} passed to JavaValueFactory.{5}."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind UNEXPECTED_ERROR =
      SoyErrorKind.of(formatPlain("{3}"), StyleAllowance.NO_PUNCTUATION);

  private final ErrorReporter reporter;
  private final String fnName;
  private final Class<?> fnClass;
  private final SoyType expectedReturnType;
  private final SourceLocation sourceLocation;
  private final boolean includeTriggeredInTemplateMsg;

  ValidatorErrorReporter(
      ErrorReporter reporter,
      String fnName,
      Class<?> fnClass,
      SoyType expectedReturnType,
      SourceLocation sourceLocation,
      boolean includeTriggeredInTemplateMsg) {
    this.reporter = reporter;
    this.fnName = fnName;
    this.fnClass = fnClass;
    this.sourceLocation = sourceLocation;
    this.expectedReturnType = expectedReturnType;
    this.includeTriggeredInTemplateMsg = includeTriggeredInTemplateMsg;
  }

  private void report(SoyErrorKind error, Object... additionalArgs) {
    Object[] args = new Object[additionalArgs.length + 3];
    args[0] = fnName;
    args[1] = fnClass.getName();
    args[2] = includeTriggeredInTemplateMsg ? "\nTriggered by usage in template at:" : "";
    for (int i = 0; i < additionalArgs.length; i++) {
      args[3 + i] = additionalArgs[i];
    }
    reporter.report(sourceLocation, error, args);
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
          "soy type of '" + expectedReturnType + "'",
          "java type of '" + returnType.getName() + "'");
    } else {
      report(
          INVALID_RETURN_TYPE_WITH_METHOD,
          "soy type of '" + expectedReturnType + "'",
          "java type of '" + returnType.getName() + "'",
          simpleMethodName(method));
    }
  }

  void incompatibleReturnType(SoyType actualType, @Nullable Method method) {
    if (method == null) {
      report(
          INCOMPATIBLE_RETURN_TYPE_NO_METHOD,
          "soy type of '" + expectedReturnType + "'",
          "soy type of '" + actualType + "'");
    } else {
      report(
          INCOMPATIBLE_RETURN_TYPE_WITH_METHOD,
          "soy type of '" + expectedReturnType + "'",
          "soy type of '" + actualType + "'",
          simpleMethodName(method));
    }
  }

  void incompatibleReturnType(Class<?> actualJavaType, @Nullable Method method) {
    if (method == null) {
      report(
          INCOMPATIBLE_RETURN_TYPE_NO_METHOD,
          "soy type of '" + expectedReturnType + "'",
          "java type of '" + actualJavaType.getName() + "'");
    } else {
      report(
          INCOMPATIBLE_RETURN_TYPE_WITH_METHOD,
          "soy type of '" + expectedReturnType + "'",
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
      Method method, int paramIdx, Class<?> actualParamType, Class<?> expectedClass) {
    report(
        PARAM_MISMATCH_ONE,
        "'" + expectedClass.getName() + "'",
        "'" + actualParamType.getName() + "'",
        (paramIdx + 1) + getOrdinalSuffix(paramIdx + 1),
        simpleMethodName(method));
  }

  void invalidParameterType(
      Method method, int paramIdx, Class<?> expectedType, ValidationResult result) {
    switch (result.result()) {
      case VALID:
        throw new IllegalStateException("unexpected valid result");
      case INVALID:
        SoyErrorKind msg;
        String expected;
        if (result.allowedTypes().size() == 1) {
          msg = PARAM_MISMATCH_ONE;
          expected = "'" + Iterables.getOnlyElement(result.allowedTypes()) + "'";
        } else {
          msg = PARAM_MISMATCH_MANY;
          expected =
              result.allowedTypes().stream()
                  .collect(Collectors.joining("'\n          '", "\n          '", "'"));
        }
        report(
            msg,
            expected,
            "'" + expectedType.getName() + "'",
            (paramIdx + 1) + getOrdinalSuffix(paramIdx + 1),
            simpleMethodName(method));
        break;
      case VE:
        report(
            VE_PARAM_NOT_SUPPORTED,
            "soy type ('" + result.allowedSoyType() + "')",
            (paramIdx + 1) + getOrdinalSuffix(paramIdx + 1),
            simpleMethodName(method));
        break;
      case NULL_TO_PRIMITIVE:
        report(
            PARAM_MISMATCH_ONE,
            "a nullable soy type ('" + result.allowedSoyType() + "')",
            "primitive type '" + expectedType.getName() + "'",
            (paramIdx + 1) + getOrdinalSuffix(paramIdx + 1),
            simpleMethodName(method));
        break;
    }
  }

  void nonSoyExpressionNotConvertible(Class<?> actualClass, SoyType newType, String methodName) {
    report(
        INCOMPATIBLE_TYPES,
        methodName,
        "java type of '" + actualClass.getName() + "'",
        "soy type of '" + newType + "'");
  }

  void nonSoyExpressionNotCoercible(Class<?> actualClass, SoyType newType, String methodName) {
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
    report(NULL_RETURN, fnClass.getSimpleName());
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

  void staticMismatch(Method method) {
    String expected;
    String actual;
    if (Modifier.isStatic(method.getModifiers())) {
      expected = "callInstanceMethod";
      actual = "Static";
    } else {
      expected = "callStaticMethod";
      actual = "Instance";
    }
    report(STATIC_MISMATCH, actual, simpleMethodName(method), expected);
  }

  void unexpectedError(Throwable t) {
    report(UNEXPECTED_ERROR, Throwables.getStackTraceAsString(t));
  }

  private static String formatPlain(String innerFmt) {
    return "Error in plugin implementation for function ''{0}''."
        + "\n"
        + innerFmt
        + "\nPlugin implementation: {1}"
        + "{2}";
  }

  private static String formatWithExpectedAndActual(String innerFmt) {
    return "Error in plugin implementation for function ''{0}''."
        + "\n"
        + innerFmt
        + "\n  expected: {3}"
        + "\n  actual:   {4}"
        + "\nPlugin implementation: {1}"
        + "{2}";
  }

  private static String formatWithExpectedListAndActual(String innerFmt) {
    return "Error in plugin implementation for function ''{0}''."
        + "\n"
        + innerFmt
        + "\n  expected one of: {3}"
        + "\n  actual: {4}"
        + "\nPlugin implementation: {1}"
        + "{2}";
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
