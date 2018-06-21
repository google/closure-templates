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
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverterUtility;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.pysrc.restricted.PyExpr;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for KeysFunction.
 *
 */
@RunWith(JUnit4.class)
public class AugmentMapFunctionTest {

  @Test
  public void testComputeForJava() {
    AugmentMapFunction augmentMapFunction = new AugmentMapFunction();
    SoyLegacyObjectMap origMap =
        SoyValueConverterUtility.newDict(
            "aaa", "blah", "bbb", "bleh", "ccc", SoyValueConverterUtility.newDict("xxx", 2));
    SoyLegacyObjectMap additionalMap =
        SoyValueConverterUtility.newDict(
            "aaa", "bluh", "ccc", SoyValueConverterUtility.newDict("yyy", 5));
    SoyDict augmentedDict =
        (SoyDict)
            augmentMapFunction.computeForJava(ImmutableList.<SoyValue>of(origMap, additionalMap));

    assertThat(augmentedDict.getField("aaa").stringValue()).isEqualTo("bluh");
    assertThat(augmentedDict.getItem(StringData.forValue("bbb")).stringValue()).isEqualTo("bleh");
    assertThat(((SoyDict) augmentedDict.getField("ccc")).getField("yyy").integerValue())
        .isEqualTo(5);
    assertThat(((SoyDict) augmentedDict.getField("ccc")).getField("xxx")).isEqualTo(null);
  }

  @Test
  public void testComputeForJsSrc() {
    AugmentMapFunction augmentMapFunction = new AugmentMapFunction();
    JsExpr baseMapExpr = new JsExpr("BASE_MAP_JS_CODE", Integer.MAX_VALUE);
    JsExpr additionalMapExpr = new JsExpr("ADDITIONAL_MAP_JS_CODE", Integer.MAX_VALUE);
    assertThat(augmentMapFunction.computeForJsSrc(ImmutableList.of(baseMapExpr, additionalMapExpr)))
        .isEqualTo(
            new JsExpr(
                "soy.$$augmentMap(BASE_MAP_JS_CODE, ADDITIONAL_MAP_JS_CODE)", Integer.MAX_VALUE));
  }

  @Test
  public void testComputeForPySrc() {
    AugmentMapFunction augmentMapFunction = new AugmentMapFunction();
    PyExpr baseMapExpr = new PyExpr("base", Integer.MAX_VALUE);
    PyExpr additionalMapExpr = new PyExpr("additional", Integer.MAX_VALUE);
    assertThat(augmentMapFunction.computeForPySrc(ImmutableList.of(baseMapExpr, additionalMapExpr)))
        .isEqualTo(new PyExpr("dict(base, **additional)", Integer.MAX_VALUE));
  }
}
