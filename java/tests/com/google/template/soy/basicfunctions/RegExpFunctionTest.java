/*
 * Copyright 2026 Google Inc.
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

import com.google.template.soy.data.restricted.RegexpData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RegExpFunction}. */
@RunWith(JUnit4.class)
public class RegExpFunctionTest {

  @Test
  public void testNoArgs() {
    assertThat(BasicFunctionsRuntime.regexpEcma()).isEqualTo(RegexpData.of("(?:)", ""));
  }

  @Test
  public void testStringPattern() {
    assertThat(BasicFunctionsRuntime.regexpEcma(StringData.forValue("")))
        .isEqualTo(RegexpData.of("(?:)", ""));
    assertThat(BasicFunctionsRuntime.regexpEcma(StringData.forValue(""), StringData.forValue("g")))
        .isEqualTo(RegexpData.of("(?:)", "g"));
    assertThat(BasicFunctionsRuntime.regexpEcma(StringData.forValue("abc")))
        .isEqualTo(RegexpData.of("abc", ""));
    assertThat(
            BasicFunctionsRuntime.regexpEcma(StringData.forValue("abc"), StringData.forValue("i")))
        .isEqualTo(RegexpData.of("abc", "i"));
  }

  @Test
  public void testRegexpPattern() {
    RegexpData original = RegexpData.of("abc", "g");
    assertThat(BasicFunctionsRuntime.regexpEcma(original)).isEqualTo(original);
    assertThat(BasicFunctionsRuntime.regexpEcma(original, StringData.forValue("i")))
        .isEqualTo(RegexpData.of("abc", "i"));
    assertThat(BasicFunctionsRuntime.regexpEcma(original, StringData.forValue("")))
        .isEqualTo(RegexpData.of("abc", ""));
    assertThat(BasicFunctionsRuntime.regexpEcma(original, UndefinedData.INSTANCE))
        .isEqualTo(original);
  }

  @Test
  public void testUndefined() {
    assertThat(BasicFunctionsRuntime.regexpEcma(UndefinedData.INSTANCE))
        .isEqualTo(RegexpData.of("(?:)", ""));
    assertThat(BasicFunctionsRuntime.regexpEcma(UndefinedData.INSTANCE, StringData.forValue("i")))
        .isEqualTo(RegexpData.of("(?:)", "i"));
    assertThat(BasicFunctionsRuntime.regexpEcma(UndefinedData.INSTANCE, UndefinedData.INSTANCE))
        .isEqualTo(RegexpData.of("(?:)", ""));
  }
}
