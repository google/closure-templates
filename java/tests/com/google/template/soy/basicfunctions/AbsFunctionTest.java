/*
 * Copyright 2025 Google Inc.
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

/** Unit tests for AbsFunction. */
@RunWith(JUnit4.class)
public class AbsFunctionTest {

  @Test
  public void testComputeForJavaSource() {
    AbsFunction absFunction = new AbsFunction();

    SoyJavaSourceFunctionTester factory = new SoyJavaSourceFunctionTester(absFunction);
    assertThat(factory.callFunction(1)).isEqualTo(IntegerData.forValue(1));
    assertThat(factory.callFunction(-2.5)).isEqualTo(FloatData.forValue(2.5));
    assertThat(factory.callFunction(FloatData.forValue(2.5))).isEqualTo(FloatData.forValue(2.5));
    assertThat(factory.callFunction(IntegerData.forValue(-1))).isEqualTo(IntegerData.forValue(1));
    assertThat(factory.callFunction(IntegerData.forValue(-0))).isEqualTo(IntegerData.forValue(0));
    assertThat(factory.callFunction(FloatData.forValue(0.0))).isEqualTo(FloatData.forValue(0.0));
  }
}
