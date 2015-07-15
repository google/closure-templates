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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.pysrc.restricted.PyExpr;

import junit.framework.TestCase;


/**
 * Unit tests for KeysFunction.
 *
 */
public class AugmentMapFunctionTest extends TestCase {


  private static final SoyValueHelper VALUE_HELPER = SoyValueHelper.UNCUSTOMIZED_INSTANCE;


  public void testComputeForJava() {
    AugmentMapFunction augmentMapFunction = new AugmentMapFunction();
    SoyMap origMap = VALUE_HELPER.newEasyDict(
        "aaa", "blah", "bbb", "bleh", "ccc", VALUE_HELPER.newEasyDict("xxx", 2));
    SoyMap additionalMap =
        VALUE_HELPER.newEasyDict("aaa", "bluh", "ccc", VALUE_HELPER.newEasyDict("yyy", 5));
    SoyDict augmentedDict = (SoyDict) augmentMapFunction.computeForJava(
        ImmutableList.<SoyValue>of(origMap, additionalMap));

    assertEquals("bluh", augmentedDict.getField("aaa").stringValue());
    assertEquals("bleh", augmentedDict.getItem(StringData.forValue("bbb")).stringValue());
    assertEquals(5, ((SoyDict) augmentedDict.getField("ccc")).getField("yyy").integerValue());
    assertEquals(null, ((SoyDict) augmentedDict.getField("ccc")).getField("xxx"));
  }

  public void testComputeForJsSrc() {
    AugmentMapFunction augmentMapFunction = new AugmentMapFunction();
    JsExpr baseMapExpr = new JsExpr("BASE_MAP_JS_CODE", Integer.MAX_VALUE);
    JsExpr additionalMapExpr = new JsExpr("ADDITIONAL_MAP_JS_CODE", Integer.MAX_VALUE);
    assertEquals(
        new JsExpr("soy.$$augmentMap(BASE_MAP_JS_CODE, ADDITIONAL_MAP_JS_CODE)", Integer.MAX_VALUE),
        augmentMapFunction.computeForJsSrc(ImmutableList.of(baseMapExpr, additionalMapExpr)));
  }

  public void testComputeForPySrc() {
    AugmentMapFunction augmentMapFunction = new AugmentMapFunction();
    PyExpr baseMapExpr = new PyExpr("base", Integer.MAX_VALUE);
    PyExpr additionalMapExpr = new PyExpr("additional", Integer.MAX_VALUE);
    assertThat(augmentMapFunction.computeForPySrc(ImmutableList.of(baseMapExpr, additionalMapExpr)))
        .isEqualTo(new PyExpr("dict(base, **additional)", Integer.MAX_VALUE));
  }
}
