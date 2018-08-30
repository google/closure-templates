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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.Dir;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SanitizedContents;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.plugin.java.restricted.testing.SoyJavaSourceFunctionTester;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import com.google.template.soy.shared.SharedRestrictedTestUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for BidiDirAttrFunction.
 *
 */
@RunWith(JUnit4.class)
public class BidiDirAttrFunctionTest {

  @Test
  public void testComputeForJavaSource() {
    SanitizedContent empty = SanitizedContents.emptyString(ContentKind.ATTRIBUTES);
    SanitizedContent ltr = SanitizedContents.constantAttributes("dir=\"ltr\"");
    SanitizedContent rtl = SanitizedContents.constantAttributes("dir=\"rtl\"");

    // the java source version doesn't use the provider
    BidiDirAttrFunction fn =
        new BidiDirAttrFunction(
            () -> {
              throw new UnsupportedOperationException();
            });
    SoyJavaSourceFunctionTester tester =
        new SoyJavaSourceFunctionTester.Builder(fn).withBidiGlobalDir(BidiGlobalDir.LTR).build();
    assertThat(tester.callFunction(StringData.EMPTY_STRING)).isEqualTo(empty);
    assertThat(tester.callFunction(StringData.forValue("a"))).isEqualTo(empty);
    assertThat(tester.callFunction(StringData.forValue("\u05E0"))).isEqualTo(rtl);
    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("\u05E0"))).isEqualTo(rtl);
    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("\u05E0", Dir.RTL)))
        .isEqualTo(rtl);
    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("\u05E0", Dir.LTR)))
        .isEqualTo(empty);
    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("\u05E0", Dir.NEUTRAL)))
        .isEqualTo(empty);

    tester =
        new SoyJavaSourceFunctionTester.Builder(fn).withBidiGlobalDir(BidiGlobalDir.RTL).build();
    assertThat(tester.callFunction(StringData.EMPTY_STRING)).isEqualTo(empty);
    assertThat(tester.callFunction(StringData.forValue("\u05E0"))).isEqualTo(empty);
    assertThat(tester.callFunction(StringData.forValue("a"))).isEqualTo(ltr);
    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("a"))).isEqualTo(ltr);
    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("a", Dir.LTR))).isEqualTo(ltr);
    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("a", Dir.RTL)))
        .isEqualTo(empty);
    assertThat(tester.callFunction(SanitizedContents.unsanitizedText("a", Dir.NEUTRAL)))
        .isEqualTo(empty);
  }

  @Test
  public void testComputeForPySrc() {
    BidiDirAttrFunction codeSnippet =
        new BidiDirAttrFunction(
            SharedRestrictedTestUtils.BIDI_GLOBAL_DIR_FOR_PY_ISRTL_CODE_SNIPPET_SUPPLIER);

    PyExpr textExpr = new PyStringExpr("'data'", Integer.MAX_VALUE);
    assertThat(codeSnippet.computeForPySrc(ImmutableList.of(textExpr)).getText())
        .isEqualTo("bidi.dir_attr(-1 if IS_RTL else 1, 'data')");

    PyExpr isHtmlExpr = new PyExpr("is_html", Integer.MAX_VALUE);
    assertThat(codeSnippet.computeForPySrc(ImmutableList.of(textExpr, isHtmlExpr)).getText())
        .isEqualTo("bidi.dir_attr(-1 if IS_RTL else 1, 'data', is_html)");
  }
}
