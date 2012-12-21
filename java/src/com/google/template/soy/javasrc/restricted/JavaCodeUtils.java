/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.javasrc.restricted;

import com.google.common.base.Joiner;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.StringData;

import java.util.regex.Pattern;


/**
 * Utilities for building code for the Java Source backend.
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 * @author Kai Huang
 */
public class JavaCodeUtils {

  private JavaCodeUtils() {}


  public static final String UTILS_LIB = "com.google.template.soy.javasrc.codedeps.SoyUtils";

  public static final String NULL_DATA_INSTANCE =
      "com.google.template.soy.data.restricted.NullData.INSTANCE";

  // group(1) is the number literal.
  private static final Pattern NUMBER_IN_PARENS = Pattern.compile("^[(]([0-9]+(?:[.][0-9]+)?)[)]$");


  public static String genMaybeProtect(JavaExpr expr, int minSafePrecedence) {
    return (expr.getPrecedence() >= minSafePrecedence) ?
           expr.getText() : "(" + expr.getText() + ")";
  }


  public static String genNewBooleanData(String innerExprText) {
    return "com.google.template.soy.data.restricted.BooleanData.forValue(" + innerExprText + ")";
  }


  public static String genNewIntegerData(String innerExprText) {
    return "com.google.template.soy.data.restricted.IntegerData.forValue(" + innerExprText + ")";
  }


  public static String genNewFloatData(String innerExprText) {
    return "com.google.template.soy.data.restricted.FloatData.forValue(" + innerExprText + ")";
  }


  public static String genNewStringData(String innerExprText) {
    return "com.google.template.soy.data.restricted.StringData.forValue(" + innerExprText + ")";
  }


  public static String genNewListData(String innerExprText) {
    return "new com.google.template.soy.data.SoyListData(" + innerExprText + ")";
  }


  public static String genNewMapData(String innerExprText) {
    return "new com.google.template.soy.data.SoyMapData(" + innerExprText + ")";
  }


  public static String genNewSanitizedContent(
      String innerExprText, SanitizedContent.ContentKind contentKind) {
    return "new " + SanitizedContent.class.getCanonicalName() + "(" + innerExprText + ", " +
        SanitizedContent.ContentKind.class.getCanonicalName() + "." + contentKind.name() + ")";
  }


  public static String genCoerceBoolean(JavaExpr expr) {

    // Special case: If the expr is wrapped by "BooleanData.forValue()", remove the
    // "BooleanData.forValue" instead of generating silly code. We leave the parentheses because we
    // don't know whether the inner expression needs protection.
    String exprText = expr.getText();
    if (exprText.startsWith("com.google.template.soy.data.restricted.BooleanData.forValue(")) {
      return exprText.substring(
          "com.google.template.soy.data.restricted.BooleanData.forValue".length());
    }

    // Normal case.
    return genMaybeProtect(expr, Integer.MAX_VALUE) + ".toBoolean()";
  }


  public static String genCoerceString(JavaExpr expr) {

    // Special case: If the expr is wrapped by "StringData.forValue()", remove the
    // "StringData.forValue" instead of generating silly code. We leave the parentheses because we
    // don't know whether the inner expression needs protection.
    String exprText = expr.getText();
    if (exprText.startsWith("com.google.template.soy.data.restricted.StringData.forValue(")) {
      return exprText.substring(
          "com.google.template.soy.data.restricted.StringData.forValue".length());
    }

    // Normal case.
    return genMaybeProtect(expr, Integer.MAX_VALUE) + ".toString()";
  }


  public static String genBooleanValue(JavaExpr expr) {

    // Special case: If the expr is wrapped by "BooleanData.forValue()", remove the
    // "BooleanData.forValue" instead of generating silly code. We leave the parentheses because we
    // don't know whether the inner expression needs protection.
    String exprText = expr.getText();
    if (exprText.startsWith("com.google.template.soy.data.restricted.BooleanData.forValue(")) {
      return exprText.substring(
          "com.google.template.soy.data.restricted.BooleanData.forValue".length());
    }

    // Normal case.
    return genMaybeProtect(expr, Integer.MAX_VALUE) + ".booleanValue()";
  }


  public static String genIntegerValue(JavaExpr expr) {

    // Special case: If the expr is wrapped by "IntegerData.forValue()", remove the
    // "IntegerData.forValue" instead of generating silly code. We leave the parentheses because we
    // don't know whether the inner expression needs protection.
    String exprText = expr.getText();
    if (exprText.startsWith("com.google.template.soy.data.restricted.IntegerData.forValue(")) {
      String result = exprText.substring(
          "com.google.template.soy.data.restricted.IntegerData.forValue".length());
      if (NUMBER_IN_PARENS.matcher(result).matches()) {
        result = result.substring(1, result.length() - 1);
      }
      return result;
    }

    // Normal case.
    return genMaybeProtect(expr, Integer.MAX_VALUE) + ".integerValue()";
  }


  public static String genFloatValue(JavaExpr expr) {

    // Special case: If the expr is wrapped by "FloatData.forValue()", remove the
    // "FloatData.forValue" instead of generating silly code. We leave the parentheses because we
    // don't know whether the inner expression needs protection.
    String exprText = expr.getText();
    if (exprText.startsWith("com.google.template.soy.data.restricted.FloatData.forValue(")) {
      String result = exprText.substring(
          "com.google.template.soy.data.restricted.FloatData.forValue".length());
      if (NUMBER_IN_PARENS.matcher(result).matches()) {
        result = result.substring(1, result.length() - 1);
      }
      return result;
    }

    // Normal case.
    return genMaybeProtect(expr, Integer.MAX_VALUE) + ".floatValue()";
  }


