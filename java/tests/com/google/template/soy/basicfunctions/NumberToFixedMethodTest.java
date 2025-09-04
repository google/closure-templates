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

import com.google.template.soy.plugin.java.restricted.testing.SoyJavaSourceFunctionTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class NumberToFixedMethodTest {

  @Test
  public void testComputeForJavaSource() {
    NumberToFixedMethod numberToFixedMethod = new NumberToFixedMethod();
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(numberToFixedMethod);

    assertThat(tester.callMethod(1.2345)).isEqualTo("1");
    assertThat(tester.callMethod(1.7890)).isEqualTo("2");
    assertThat(tester.callMethod(2.5670)).isEqualTo("3");
    assertThat(tester.callMethod(3.4)).isEqualTo("3");
    assertThat(tester.callMethod(3.5)).isEqualTo("4");

    assertThat(tester.callMethod(1, 0)).isEqualTo("1");
    assertThat(tester.callMethod(1.0, 0)).isEqualTo("1");
    assertThat(tester.callMethod(1.0, 1)).isEqualTo("1.0");
    assertThat(tester.callMethod(1.0, 2)).isEqualTo("1.00");
    assertThat(tester.callMethod(1.0, 3)).isEqualTo("1.000");
    assertThat(tester.callMethod(1, 3)).isEqualTo("1.000");
    assertThat(tester.callMethod(1.0, 4)).isEqualTo("1.0000");
    assertThat(tester.callMethod(1.0, 5)).isEqualTo("1.00000");
    assertThat(tester.callMethod(1.0, 6)).isEqualTo("1.000000");
    assertThat(tester.callMethod(1.0, 7)).isEqualTo("1.0000000");
    assertThat(tester.callMethod(1.0, 8)).isEqualTo("1.00000000");
  }
}
