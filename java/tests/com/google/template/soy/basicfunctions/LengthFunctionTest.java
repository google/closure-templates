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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.pysrc.restricted.PyExpr;

import junit.framework.TestCase;


/**
 * Unit tests for LengthFunction.
 *
 */
public class LengthFunctionTest extends TestCase {

  public void testComputeForJava() {
    LengthFunction lengthFunction = new LengthFunction();
    SoyValue list = SoyValueHelper.UNCUSTOMIZED_INSTANCE.newEasyList(1, 3, 5, 7);
    assertEquals(IntegerData.forValue(4), lengthFunction.computeForJava(ImmutableList.of(list)));
  }

  public void testComputeForJsSrc() {
    LengthFunction lengthFunction = new LengthFunction();
    JsExpr expr = new JsExpr("JS_CODE", Integer.MAX_VALUE);
    assertEquals(new JsExpr("JS_CODE.length", Integer.MAX_VALUE),
                 lengthFunction.computeForJsSrc(ImmutableList.of(expr)));
  }

  public void testComputeForPySrc() {
    LengthFunction lengthFunction = new LengthFunction();
    PyExpr expr = new PyExpr("data", Integer.MAX_VALUE);
    assertThat(lengthFunction.computeForPySrc(ImmutableList.of(expr)))
        .isEqualTo(new PyExpr("len(data)", Integer.MAX_VALUE));
  }
}
