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
import com.google.common.collect.Sets;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.pysrc.restricted.PyExpr;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for RandomIntFunction.
 *
 */
@RunWith(JUnit4.class)
public class RandomIntFunctionTest {

  @Test
  public void testComputeForJava() {
    RandomIntFunction randomIntFunction = new RandomIntFunction();

    SoyValue arg = IntegerData.ONE;
    assertThat(randomIntFunction.computeForJava(ImmutableList.of(arg))).isEqualTo(IntegerData.ZERO);

    arg = IntegerData.forValue(3);
    Set<Integer> seenResults = Sets.newHashSetWithExpectedSize(3);
    for (int i = 0; i < 100; i++) {
      int result = randomIntFunction.computeForJava(ImmutableList.of(arg)).integerValue();
      assertThat(result).isAtLeast(0);
      assertThat(result).isAtMost(2);
      seenResults.add(result);
      if (seenResults.size() == 3) {
        break;
      }
    }
    assertThat(seenResults).hasSize(3);
  }

  @Test
  public void testComputeForJsSrc() {
    RandomIntFunction randomIntFunction = new RandomIntFunction();
    JsExpr argExpr = new JsExpr("JS_CODE", Integer.MAX_VALUE);
    assertThat(randomIntFunction.computeForJsSrc(ImmutableList.of(argExpr)))
        .isEqualTo(new JsExpr("Math.floor(Math.random() * JS_CODE)", Integer.MAX_VALUE));
  }

  @Test
  public void testComputeForPySrc() {
    RandomIntFunction randomIntFunction = new RandomIntFunction();
    PyExpr argExpr = new PyExpr("upper", Integer.MAX_VALUE);
    assertThat(randomIntFunction.computeForPySrc(ImmutableList.of(argExpr)))
        .isEqualTo(new PyExpr("random.randint(0, upper - 1)", Integer.MAX_VALUE));
  }
}
