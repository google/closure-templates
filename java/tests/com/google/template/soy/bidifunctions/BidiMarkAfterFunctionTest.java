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
import com.google.template.soy.data.SanitizedContents;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.plugin.java.restricted.testing.SoyJavaSourceFunctionTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for BidiMarkAfterFunction.
 *
 */
@RunWith(JUnit4.class)
public class BidiMarkAfterFunctionTest {

  @Test
  public void testComputeForJava() {
    // the java source version doesn't use the provider
    BidiMarkAfterFunction fn = new BidiMarkAfterFunction();

    SoyJavaSourceFunctionTester tester =
        new SoyJavaSourceFunctionTester.Builder(fn).withBidiGlobalDir(BidiGlobalDir.LTR).build();

    assertThat(tester.callFunction(StringData.EMPTY_STRING)).isEqualTo("");
    assertThat(tester.callFunction(StringData.forValue("a"))).isEqualTo("");
    assertThat(tester.callFunction(StringData.forValue("\u05E0"))).isEqualTo("\u200E");

    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("a"))).isEqualTo("");
    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("a", Dir.LTR))).isEqualTo("");
    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("a", Dir.NEUTRAL)))
        .isEqualTo("");
    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("a", Dir.RTL)))
        .isEqualTo("\u200E");
    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("\u05E0")))
        .isEqualTo("\u200E");
    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("\u05E0", Dir.RTL)))
        .isEqualTo("\u200E");
    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("\u05E0", Dir.NEUTRAL)))
        .isEqualTo("\u200E");
    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("\u05E0", Dir.LTR)))
        .isEqualTo("\u200E");

    tester =
        new SoyJavaSourceFunctionTester.Builder(fn).withBidiGlobalDir(BidiGlobalDir.RTL).build();
    assertThat(tester.callFunction(StringData.EMPTY_STRING)).isEqualTo("");
    assertThat(tester.callFunction(StringData.forValue("\u05E0"))).isEqualTo("");
    assertThat(tester.callFunction(StringData.forValue("a"))).isEqualTo("\u200F");

    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("\u05E0"))).isEqualTo("");
    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("\u05E0", Dir.RTL)))
        .isEqualTo("");
    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("\u05E0", Dir.NEUTRAL)))
        .isEqualTo("");
    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("\u05E0", Dir.LTR)))
        .isEqualTo("\u200F");
    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("a"))).isEqualTo("\u200F");
    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("a", Dir.LTR)))
        .isEqualTo("\u200F");
    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("a", Dir.NEUTRAL)))
        .isEqualTo("\u200F");
    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("a", Dir.RTL)))
        .isEqualTo("\u200F");
  }

}
