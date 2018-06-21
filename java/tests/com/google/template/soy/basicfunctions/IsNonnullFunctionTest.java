/*
 * Copyright 2012 Google Inc.
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
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.types.UnknownType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for IsNonnullFunction.
 *
 */
@RunWith(JUnit4.class)
public class IsNonnullFunctionTest {

  @Test
  public void testComputeForJava() {
    IsNonnullFunction isNonnullFunction = new IsNonnullFunction();

    assertThat(isNonnullFunction.computeForJava(ImmutableList.of(UndefinedData.INSTANCE)))
        .isEqualTo(BooleanData.FALSE);
    assertThat(isNonnullFunction.computeForJava(ImmutableList.of(NullData.INSTANCE)))
        .isEqualTo(BooleanData.FALSE);
    assertThat(isNonnullFunction.computeForJava(ImmutableList.of(IntegerData.forValue(0))))
        .isEqualTo(BooleanData.TRUE);
    assertThat(isNonnullFunction.computeForJava(ImmutableList.of(StringData.forValue(""))))
        .isEqualTo(BooleanData.TRUE);
  }

  @Test
  public void testComputeForJbcSrc() {
    IsNonnullFunction isNonnullFunction = new IsNonnullFunction();
    assertThatExpression(
            isNonnullFunction.computeForJbcSrc(
                /*context=*/ null,
                ImmutableList.of(
                    SoyExpression.forSoyValue(
                        UnknownType.getInstance(),
                        BytecodeUtils.constantNull(BytecodeUtils.SOY_VALUE_TYPE)))))
        .evaluatesTo(false);
    assertThatExpression(
            isNonnullFunction.computeForJbcSrc(
                /*context=*/ null,
                ImmutableList.of(SoyExpression.forInt(BytecodeUtils.constant(1L)).box())))
        .evaluatesTo(true);
  }

  @Test
  public void testComputeForJsSrc() {
    IsNonnullFunction isNonnullFunction = new IsNonnullFunction();
    JsExpr expr = new JsExpr("JS_CODE", Integer.MAX_VALUE);
    assertThat(isNonnullFunction.computeForJsSrc(ImmutableList.of(expr)))
        .isEqualTo(new JsExpr("JS_CODE != null", Operator.NOT_EQUAL.getPrecedence()));
  }

  @Test
  public void testComputeForPySrc() {
    IsNonnullFunction isNonnullFunction = new IsNonnullFunction();
    PyExpr expr = new PyExpr("data", Integer.MAX_VALUE);
    assertThat(isNonnullFunction.computeForPySrc(ImmutableList.of(expr)))
        .isEqualTo(
            new PyExpr(
                "data is not None", PyExprUtils.pyPrecedenceForOperator(Operator.NOT_EQUAL)));
  }
}
