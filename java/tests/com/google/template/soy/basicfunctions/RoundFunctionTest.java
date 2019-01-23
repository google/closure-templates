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

import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.plugin.java.restricted.testing.SoyJavaSourceFunctionTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for RoundFunction.
 *
 */
@RunWith(JUnit4.class)
public class RoundFunctionTest {

  @Test
  public void testComputeForJavaSource() {
    RoundFunction roundFunction = new RoundFunction();
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(roundFunction);

    double input = 9753.141592653590;
    assertThat(tester.callFunction(1)).isEqualTo(1);
    assertThat(tester.callFunction(input)).isEqualTo(9753);
    assertThat(tester.callFunction(input, 0)).isEqualTo(IntegerData.forValue(9753));
    assertThat(tester.callFunction(input, 4)).isEqualTo(FloatData.forValue(9753.1416));
    assertThat(tester.callFunction(input, -2)).isEqualTo(IntegerData.forValue(9800));
  }
}
