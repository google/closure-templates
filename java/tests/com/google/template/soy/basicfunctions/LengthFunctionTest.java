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
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.jssrc.restricted.JsExpr;

import junit.framework.TestCase;


/**
 * Unit tests for LengthFunction.
 *
 */
public class LengthFunctionTest extends TestCase {


  public void testComputeForTofu() {

    LengthFunction lengthFunction = new LengthFunction();
    SoyData list = new SoyListData(1, 3, 5, 7);
    assertEquals(IntegerData.forValue(4),
                 lengthFunction.computeForTofu(ImmutableList.of(list)));
  }


  public void testComputeForJsSrc() {

    LengthFunction lengthFunction = new LengthFunction();
    JsExpr expr = new JsExpr("JS_CODE", Integer.MAX_VALUE);
    assertEquals(new JsExpr("JS_CODE.length", Integer.MAX_VALUE),
                 lengthFunction.computeForJsSrc(ImmutableList.of(expr)));
  }


  public void testComputeForJavaSrc() {

    LengthFunction lengthFunction = new LengthFunction();

    JavaExpr expr = new JavaExpr("JAVA_CODE", SoyListData.class, Integer.MAX_VALUE);
    assertEquals(
        new JavaExpr(
            "com.google.template.soy.data.restricted.IntegerData.forValue((JAVA_CODE).length())",
            IntegerData.class, Integer.MAX_VALUE),
        lengthFunction.computeForJavaSrc(ImmutableList.of(expr)));

    expr = new JavaExpr("JAVA_CODE", SoyData.class, Integer.MAX_VALUE);
    assertEquals(
        new JavaExpr(
            "com.google.template.soy.data.restricted.IntegerData.forValue(" +
                "((com.google.template.soy.data.SoyListData) JAVA_CODE).length())",
            IntegerData.class, Integer.MAX_VALUE),
        lengthFunction.computeForJavaSrc(ImmutableList.of(expr)));
  }

}
