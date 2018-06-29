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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.types.SoyType;
import java.lang.reflect.Method;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import org.objectweb.asm.Type;

@CheckReturnValue
class PluginCodegenException extends RuntimeException {

  private final SourceLocation location;

  private PluginCodegenException(Node node, String msg) {
    super(msg);
    this.location = node.getSourceLocation();
  }

  public SourceLocation getOriginalLocation() {
    return location;
  }

  static PluginCodegenException invalidReturnType(
      FunctionNode fnNode, Class<?> returnType, @Nullable Method method) {
    return new PluginCodegenException(
        fnNode,
        errorMsg(
            fnNode,
            "Return type cannot be represented in Soy."
                + (method == null ? "" : "\nMethod: " + simpleMethodName(method) + "."),
            "soy type of '" + fnNode.getType() + "'",
            "java type of '" + returnType.getName() + "'"));
  }

  static PluginCodegenException incompatibleReturnType(
      FunctionNode fnNode, SoyType actualType, @Nullable Method method) {
    return new PluginCodegenException(
        fnNode,
        errorMsg(
            fnNode,
            "Type mismatch on return type"
                + (method == null ? "." : " of " + simpleMethodName(method) + "."),
            "soy type of '" + fnNode.getType() + "'",
            "soy type of '" + actualType + "'"));
  }

  static PluginCodegenException invalidParameterLength(
      FunctionNode fnNode, Method method, JavaValue[] actualParams) {
    return new PluginCodegenException(
        fnNode,
        errorMsg(
            fnNode,
            "Parameter length mismatch calling " + simpleMethodName(method) + ".",
            method.getParameterTypes().length == 1
                ? "1 parameter"
                : method.getParameterTypes().length + " parameters",
            actualParams.length == 1 ? "1 parameter" : actualParams.length + " parameters"));
  }

  static PluginCodegenException invalidParameterType(
      FunctionNode fnNode,
      Method method,
      int paramIdx,
      Class<?> expectedType,
      Expression actualExpr) {
    Type actualType =
        actualExpr instanceof SoyExpression
            ? ((SoyExpression) actualExpr).soyRuntimeType().runtimeType()
            : actualExpr.resultType();
    Class<?> actualClass = BytecodeUtils.classFromAsmType(actualType);
    return new PluginCodegenException(
        fnNode,
        errorMsg(
            fnNode,
            "Type mismatch on the "
                + paramIdx
                + getOrdinalSuffix(paramIdx)
                + " parameter to "
                + simpleMethodName(method)
                + ".",
            "'" + expectedType.getName() + "'",
            "'" + actualClass.getName() + "'"));
  }

  /** Formats a plugin codegen exception with an expected & actual value. */
  private static String errorMsg(FunctionNode fnNode, String msg, Object expected, Object actual) {
    return errorMsg(
        fnNode, String.format("%s" + "\n  expected: %s, actual: %s", msg, expected, actual));
  }

  /** Formats a plugin codegen exception without an expected & actual value. */
  private static String errorMsg(FunctionNode fnNode, String msg) {
    return String.format(
        "Error in plugin implementation for function '%s'."
            + "\n%s"
            + "\nPlugin implementation: %s",
        fnNode.getFunctionName(), msg, fnNode.getSoyFunction().getClass().getName());
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
