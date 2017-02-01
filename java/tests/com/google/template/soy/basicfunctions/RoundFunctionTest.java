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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.pysrc.restricted.PyExpr;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for RoundFunction.
 *
 */
@RunWith(JUnit4.class)
public class RoundFunctionTest {

  @Test
  public void testComputeForJava() {
    RoundFunction roundFunction = new RoundFunction();

    SoyValue float0 = FloatData.forValue(9753.141592653590);
    assertEquals(
        IntegerData.forValue(9753),
        roundFunction.computeForJava(ImmutableList.<SoyValue>of(float0)));

    SoyValue numDigitsAfterPt = IntegerData.ZERO;
    assertEquals(
        IntegerData.forValue(9753),
        roundFunction.computeForJava(ImmutableList.of(float0, numDigitsAfterPt)));

    numDigitsAfterPt = IntegerData.forValue(4);
    assertEquals(
        FloatData.forValue(9753.1416),
        roundFunction.computeForJava(ImmutableList.of(float0, numDigitsAfterPt)));

    numDigitsAfterPt = IntegerData.forValue(-2);
    assertEquals(
        IntegerData.forValue(9800),
        roundFunction.computeForJava(ImmutableList.of(float0, numDigitsAfterPt)));
  }

  @Test
  public void testComputeForJsSrc() {
    RoundFunction roundFunction = new RoundFunction();

    JsExpr floatExpr = new JsExpr("FLOAT_JS_CODE", Integer.MAX_VALUE);
    assertEquals(
        new JsExpr("Math.round(FLOAT_JS_CODE)", Integer.MAX_VALUE),
        roundFunction.computeForJsSrc(ImmutableList.of(floatExpr)));

    JsExpr numDigitsAfterPtExpr = new JsExpr("0", Integer.MAX_VALUE);
    assertEquals(
        new JsExpr("Math.round(FLOAT_JS_CODE)", Integer.MAX_VALUE),
        roundFunction.computeForJsSrc(ImmutableList.of(floatExpr, numDigitsAfterPtExpr)));

    numDigitsAfterPtExpr = new JsExpr("4", Integer.MAX_VALUE);
    assertEquals(
        new JsExpr("Math.round(FLOAT_JS_CODE * 10000) / 10000", Operator.DIVIDE_BY.getPrecedence()),
        roundFunction.computeForJsSrc(ImmutableList.of(floatExpr, numDigitsAfterPtExpr)));

    numDigitsAfterPtExpr = new JsExpr("-2", Operator.NEGATIVE.getPrecedence());
    assertEquals(
        new JsExpr("Math.round(FLOAT_JS_CODE / 100) * 100", Operator.TIMES.getPrecedence()),
        roundFunction.computeForJsSrc(ImmutableList.of(floatExpr, numDigitsAfterPtExpr)));

    numDigitsAfterPtExpr = new JsExpr("NUM_DIGITS_JS_CODE", Integer.MAX_VALUE);
    assertEquals(
        new JsExpr(
            "Math.round(FLOAT_JS_CODE * Math.pow(10, NUM_DIGITS_JS_CODE)) /"
                + " Math.pow(10, NUM_DIGITS_JS_CODE)",
            Operator.DIVIDE_BY.getPrecedence()),
        roundFunction.computeForJsSrc(ImmutableList.of(floatExpr, numDigitsAfterPtExpr)));
  }

  @Test
  public void testComputeForPySrc() {
    RoundFunction roundFunction = new RoundFunction();

    String modifiedNumber =
        "(math.frexp(number)[0] + sys.float_info.epsilon)*2**math.frexp(number)[1]";

    PyExpr floatExpr = new PyExpr("number", Integer.MAX_VALUE);
    assertThat(roundFunction.computeForPySrc(ImmutableList.of(floatExpr)))
        .isEqualTo(
            new PyExpr(
                "runtime.simplify_num(round(" + modifiedNumber + ", 0), 0)", Integer.MAX_VALUE));

    PyExpr numDigitsAfterPtExpr = new PyExpr("0", Integer.MAX_VALUE);
    assertThat(roundFunction.computeForPySrc(ImmutableList.of(floatExpr, numDigitsAfterPtExpr)))
        .isEqualTo(
            new PyExpr(
                "runtime.simplify_num(round(" + modifiedNumber + ", 0), 0)", Integer.MAX_VALUE));

    numDigitsAfterPtExpr = new PyExpr("4", Integer.MAX_VALUE);
    assertThat(roundFunction.computeForPySrc(ImmutableList.of(floatExpr, numDigitsAfterPtExpr)))
        .isEqualTo(
            new PyExpr(
                "runtime.simplify_num(round(" + modifiedNumber + ", 4), 4)", Integer.MAX_VALUE));

    numDigitsAfterPtExpr = new PyExpr("-2", Operator.NEGATIVE.getPrecedence());
    assertThat(roundFunction.computeForPySrc(ImmutableList.of(floatExpr, numDigitsAfterPtExpr)))
        .isEqualTo(
            new PyExpr(
                "runtime.simplify_num(round(" + modifiedNumber + ", -2), -2)", Integer.MAX_VALUE));

    numDigitsAfterPtExpr = new PyExpr("digits", Integer.MAX_VALUE);
    assertThat(roundFunction.computeForPySrc(ImmutableList.of(floatExpr, numDigitsAfterPtExpr)))
        .isEqualTo(
            new PyExpr(
                "runtime.simplify_num(round(" + modifiedNumber + ", digits), digits)",
                Integer.MAX_VALUE));
  }
}
