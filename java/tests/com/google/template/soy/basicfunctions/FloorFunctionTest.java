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
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.pysrc.restricted.PyExpr;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for FloorFunction.
 *
 */
@RunWith(JUnit4.class)
public class FloorFunctionTest {

  @Test
  public void testComputeForJava() {
    FloorFunction floorFunction = new FloorFunction();

    SoyValue float0 = FloatData.forValue(7.5);
    assertEquals(IntegerData.forValue(7), floorFunction.computeForJava(ImmutableList.of(float0)));

    SoyValue integer = IntegerData.forValue(14);
    assertEquals(IntegerData.forValue(14), floorFunction.computeForJava(ImmutableList.of(integer)));
  }

  @Test
  public void testComputeForJsSrc() {
    FloorFunction floorFunction = new FloorFunction();
    JsExpr expr = new JsExpr("JS_CODE", Integer.MAX_VALUE);
    assertEquals(
        new JsExpr("Math.floor(JS_CODE)", Integer.MAX_VALUE),
        floorFunction.computeForJsSrc(ImmutableList.of(expr)));
  }

  @Test
  public void testComputeForPySrc() {
    FloorFunction floorFunction = new FloorFunction();
    PyExpr expr = new PyExpr("number", Integer.MAX_VALUE);
    assertThat(floorFunction.computeForPySrc(ImmutableList.of(expr)))
        .isEqualTo(new PyExpr("int(math.floor(number))", Integer.MAX_VALUE));
  }
}
