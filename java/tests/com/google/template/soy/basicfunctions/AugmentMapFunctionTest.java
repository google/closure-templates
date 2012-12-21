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
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.jssrc.restricted.JsExpr;

import junit.framework.TestCase;


/**
 * Unit tests for KeysFunction.
 *
 * @author Kai Huang
 */
public class AugmentMapFunctionTest extends TestCase {


  public void testCompute() {

    AugmentMapFunction augmentMapFunction = new AugmentMapFunction();
    SoyMapData origMap =
        new SoyMapData("aaa", "blah", "bbb", "bleh", "ccc", new SoyMapData("xxx", 2));
    SoyMapData additionalMap =
        new SoyMapData("aaa", "bluh", "ccc", new SoyMapData("yyy", 5));
    SoyMapData augmentedMap = (SoyMapData) augmentMapFunction.compute(
        ImmutableList.<SoyData>of(origMap, additionalMap));

    assertEquals("bluh", augmentedMap.getString("aaa"));
    assertEquals("bleh", augmentedMap.getString("bbb"));
    assertEquals(5, augmentedMap.getInteger("ccc.yyy"));
    assertEquals(null, augmentedMap.get("ccc.xxx"));
  }


  public void testComputeForJsSrc() {

    AugmentMapFunction augmentMapFunction = new AugmentMapFunction();
    JsExpr baseMapExpr = new JsExpr("BASE_MAP_JS_CODE", Integer.MAX_VALUE);
    JsExpr additionalMapExpr = new JsExpr("ADDITIONAL_MAP_JS_CODE", Integer.MAX_VALUE);
    assertEquals(
        new JsExpr("soy.$$augmentMap(BASE_MAP_JS_CODE, ADDITIONAL_MAP_JS_CODE)", Integer.MAX_VALUE),
        augmentMapFunction.computeForJsSrc(ImmutableList.of(baseMapExpr, additionalMapExpr)));
  }

}
