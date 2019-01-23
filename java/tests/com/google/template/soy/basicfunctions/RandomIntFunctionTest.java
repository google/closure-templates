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

import com.google.common.collect.Sets;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.plugin.java.restricted.testing.SoyJavaSourceFunctionTester;
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
  public void testComputeForJavaSource() {
    RandomIntFunction randomIntFunction = new RandomIntFunction();
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(randomIntFunction);

    SoyValue arg = IntegerData.ONE;
    assertThat(tester.callFunction(arg)).isEqualTo(0);

    arg = IntegerData.forValue(3);
    Set<Integer> seenResults = Sets.newHashSetWithExpectedSize(3);
    for (int i = 0; i < 100; i++) {
      int result = ((Long) tester.callFunction(arg)).intValue();
      assertThat(result).isAtLeast(0);
      assertThat(result).isAtMost(2);
      seenResults.add(result);
      if (seenResults.size() == 3) {
        break;
      }
    }
    assertThat(seenResults).hasSize(3);
  }

}
