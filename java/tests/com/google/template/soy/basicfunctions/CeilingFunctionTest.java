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
import static com.google.template.soy.jbcsrc.restricted.testing.ExpressionTester.assertThatExpression;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.types.UnknownType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for CeilingFunction.
 *
 */
@RunWith(JUnit4.class)
public class CeilingFunctionTest {

  @Test
  public void testComputeForJava() {
    CeilingFunction ceilingFunction = new CeilingFunction();

    SoyValue float0 = FloatData.forValue(7.5);
    assertThat(ceilingFunction.computeForJava(ImmutableList.of(float0)))
        .isEqualTo(IntegerData.forValue(8));

    SoyValue integer = IntegerData.forValue(14);
    assertThat(ceilingFunction.computeForJava(ImmutableList.of(integer)))
        .isEqualTo(IntegerData.forValue(14));
  }

  @Test
  public void testComputeForJbcSrc() {
    CeilingFunction ceilingFunction = new CeilingFunction();
    assertThatExpression(
            ceilingFunction.computeForJbcSrc(
                /*context=*/ null,
                ImmutableList.of(SoyExpression.forInt(BytecodeUtils.constant(1L)))))
        .evaluatesTo(1L);

    assertThatExpression(
            ceilingFunction.computeForJbcSrc(
                /*context=*/ null,
                ImmutableList.of(SoyExpression.forFloat(BytecodeUtils.constant(2.5D)))))
        .evaluatesTo(3L);
    assertThatExpression(
            ceilingFunction.computeForJbcSrc(
                /*context=*/ null,
                ImmutableList.of(
                    SoyExpression.forSoyValue(
                        UnknownType.getInstance(),
                        MethodRef.FLOAT_DATA_FOR_VALUE.invoke(BytecodeUtils.constant(2.5D))))))
        .evaluatesTo(3L);
  }

  @Test
  public void testComputeForJsSrc() {
    CeilingFunction ceilingFunction = new CeilingFunction();
    JsExpr expr = new JsExpr("JS_CODE", Integer.MAX_VALUE);
    assertThat(ceilingFunction.computeForJsSrc(ImmutableList.of(expr)))
        .isEqualTo(new JsExpr("Math.ceil(JS_CODE)", Integer.MAX_VALUE));
  }

  @Test
  public void testComputeForPySrc() {
    CeilingFunction ceilingFunction = new CeilingFunction();
    PyExpr expr = new PyExpr("number", Integer.MAX_VALUE);
    assertThat(ceilingFunction.computeForPySrc(ImmutableList.of(expr)))
        .isEqualTo(new PyExpr("int(math.ceil(number))", Integer.MAX_VALUE));
  }
}
