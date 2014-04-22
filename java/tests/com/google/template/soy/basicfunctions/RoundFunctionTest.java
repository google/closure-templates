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
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.restricted.JsExpr;

import junit.framework.TestCase;


/**
 * Unit tests for RoundFunction.
 *
 */
public class RoundFunctionTest extends TestCase {


  public void testComputeForJava() {

    RoundFunction roundFunction = new RoundFunction();

    SoyValue float0 = FloatData.forValue(9753.141592653590);
    assertEquals(IntegerData.forValue(9753),
                 roundFunction.computeForJava(ImmutableList.<SoyValue>of(float0)));

    SoyValue numDigitsAfterPt = IntegerData.ZERO;
    assertEquals(IntegerData.forValue(9753),
                 roundFunction.computeForJava(ImmutableList.of(float0, numDigitsAfterPt)));

    numDigitsAfterPt = IntegerData.forValue(4);
    assertEquals(FloatData.forValue(9753.1416),
                 roundFunction.computeForJava(ImmutableList.of(float0, numDigitsAfterPt)));

    numDigitsAfterPt = IntegerData.forValue(-2);
    assertEquals(IntegerData.forValue(9800),
                 roundFunction.computeForJava(ImmutableList.of(float0, numDigitsAfterPt)));
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

}
