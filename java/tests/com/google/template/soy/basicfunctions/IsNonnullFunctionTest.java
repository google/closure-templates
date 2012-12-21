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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.jssrc.restricted.JsExpr;

import junit.framework.TestCase;


/**
 * Unit tests for IsNonnullFunction.
 *
 * @author Kai Huang
 */
public class IsNonnullFunctionTest extends TestCase {


  public void testCompute() {

    IsNonnullFunction isNonnullFunction = new IsNonnullFunction();
    assertEquals(
        BooleanData.FALSE,
        isNonnullFunction.compute(ImmutableList.<SoyData>of(UndefinedData.INSTANCE)));
    assertEquals(
        BooleanData.FALSE,
        isNonnullFunction.compute(ImmutableList.<SoyData>of(NullData.INSTANCE)));
    assertEquals(
        BooleanData.TRUE,
        isNonnullFunction.compute(ImmutableList.<SoyData>of(IntegerData.forValue(0))));
    assertEquals(
        BooleanData.TRUE,
        isNonnullFunction.compute(ImmutableList.<SoyData>of(StringData.forValue(""))));
  }


  public void testComputeForJsSrc() {

    IsNonnullFunction isNonnullFunction = new IsNonnullFunction();
    JsExpr expr = new JsExpr("JS_CODE", Integer.MAX_VALUE);
    assertEquals(
        new JsExpr("JS_CODE != null", Operator.NOT_EQUAL.getPrecedence()),
        isNonnullFunction.computeForJsSrc(ImmutableList.of(expr)));
  }


  public void testComputeForJavaSrc() {

    IsNonnullFunction isNonnullFunction = new IsNonnullFunction();
    JavaExpr expr = new JavaExpr("JAVA_CODE", FloatData.class, Integer.MAX_VALUE);
    assertEquals(
        new JavaExpr(
            "com.google.template.soy.javasrc.codedeps.SoyUtils.$$isNonnull(JAVA_CODE)",
            BooleanData.class, Integer.MAX_VALUE),
        isNonnullFunction.computeForJavaSrc(ImmutableList.of(expr)));
  }

}
