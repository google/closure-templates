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
import com.google.template.soy.jssrc.restricted.JsExpr;

import junit.framework.TestCase;


/**
 * Unit tests for MaxFunction.
 *
 */
public class MaxFunctionTest extends TestCase {


  public void testComputeForJava() {

    MaxFunction maxFunction = new MaxFunction();

    SoyValue float0 = FloatData.forValue(7.5);
    SoyValue float1 = FloatData.forValue(7.777);
    assertEquals(FloatData.forValue(7.777),
                 maxFunction.computeForJava(ImmutableList.of(float0, float1)));

    SoyValue integer0 = IntegerData.forValue(-7);
    SoyValue integer1 = IntegerData.forValue(-8);
    assertEquals(IntegerData.forValue(-7),
                 maxFunction.computeForJava(ImmutableList.of(integer0, integer1)));
  }


  public void testComputeForJsSrc() {

    MaxFunction maxFunction = new MaxFunction();
    JsExpr expr0 = new JsExpr("JS_CODE_0", Integer.MAX_VALUE);
    JsExpr expr1 = new JsExpr("JS_CODE_1", Integer.MAX_VALUE);
    assertEquals(new JsExpr("Math.max(JS_CODE_0, JS_CODE_1)", Integer.MAX_VALUE),
                 maxFunction.computeForJsSrc(ImmutableList.of(expr0, expr1)));
  }

}
