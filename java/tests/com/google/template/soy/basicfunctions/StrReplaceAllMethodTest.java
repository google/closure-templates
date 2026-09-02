/*
 * Copyright 2020 Google Inc.
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
import static org.junit.Assert.assertThrows;

import com.google.template.soy.data.restricted.RegexpData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.plugin.java.restricted.testing.SoyJavaSourceFunctionTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link StrReplaceAllMethod}. */
@RunWith(JUnit4.class)
public class StrReplaceAllMethodTest {

  @Test
  public void testStringReplacement() {
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(new StrReplaceAllMethod());
    assertThat(tester.callMethod("hello world hello", "hello", "goodbye"))
        .isEqualTo("goodbye world goodbye");
    assertThat(tester.callMethod(StringData.forValue("hello world"), "world", "earth"))
        .isEqualTo("hello earth");
  }

  @Test
  public void testRegexpReplacement_global() {
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(new StrReplaceAllMethod());
    RegexpData regex = RegexpData.of("hello", "g");
    assertThat(tester.callMethod("hello world hello", regex, "goodbye"))
        .isEqualTo("goodbye world goodbye");
  }

  @Test
  public void testRegexpReplacement_nonGlobal_throws() {
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(new StrReplaceAllMethod());
    RegexpData regex = RegexpData.of("hello", "");
    Throwable t =
        assertThrows(
            Throwable.class, () -> tester.callMethod("hello world hello", regex, "goodbye"));
    Throwable cause = t;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    assertThat(cause)
        .hasMessageThat()
        .contains("String.prototype.replaceAll called with a non-global RegExp argument");
  }

  @Test
  public void testStringReplacement_dollarPatterns() {
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(new StrReplaceAllMethod());
    assertThat(tester.callMethod("hello world hello", "hello", "[$&]"))
        .isEqualTo("[hello] world [hello]");
    assertThat(tester.callMethod("hello world hello", "world", "$$5")).isEqualTo("hello $5 hello");
    assertThat(tester.callMethod("hello world hello", "world", "[$`|$&|$']"))
        .isEqualTo("hello [hello |world| hello] hello");
    assertThat(tester.callMethod("hello world hello", "world", "$1")).isEqualTo("hello $1 hello");
    assertThat(tester.callMethod("abc", "", "-")).isEqualTo("-a-b-c-");
  }

  @Test
  public void testRegexpReplacement_dollarPatterns() {
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(new StrReplaceAllMethod());
    RegexpData regex = RegexpData.of("world", "g");
    assertThat(tester.callMethod("hello world", regex, "[$&]")).isEqualTo("hello [world]");
    assertThat(tester.callMethod("hello world", regex, "$$5")).isEqualTo("hello $5");
    assertThat(tester.callMethod("hello world", regex, "[$`|$&|$']"))
        .isEqualTo("hello [hello |world|]");
  }

  @Test
  public void testRegexpReplacement_captureGroups() {
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(new StrReplaceAllMethod());
    RegexpData regex = RegexpData.of("(\\w+)\\s+(\\w+)", "g");
    assertThat(tester.callMethod("John Smith", regex, "$2, $1")).isEqualTo("Smith, John");
    assertThat(tester.callMethod("John Smith", regex, "$3")).isEqualTo("$3");
    assertThat(tester.callMethod("John Smith", regex, "$0")).isEqualTo("$0");
  }
}