  public static String genNumberValue(JavaExpr expr) {

    // Special case: If the expr is wrapped by "IntegerData.forValue()" or "FloatData.forValue()",
    // remove the "IntegerData.forValue" or "FloatData.forValue" instead of generating silly code.
    // We leave the parentheses because we don't know whether the inner expression needs protection.
    String exprText = expr.getText();
    String result = null;
    if (exprText.startsWith("com.google.template.soy.data.restricted.IntegerData.forValue(")) {
      result = exprText.substring(
          "com.google.template.soy.data.restricted.IntegerData.forValue".length());
    }
    if (exprText.startsWith("com.google.template.soy.data.restricted.FloatData.forValue(")) {
      result = exprText.substring(
          "com.google.template.soy.data.restricted.FloatData.forValue".length());
    }
    if (result != null) {
      if (NUMBER_IN_PARENS.matcher(result).matches()) {
        result = result.substring(1, result.length() - 1);
      }
      return result;
    }

    // Normal case.
    return genMaybeProtect(expr, Integer.MAX_VALUE) + ".numberValue()";
  }


  public static String genStringValue(JavaExpr expr) {

    // Special case: If the expr is wrapped by "StringData.forValue()", remove the
    // "StringData.forValue" instead of generating silly code. We leave the parentheses because we
    // don't know whether the inner expression needs protection.
    String exprText = expr.getText();
    if (exprText.startsWith("com.google.template.soy.data.restricted.StringData.forValue(")) {
      return exprText.substring(
          "com.google.template.soy.data.restricted.StringData.forValue".length());
    }

    // Normal case.
    return genMaybeProtect(expr, Integer.MAX_VALUE) + ".stringValue()";
  }


  public static String genMaybeCast(JavaExpr expr, Class<? extends SoyData> class0) {
    if (class0.isAssignableFrom(expr.getType())) {
      return expr.getText();
    } else {
      return "(" + class0.getName() + ") " + genMaybeProtect(expr, Integer.MAX_VALUE);
    }
  }


  public static String genConditional(
      String condExprText, String thenExprText, String elseExprText) {
    return condExprText + " ? " + thenExprText + " : " + elseExprText;
  }


  public static boolean isAlwaysInteger(JavaExpr expr) {
    return IntegerData.class.isAssignableFrom(expr.getType());
  }


  public static boolean isAlwaysFloat(JavaExpr expr) {
    return FloatData.class.isAssignableFrom(expr.getType());
  }


  public static boolean isAlwaysString(JavaExpr expr) {
    return StringData.class.isAssignableFrom(expr.getType());
  }


  public static boolean isAlwaysNumber(JavaExpr expr) {
    return NumberData.class.isAssignableFrom(expr.getType());
  }


  public static boolean isAlwaysTwoIntegers(JavaExpr expr0, JavaExpr expr1) {
    return isAlwaysInteger(expr0) && isAlwaysInteger(expr1);
  }


  public static boolean isAlwaysTwoFloatsOrOneFloatOneInteger(JavaExpr expr0, JavaExpr expr1) {
    return (isAlwaysFloat(expr0) && isAlwaysNumber(expr1)) ||
           (isAlwaysFloat(expr1) && isAlwaysNumber(expr0));
  }


  public static boolean isAlwaysAtLeastOneFloat(JavaExpr expr0, JavaExpr expr1) {
    return isAlwaysFloat(expr0) || isAlwaysFloat(expr1);
  }


  public static boolean isAlwaysAtLeastOneString(JavaExpr expr0, JavaExpr expr1) {
    return isAlwaysString(expr0) || isAlwaysString(expr1);
  }


  public static String genUnaryOp(String operatorExprText, String operandExprText) {
    return operatorExprText + " " + operandExprText;
  }


  public static String genBinaryOp(
      String operatorExprText, String operand0ExprText, String operand1ExprText) {
    return operand0ExprText + " " + operatorExprText + " " + operand1ExprText;
  }


  public static String genFunctionCall(
      String functionNameExprText, String... functionArgsExprTexts) {
    return functionNameExprText + "(" + Joiner.on(", ").join(functionArgsExprTexts) + ")";
  }


  public static JavaExpr genJavaExprForNumberToNumberBinaryFunction(
      String javaFunctionName, String utilsLibFunctionName, JavaExpr arg0, JavaExpr arg1) {

    if (isAlwaysTwoIntegers(arg0, arg1)) {
      String exprText = genNewIntegerData(genFunctionCall(
          javaFunctionName, genIntegerValue(arg0), genIntegerValue(arg1)));
      return new JavaExpr(exprText, IntegerData.class, Integer.MAX_VALUE);

    } else if (isAlwaysAtLeastOneFloat(arg0, arg1)) {
      String exprText = genNewFloatData(genFunctionCall(
          javaFunctionName, genFloatValue(arg0), genFloatValue(arg1)));
      return new JavaExpr(exprText, FloatData.class, Integer.MAX_VALUE);

    } else {
      String exprText = genFunctionCall(
          UTILS_LIB + "." + utilsLibFunctionName,
          genMaybeCast(arg0, NumberData.class),
          genMaybeCast(arg1, NumberData.class));
      return new JavaExpr(exprText, NumberData.class, Integer.MAX_VALUE);
    }
  }

}
