/*
 * Copyright 2013 Google Inc.
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

import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.plugin.java.restricted.testing.SoyJavaSourceFunctionTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link com.google.template.soy.basicfunctions.StrIndexOfFunction}.
 *
 */
@RunWith(JUnit4.class)
public class StrIndexOfFunctionTest {

  @Test
  public void testComputeForJavaSource_containsString() {
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(new StrIndexOfFunction());
    assertThat(tester.callFunction("foobarfoo", "bar")).isEqualTo(3);
  }

  @Test
  public void testComputeForJavaSource_containsSanitizedContent() {
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(new StrIndexOfFunction());
    assertThat(tester.callFunction(StringData.forValue("foobarfoo"), StringData.forValue("bar")))
        .isEqualTo(3);
  }

  @Test
  public void testComputeForJavaSource_doesNotContainString() {
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(new StrIndexOfFunction());
    assertThat(tester.callFunction("foobarfoo", "baz")).isEqualTo(-1);
  }

  @Test
  public void testComputeForJavaSource_doesNotContainSanitizedContent() {
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(new StrIndexOfFunction());
    assertThat(tester.callFunction(StringData.forValue("foobarfoo"), StringData.forValue("baz")))
        .isEqualTo(-1);
  }

}
