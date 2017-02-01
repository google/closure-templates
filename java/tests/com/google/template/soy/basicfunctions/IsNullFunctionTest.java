/*
 * Copyright 2016 Google Inc.
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
import static com.google.template.soy.data.restricted.BooleanData.FALSE;
import static com.google.template.soy.data.restricted.BooleanData.TRUE;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SoyValue;
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

/** Tests for {@link IsNullFunction}. */
@RunWith(JUnit4.class)
public final class IsNullFunctionTest {

  private static final IsNullFunction IS_NULL = new IsNullFunction();

  @Test
  public void testComputeForJava() {
    assertThat(IS_NULL.computeForJava(ImmutableList.<SoyValue>of(UndefinedData.INSTANCE)))
        .isEqualTo(TRUE);
    assertThat(IS_NULL.computeForJava(ImmutableList.<SoyValue>of(NullData.INSTANCE)))
        .isEqualTo(TRUE);
    assertThat(IS_NULL.computeForJava(ImmutableList.<SoyValue>of(IntegerData.forValue(0))))
        .isEqualTo(FALSE);
    assertThat(IS_NULL.computeForJava(ImmutableList.<SoyValue>of(StringData.forValue(""))))
        .isEqualTo(FALSE);
  }

  @Test
  public void testComputeForJsSrc() {
    JsExpr expr = new JsExpr("JS_CODE", Integer.MAX_VALUE);
    assertThat(IS_NULL.computeForJsSrc(ImmutableList.of(expr)))
        .isEqualTo(new JsExpr("JS_CODE == null", Operator.EQUAL.getPrecedence()));
  }

  @Test
  public void testComputeForPySrc() {
    PyExpr expr = new PyExpr("PY_CODE", Integer.MAX_VALUE);
    assertThat(IS_NULL.computeForPySrc(ImmutableList.of(expr)))
        .isEqualTo(
            new PyExpr("PY_CODE is None", PyExprUtils.pyPrecedenceForOperator(Operator.EQUAL)));
  }
}
