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
 * Unit tests for MinFunction.
 *
 */
@RunWith(JUnit4.class)
public class MinFunctionTest {

  @Test
  public void testComputeForJavaSource() {
    MinFunction minFunction = new MinFunction();
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(minFunction);

    // Test same LHS & RHS type.
    assertThat(tester.callFunction(7.5, 7.777)).isEqualTo(FloatData.forValue(7.5));
    assertThat(tester.callFunction(-7, -8)).isEqualTo(IntegerData.forValue(-8));
    assertThat(tester.callFunction(FloatData.forValue(7.5), 7.777))
        .isEqualTo(FloatData.forValue(7.5));
    assertThat(tester.callFunction(IntegerData.forValue(-7), -8))
        .isEqualTo(IntegerData.forValue(-8));

    // Test mixed LHS & RHS type.
    assertThat(tester.callFunction(7, 7.777)).isEqualTo(IntegerData.forValue(7));
    assertThat(tester.callFunction(7.777, 8)).isEqualTo(FloatData.forValue(7.777));
    assertThat(tester.callFunction(IntegerData.forValue(7), 7.777))
        .isEqualTo(IntegerData.forValue(7));
    assertThat(tester.callFunction(FloatData.forValue(7.5), 8)).isEqualTo(FloatData.forValue(7.5));
    assertThat(tester.callFunction(FloatData.forValue(7.5), IntegerData.forValue(8)))
        .isEqualTo(FloatData.forValue(7.5));
  }

}
