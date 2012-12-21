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
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.jssrc.restricted.JsExpr;

import junit.framework.TestCase;


/**
 * Unit tests for CeilingFunction.
 *
 * @author Kai Huang
 */
public class CeilingFunctionTest extends TestCase {


  public void testComputeForTofu() {

    CeilingFunction ceilingFunction = new CeilingFunction();

    SoyData float0 = FloatData.forValue(7.5);
    assertEquals(IntegerData.forValue(8),
                 ceilingFunction.computeForTofu(ImmutableList.of(float0)));

    SoyData integer = IntegerData.forValue(14);
    assertEquals(IntegerData.forValue(14),
                 ceilingFunction.computeForTofu(ImmutableList.of(integer)));
  }


  public void testComputeForJsSrc() {

    CeilingFunction ceilingFunction = new CeilingFunction();
    JsExpr expr = new JsExpr("JS_CODE", Integer.MAX_VALUE);
    assertEquals(new JsExpr("Math.ceil(JS_CODE)", Integer.MAX_VALUE),
                 ceilingFunction.computeForJsSrc(ImmutableList.of(expr)));
  }


  public void testComputeForJavaSrc() {

    CeilingFunction ceilingFunction = new CeilingFunction();
    JavaExpr expr = new JavaExpr("JAVA_CODE", FloatData.class, Integer.MAX_VALUE);
    assertEquals(
        new JavaExpr(
            "com.google.template.soy.data.restricted.IntegerData.forValue(" +
                "(int) Math.ceil(JAVA_CODE.numberValue()))",
            IntegerData.class, Integer.MAX_VALUE),
        ceilingFunction.computeForJavaSrc(ImmutableList.of(expr)));
  }

}
