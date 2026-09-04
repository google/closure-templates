/*
 * Copyright 2023 Google Inc.
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
public final class EncodeUriComponentFunctionTest {

  @Test
  public void testComputeForJavaSource() {
    EncodeUriComponentFunction function = new EncodeUriComponentFunction();
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(function);

    // Safe characters that shouldn't be percent-encoded:
    assertThat(tester.callFunction("A-Za-z0-9 -_.!~*'()")).isEqualTo("A-Za-z0-9%20-_.!~*'()");

    // Characters that should be escaped
    assertThat(tester.callFunction(" +@#&?/<>=:,$;%^`\"{}\\[]"))
        .isEqualTo("%20%2B%40%23%26%3F%2F%3C%3E%3D%3A%2C%24%3B%25%5E%60%22%7B%7D%5C%5B%5D");

    // Unicode characters (Emojis, non-ascii characters)
    assertThat(tester.callFunction("こんにちは🌟"))
        .isEqualTo("%E3%81%93%E3%82%93%E3%81%AB%E3%81%A1%E3%81%AF%F0%9F%8C%9F");
  }
}
