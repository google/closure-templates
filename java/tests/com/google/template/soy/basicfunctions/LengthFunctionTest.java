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

import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverterUtility;
import com.google.template.soy.plugin.java.restricted.testing.SoyJavaSourceFunctionTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for LengthFunction.
 *
 */
@RunWith(JUnit4.class)
public class LengthFunctionTest {

  @Test
  public void testComputeForJavaSource() {
    LengthFunction lengthFunction = new LengthFunction();
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(lengthFunction);
    SoyValue list = SoyValueConverterUtility.newList(1, 3, 5, 7);
    assertThat(tester.callFunction(list)).isEqualTo(4);
  }
}
