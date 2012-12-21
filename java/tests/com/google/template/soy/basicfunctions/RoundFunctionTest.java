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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.jssrc.restricted.JsExpr;

import junit.framework.TestCase;


/**
 * Unit tests for RoundFunction.
 *
 * @author Kai Huang
 */
public class RoundFunctionTest extends TestCase {


  public void testComputeForTofu() {

    RoundFunction roundFunction = new RoundFunction();

    SoyData float0 = FloatData.forValue(9753.141592653590);
    assertEquals(IntegerData.forValue(9753),
                 roundFunction.computeForTofu(ImmutableList.<SoyData>of(float0)));

    SoyData numDigitsAfterPt = IntegerData.ZERO;
    assertEquals(IntegerData.forValue(9753),
                 roundFunction.computeForTofu(ImmutableList.of(float0, numDigitsAfterPt)));

    numDigitsAfterPt = IntegerData.forValue(4);
    assertEquals(FloatData.forValue(9753.1416),
                 roundFunction.computeForTofu(ImmutableList.of(float0, numDigitsAfterPt)));

    numDigitsAfterPt = IntegerData.forValue(-2);
    assertEquals(IntegerData.forValue(9800),
                 roundFunction.computeForTofu(ImmutableList.of(float0, numDigitsAfterPt)));
  }


  public void testComputeForJsSrc() {

    RoundFunction roundFunction = new RoundFunction();

    JsExpr floatExpr = new JsExpr("FLOAT_JS_CODE", Integer.MAX_VALUE);
    assertEquals(new JsExpr("Math.round(FLOAT_JS_CODE)", Integer.MAX_VALUE),
                 roundFunction.computeForJsSrc(ImmutableList.of(floatExpr)));

    JsExpr numDigitsAfterPtExpr = new JsExpr("0", Integer.MAX_VALUE);
    assertEquals(new JsExpr("Math.round(FLOAT_JS_CODE)", Integer.MAX_VALUE),
                 roundFunction.computeForJsSrc(
                     ImmutableList.of(floatExpr, numDigitsAfterPtExpr)));

    numDigitsAfterPtExpr = new JsExpr("4", Integer.MAX_VALUE);
    assertEquals(new JsExpr("Math.round(FLOAT_JS_CODE * 10000) / 10000",
                            Operator.DIVIDE_BY.getPrecedence()),
                 roundFunction.computeForJsSrc(
                     ImmutableList.of(floatExpr, numDigitsAfterPtExpr)));

    numDigitsAfterPtExpr = new JsExpr("-2", Operator.NEGATIVE.getPrecedence());
    assertEquals(new JsExpr("Math.round(FLOAT_JS_CODE / 100) * 100",
                            Operator.TIMES.getPrecedence()),
                 roundFunction.computeForJsSrc(
                     ImmutableList.of(floatExpr, numDigitsAfterPtExpr)));

    numDigitsAfterPtExpr = new JsExpr("NUM_DIGITS_JS_CODE", Integer.MAX_VALUE);
    assertEquals(new JsExpr("Math.round(FLOAT_JS_CODE * Math.pow(10, NUM_DIGITS_JS_CODE)) /" +
                            " Math.pow(10, NUM_DIGITS_JS_CODE)",
                            Operator.DIVIDE_BY.getPrecedence()),
                 roundFunction.computeForJsSrc(
                     ImmutableList.of(floatExpr, numDigitsAfterPtExpr)));
  }


  public void testComputeForJavaSrc() {

    RoundFunction roundFunction = new RoundFunction();

    JavaExpr floatExpr = new JavaExpr("FLOAT_JAVA_CODE", FloatData.class, Integer.MAX_VALUE);
    assertEquals(new JavaExpr("com.google.template.soy.javasrc.codedeps.SoyUtils.$$round(" +
                              "FLOAT_JAVA_CODE, null)",
                              NumberData.class, Integer.MAX_VALUE),
                 roundFunction.computeForJavaSrc(ImmutableList.of(floatExpr)));

    JavaExpr numDigitsAfterPtExpr =
        new JavaExpr("NUM_DIGITS_JAVA_CODE", IntegerData.class, Integer.MAX_VALUE);
    assertEquals(new JavaExpr("com.google.template.soy.javasrc.codedeps.SoyUtils.$$round(" +
                              "FLOAT_JAVA_CODE, NUM_DIGITS_JAVA_CODE)",
                              NumberData.class, Integer.MAX_VALUE),
                 roundFunction.computeForJavaSrc(
                     ImmutableList.of(floatExpr, numDigitsAfterPtExpr)));
  }

}
