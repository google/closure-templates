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

import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.plugin.java.restricted.testing.SoyJavaSourceFunctionTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for IsNonnullFunction.
 *
 */
@RunWith(JUnit4.class)
public class IsNonnullFunctionTest {

  @Test
  public void testComputeForJavaSource() {
    IsNonnullFunction isNonnullFunction = new IsNonnullFunction();
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(isNonnullFunction);

    assertThat(tester.callFunction(UndefinedData.INSTANCE)).isEqualTo(false);
    assertThat(tester.callFunction(NullData.INSTANCE)).isEqualTo(false);
    assertThat(tester.callFunction(0)).isEqualTo(true);
    assertThat(tester.callFunction(IntegerData.forValue(0))).isEqualTo(true);
    assertThat(tester.callFunction("")).isEqualTo(true);
  }

}
