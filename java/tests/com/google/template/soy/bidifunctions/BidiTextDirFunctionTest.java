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

package com.google.template.soy.bidifunctions;

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.data.Dir;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.plugin.java.restricted.testing.SoyJavaSourceFunctionTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for BidiTextDirFunction.
 *
 */
@RunWith(JUnit4.class)
public class BidiTextDirFunctionTest {

  @Test
  public void testComputeForJava() {
    BidiTextDirFunction bidiTextDirFunction = new BidiTextDirFunction();
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(bidiTextDirFunction);

    assertThat(tester.callFunction(StringData.EMPTY_STRING)).isEqualTo(0);
    assertThat(tester.callFunction(StringData.forValue("a"))).isEqualTo(1);
    assertThat(tester.callFunction(StringData.forValue("\u05E0"))).isEqualTo(-1);

    assertThat(tester.callFunction(StringData.forValue("a"))).isEqualTo(1);
    assertThat(
            tester.callFunction(
                UnsafeSanitizedContentOrdainer.ordainAsSafe("a", ContentKind.HTML, Dir.LTR)))
        .isEqualTo(1);
    assertThat(
            tester.callFunction(
                UnsafeSanitizedContentOrdainer.ordainAsSafe("a", ContentKind.HTML, Dir.RTL)))
        .isEqualTo(-1);
    assertThat(
            tester.callFunction(
                UnsafeSanitizedContentOrdainer.ordainAsSafe("a", ContentKind.HTML, Dir.NEUTRAL)))
        .isEqualTo(0);
  }
}
