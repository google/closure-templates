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

package com.google.template.soy.basicfunctions;

import com.google.template.soy.data.SoyValue;
import com.google.template.soy.internal.targetexpr.TargetExpr;
import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptPluginContext;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValue;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValueFactory;
import com.google.template.soy.plugin.javascript.restricted.SoyJavaScriptSourceFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.shared.restricted.TypedSoyFunction;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Soy function that rounds a number to a specified number of digits before or after the decimal
 * point.
 *
 * <p>TODO(b/112835292): No one should use the 2 parameter overload, it is inaccurate because
 * floating point != decimal, instead they should use an i18n friendly number formatting routine. We
 * should deprecated the 2 argument overload by adding a new function {@code brokenRound()} and then
 * we can encourage people to migrate to a less broken approach. (or we could just add a pow
 * function and inline it).
 *
 */
@SoyFunctionSignature(
    name = "round",
    value = {
      // TODO(b/70946095): these should take number values and return either an int or a number
      @Signature(returnType = "?", parameterTypes = "?"),
      @Signature(
          returnType = "?",
          parameterTypes = {"?", "?"}),
    })
@SoyPureFunction
public final class RoundFunction extends TypedSoyFunction
    implements SoyJavaSourceFunction, SoyJavaScriptSourceFunction, SoyPySrcFunction {

  @Override
  public JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    if (args.size() == 1) {
      return factory.global("Math").invokeMethod("round", args.get(0));
    }
    return factory.callNamespaceFunction("soy", "soy.$$round", args.get(0), args.get(1));
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    PyExpr value = args.get(0);
    PyExpr precision = (args.size() == 2) ? args.get(1) : null;

    int precisionAsInt = convertNumDigits(precision);
    boolean isLiteral = precisionAsInt != Integer.MIN_VALUE;

    if ((precisionAsInt >= -12 && precisionAsInt <= 12) || !isLiteral) {
      // Python rounds ties away from 0 instead of towards infinity as JS and Java do. So to make
      // the behavior consistent, we add the smallest possible float amount to break ties towards
      // infinity.
      String floatBreakdown = "math.frexp(" + value.getText() + ")";
      String precisionValue = isLiteral ? precisionAsInt + "" : precision.getText();
      StringBuilder roundedValue =
          new StringBuilder("round(")
              .append('(')
              .append(floatBreakdown)
              .append("[0]")
              .append(" + sys.float_info.epsilon)*2**")
              .append(floatBreakdown)
              .append("[1]")
              .append(", ")
              .append(precisionValue)
              .append(")");
      // The precision is less than 1. Convert to an int to prevent extraneous decimals in display.
      return new PyExpr(
          "runtime.simplify_num(" + roundedValue + ", " + precisionValue + ")", Integer.MAX_VALUE);
    } else {
      throw new IllegalArgumentException(
          "Second argument to round() function is "
              + precisionAsInt
              + ", which is too large in magnitude.");
    }
  }

  /**
   * Convert the number of digits after the point from an expression to an int.
   *
   * @param numDigitsAfterPt The number of digits after the point as an expression
   * @return The number of digits after the point and an int.
   */
  private static int convertNumDigits(TargetExpr numDigitsAfterPt) {
    int numDigitsAfterPtAsInt = 0;
    if (numDigitsAfterPt != null) {
      try {
        numDigitsAfterPtAsInt = Integer.parseInt(numDigitsAfterPt.getText());
      } catch (NumberFormatException nfe) {
        numDigitsAfterPtAsInt = Integer.MIN_VALUE; // indicates it's not a simple integer literal
      }
    }
    return numDigitsAfterPtAsInt;
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method BOXED_ROUND_FN =
        JavaValueFactory.createMethod(BasicFunctionsRuntime.class, "round", SoyValue.class);

    static final Method BOXED_ROUND_WITH_NUM_DIGITS_AFTER_POINT_FN =
        JavaValueFactory.createMethod(
            BasicFunctionsRuntime.class, "round", SoyValue.class, int.class);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    if (args.size() == 1) {
      return factory.callStaticMethod(Methods.BOXED_ROUND_FN, args.get(0));
    } else {
      return factory.callStaticMethod(
          Methods.BOXED_ROUND_WITH_NUM_DIGITS_AFTER_POINT_FN, args.get(0), args.get(1).asSoyInt());
    }
  }
}
