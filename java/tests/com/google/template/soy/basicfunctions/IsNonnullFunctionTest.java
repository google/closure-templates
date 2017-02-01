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
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
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

    assertEquals(
        BooleanData.FALSE,
        isNonnullFunction.computeForJava(ImmutableList.<SoyValue>of(UndefinedData.INSTANCE)));
    assertEquals(
        BooleanData.FALSE,
        isNonnullFunction.computeForJava(ImmutableList.<SoyValue>of(NullData.INSTANCE)));
    assertEquals(
        BooleanData.TRUE,
        isNonnullFunction.computeForJava(ImmutableList.<SoyValue>of(IntegerData.forValue(0))));
    assertEquals(
        BooleanData.TRUE,
        isNonnullFunction.computeForJava(ImmutableList.<SoyValue>of(StringData.forValue(""))));
  }

  @Test
  public void testComputeForJsSrc() {
    IsNonnullFunction isNonnullFunction = new IsNonnullFunction();
    JsExpr expr = new JsExpr("JS_CODE", Integer.MAX_VALUE);
    assertEquals(
        new JsExpr("JS_CODE != null", Operator.NOT_EQUAL.getPrecedence()),
        isNonnullFunction.computeForJsSrc(ImmutableList.of(expr)));
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
