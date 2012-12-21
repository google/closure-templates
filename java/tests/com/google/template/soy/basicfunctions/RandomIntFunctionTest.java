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
import com.google.common.collect.Sets;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.jssrc.restricted.JsExpr;

import junit.framework.TestCase;

import java.util.Set;


/**
 * Unit tests for RandomIntFunction.
 *
 * @author Kai Huang
 */
public class RandomIntFunctionTest extends TestCase {


  public void testComputeForTofu() {

    RandomIntFunction randomIntFunction = new RandomIntFunction();

    SoyData arg = IntegerData.ONE;
    assertEquals(IntegerData.ZERO,
                 randomIntFunction.computeForTofu(ImmutableList.of(arg)));

    arg = IntegerData.forValue(3);
    Set<Integer> seenResults = Sets.newHashSetWithExpectedSize(3);
    for (int i = 0; i < 100; i++) {
      int result = randomIntFunction.computeForTofu(ImmutableList.of(arg)).integerValue();
      assertTrue(0 <= result && result <= 2);
      seenResults.add(result);
      if (seenResults.size() == 3) {
        break;
      }
    }
    assertEquals(3, seenResults.size());
  }


  public void testComputeForJsSrc() {

    RandomIntFunction randomIntFunction = new RandomIntFunction();
    JsExpr argExpr = new JsExpr("JS_CODE", Integer.MAX_VALUE);
    assertEquals(new JsExpr("Math.floor(Math.random() * JS_CODE)", Integer.MAX_VALUE),
                 randomIntFunction.computeForJsSrc(ImmutableList.of(argExpr)));
  }


  public void testComputeForJavaSrc() {

    RandomIntFunction randomIntFunction = new RandomIntFunction();
    JavaExpr argExpr = new JavaExpr("JAVA_CODE", SoyData.class, Integer.MAX_VALUE);
    assertEquals(
        new JavaExpr(
            "com.google.template.soy.data.restricted.IntegerData.forValue(" +
                "(int) Math.floor(Math.random() * JAVA_CODE.integerValue()))",
            IntegerData.class, Integer.MAX_VALUE),
        randomIntFunction.computeForJavaSrc(ImmutableList.of(argExpr)));
  }

}
