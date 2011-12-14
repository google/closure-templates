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

import static com.google.template.soy.javasrc.restricted.SoyJavaSrcFunctionUtils.toNumberJavaExpr;
import static com.google.template.soy.shared.restricted.SoyJavaRuntimeFunctionUtils.toSoyData;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.javasrc.restricted.JavaCodeUtils;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.javasrc.restricted.SoyJavaSrcFunction;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsCodeUtils;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.tofu.restricted.SoyAbstractTofuFunction;

import java.util.List;
import java.util.Set;


/**
 * Soy function that rounds a number to a specified number of digits before or after the decimal
 * point.
 *
 */
@Singleton
@SoyPureFunction
class RoundFunction extends SoyAbstractTofuFunction
    implements SoyJsSrcFunction, SoyJavaSrcFunction {


  @Inject
  RoundFunction() {}


  @Override public String getName() {
    return "round";
  }


  @Override public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1, 2);
  }


  @Override public SoyData compute(List<SoyData> args) {
    SoyData value = args.get(0);
    int numDigitsAfterPt = (args.size() == 2) ? args.get(1).integerValue() : 0 /* default */;

    if (numDigitsAfterPt == 0) {
      if (value instanceof IntegerData) {
        return toSoyData(value.integerValue());
      } else {
        return toSoyData((int) Math.round(value.numberValue()));
      }
    } else if (numDigitsAfterPt > 0) {
      double valueDouble = value.numberValue();
      double shift = Math.pow(10, numDigitsAfterPt);
      return toSoyData(Math.round(valueDouble * shift) / shift);
    } else {
      double valueDouble = value.numberValue();
      double shift = Math.pow(10, -numDigitsAfterPt);
      return toSoyData((int) (Math.round(valueDouble / shift) * shift));
    }
  }


  @Override public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr value = args.get(0);
    JsExpr numDigitsAfterPt = (args.size() == 2) ? args.get(1) : null;

    int numDigitsAfterPtAsInt = 0;
    if (numDigitsAfterPt != null) {
      try {
        numDigitsAfterPtAsInt = Integer.parseInt(numDigitsAfterPt.getText());
      } catch (NumberFormatException nfe) {
        numDigitsAfterPtAsInt = Integer.MIN_VALUE;  // indicates it's not a simple integer literal
      }
    }

    if (numDigitsAfterPtAsInt == 0) {
      // Case 1: round() has only one argument or the second argument is 0.
      return new JsExpr("Math.round(" + value.getText() + ")", Integer.MAX_VALUE);

    } else if ((numDigitsAfterPtAsInt >= 0 && numDigitsAfterPtAsInt <= 12) ||
               numDigitsAfterPtAsInt == Integer.MIN_VALUE) {
      String shiftExprText;
      if (numDigitsAfterPtAsInt >= 0 && numDigitsAfterPtAsInt <= 12) {
        shiftExprText = "1" + "000000000000".substring(0, numDigitsAfterPtAsInt);
      } else {
        shiftExprText = "Math.pow(10, " + numDigitsAfterPt.getText() + ")";
      }
      JsExpr shift = new JsExpr(shiftExprText, Integer.MAX_VALUE);
      JsExpr valueTimesShift = SoyJsCodeUtils.genJsExprUsingSoySyntax(
          Operator.TIMES, Lists.newArrayList(value, shift));
      return new JsExpr(
          "Math.round(" + valueTimesShift.getText() + ") / " + shift.getText(),
          Operator.DIVIDE_BY.getPrecedence());

    } else if (numDigitsAfterPtAsInt < 0 && numDigitsAfterPtAsInt >= -12) {
      String shiftExprText = "1" + "000000000000".substring(0, -numDigitsAfterPtAsInt);
      JsExpr shift = new JsExpr(shiftExprText, Integer.MAX_VALUE);
      JsExpr valueDivideByShift = SoyJsCodeUtils.genJsExprUsingSoySyntax(
          Operator.DIVIDE_BY, Lists.newArrayList(value, shift));
      return new JsExpr(
          "Math.round(" + valueDivideByShift.getText() + ") * " + shift.getText(),
          Operator.TIMES.getPrecedence());

    } else {
      throw new IllegalArgumentException(
          "Second argument to round() function is " + numDigitsAfterPtAsInt +
          ", which is too large in magnitude.");
    }
  }


  @Override public JavaExpr computeForJavaSrc(List<JavaExpr> args) {
    JavaExpr value = args.get(0);
    JavaExpr numDigitsAfterPt = (args.size() == 2) ? args.get(1) : null;

    String numDigitsAfterPtExprText =
        (numDigitsAfterPt != null) ?
        JavaCodeUtils.genMaybeCast(numDigitsAfterPt, IntegerData.class) : "null";
    return toNumberJavaExpr(JavaCodeUtils.genFunctionCall(
        JavaCodeUtils.UTILS_LIB + ".$$round",
        JavaCodeUtils.genMaybeCast(value, NumberData.class),
        numDigitsAfterPtExprText));
  }

}
