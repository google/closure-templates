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
import com.google.template.soy.error.SoyError;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.plugin.java.internal.ValidatorFactory.ValidationResult;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.MethodSignature;
import com.google.template.soy.types.SoyType;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** A wrapper over ErrorReporter for dealing with errors in plugin functions. */
public final class ValidatorErrorReporter {

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

  private static final SoyErrorKind INTERFACE_MISMATCH =
      SoyErrorKind.of(
          formatPlain(
              "MethodSignature.{3} used for a method {4}in an interface. "
                  + "Use MethodSignature.{5} instead."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind INVALID_PLUGIN_METHOD =
      SoyErrorKind.of(
          formatPlain(
              "Can''t find a public method with signature ''{3}''{4} "
                  + "in the plugin''s java deps."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind WRONG_PLUGIN_METHOD_RETURN_TYPE =
      SoyErrorKind.of(
          formatPlain("Plugin runtime method {3} returns a {4}, not a {5}."),
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind UNEXPECTED_ERROR =
      SoyErrorKind.of(formatPlain("{3}"), StyleAllowance.NO_PUNCTUATION);

  private final ErrorReporter reporter;
  private final String fnName;
  private final Class<?> fnClass;
  private final SourceLocation sourceLocation;
  private final boolean includeTriggeredInTemplateMsg;

  public ValidatorErrorReporter(
      ErrorReporter reporter,
      String fnName,
      Class<?> fnClass,
      SourceLocation sourceLocation,
      boolean includeTriggeredInTemplateMsg) {
    this.reporter = reporter;
    this.fnName = fnName;
    this.fnClass = fnClass;
    this.sourceLocation = sourceLocation;
    this.includeTriggeredInTemplateMsg = includeTriggeredInTemplateMsg;
  }

  private void report(SoyErrorKind error, Object... additionalArgs) {
    reporter.report(sourceLocation, error, createArgs(additionalArgs));
  }

  private void warn(SoyErrorKind error, Object... additionalArgs) {
    reporter.warn(sourceLocation, error, createArgs(additionalArgs));
  }

  private Object[] createArgs(Object... additionalArgs) {
    Object[] args = new Object[additionalArgs.length + 3];
    args[0] = fnName;
    args[1] = fnClass.getName();
    args[2] = includeTriggeredInTemplateMsg ? "\nTriggered by usage in template at:" : "";
    for (int i = 0; i < additionalArgs.length; i++) {
      args[3 + i] = additionalArgs[i];
    }
    return args;
  }

  ErrorReporter.Checkpoint checkpoint() {
    return reporter.checkpoint();
  }

  boolean errorsSince(ErrorReporter.Checkpoint checkpoint) {
    return reporter.errorsSince(checkpoint);
  }

  void invalidReturnType(
      Class<?> actualReturnType, SoyType expectedReturnType, @Nullable MethodSignature method) {
    if (method == null) {
      report(
          INVALID_RETURN_TYPE_NO_METHOD,
          "soy type of '" + expectedReturnType + "'",
          "java type of '" + actualReturnType.getName() + "'");
    } else {
      report(
          INVALID_RETURN_TYPE_WITH_METHOD,
          "soy type of '" + expectedReturnType + "'",
          "java type of '" + actualReturnType.getName() + "'",
          simpleMethodName(method));
    }
  }

  void incompatibleReturnType(
      SoyType actualType, SoyType expectedReturnType, @Nullable MethodSignature method) {
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

  void incompatibleReturnType(
      Class<?> actualJavaType, SoyType expectedReturnType, @Nullable MethodSignature method) {
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

  void invalidParameterLength(MethodSignature method, JavaValue[] actualParams) {
    String expected =
        method.arguments().size() == 1 ? "1 parameter" : method.arguments().size() + " parameters";
    String actual = actualParams.length == 1 ? "1 parameter" : actualParams.length + " parameters";
    report(PARAMETER_LENGTH_MISMATCH, expected, actual, simpleMethodName(method));
  }

  void invalidParameterType(
      MethodSignature method, int paramIdx, Class<?> actualParamType, Class<?> expectedClass) {
    report(
        PARAM_MISMATCH_ONE,
        "'" + expectedClass.getName() + "'",
        "'" + actualParamType.getName() + "'",
        (paramIdx + 1) + getOrdinalSuffix(paramIdx + 1),
        simpleMethodName(method));
  }

  void invalidParameterType(
      MethodSignature method, int paramIdx, Class<?> expectedType, ValidationResult result) {
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

  void nullParamArray(MethodSignature method, String methodName) {
    report(NULL_VALUES, methodName, simpleMethodName(method));
  }

  void nullParam(MethodSignature method, int paramIdx, Class<?> expectedType) {
    report(
        NULL_PARAM,
        "'" + expectedType.getName() + "'",
        "null",
        paramIdx + getOrdinalSuffix(paramIdx),
        simpleMethodName(method));
  }

  void staticMismatch(MethodSignature method, boolean expectedInstance) {
    String expected;
    String actual;
    if (expectedInstance) {
      expected = "callInstanceMethod";
      actual = "Static";
    } else {
      expected = "callStaticMethod";
      actual = "Instance";
    }
    report(STATIC_MISMATCH, actual, simpleMethodName(method), expected);
  }

  void interfaceMismatch(MethodSignature method) {
    String userMethod;
    String maybeNot;
    String correctMethod;
    if (method.inInterface()) {
      userMethod = "createInterfaceMethod";
      maybeNot = "not ";
      correctMethod = "create";
    } else {
      userMethod = "create";
      maybeNot = "";
      correctMethod = "createInterfaceMethod";
    }
    report(INTERFACE_MISMATCH, userMethod, maybeNot, correctMethod);
  }

  void invalidPluginMethod(MethodSignature method) {
    String signature =
        String.format(
            "%s.%s(%s)",
            method.fullyQualifiedClassName(),
            method.methodName(),
            method.arguments().stream().map(Class::getName).collect(Collectors.joining(", ")));
    report(
        INVALID_PLUGIN_METHOD,
        signature,
        method.arguments().isEmpty() ? " (with no parameters)" : "");
  }

  void wrongPluginMethodReturnType(String actualReturnType, MethodSignature expectedMethod) {
    report(
        WRONG_PLUGIN_METHOD_RETURN_TYPE,
        simpleMethodName(expectedMethod),
        actualReturnType,
        expectedMethod.returnType().getName());
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

  private static String simpleMethodName(MethodSignature method) {
    return "'" + method.fullyQualifiedClassName() + "." + method.methodName() + "'";
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

  public void wrapErrors(Iterable<SoyError> errors) {
    errors.forEach(e -> report(UNEXPECTED_ERROR, e.message()));
  }

  public void wrapWarnings(Iterable<SoyError> warnings) {
    warnings.forEach(e -> warn(UNEXPECTED_ERROR, e.message()));
  }
}
