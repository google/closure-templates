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
import com.google.inject.util.Providers;
import com.google.template.soy.data.Dir;
import com.google.template.soy.data.SanitizedContents;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import com.google.template.soy.shared.SharedRestrictedTestUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for BidiMarkAfterFunction.
 *
 */
@RunWith(JUnit4.class)
public class BidiMarkAfterFunctionTest {

  private static final BidiMarkAfterFunction BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_LTR =
      new BidiMarkAfterFunction(Providers.of(BidiGlobalDir.LTR));

  private static final BidiMarkAfterFunction BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_RTL =
      new BidiMarkAfterFunction(Providers.of(BidiGlobalDir.RTL));

  @Test
  public void testComputeForJava() {
    SoyValue text = StringData.EMPTY_STRING;
    assertThat(BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_LTR.computeForJava(ImmutableList.of(text)))
        .isEqualTo(StringData.EMPTY_STRING);
    text = StringData.forValue("a");
    assertThat(BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_LTR.computeForJava(ImmutableList.of(text)))
        .isEqualTo(StringData.EMPTY_STRING);
    text = StringData.forValue("\u05E0");
    assertThat(BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_LTR.computeForJava(ImmutableList.of(text)))
        .isEqualTo(StringData.forValue("\u200E"));

    text = SanitizedContents.unsanitizedText("a");
    assertThat(BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_LTR.computeForJava(ImmutableList.of(text)))
        .isEqualTo(StringData.EMPTY_STRING);
    text = SanitizedContents.unsanitizedText("a", Dir.LTR);
    assertThat(BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_LTR.computeForJava(ImmutableList.of(text)))
        .isEqualTo(StringData.EMPTY_STRING);
    text = SanitizedContents.unsanitizedText("a", Dir.NEUTRAL);
    assertThat(BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_LTR.computeForJava(ImmutableList.of(text)))
        .isEqualTo(StringData.EMPTY_STRING);
    text = SanitizedContents.unsanitizedText("a", Dir.RTL);
    assertThat(BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_LTR.computeForJava(ImmutableList.of(text)))
        .isEqualTo(StringData.forValue("\u200E"));
    text = SanitizedContents.unsanitizedText("\u05E0");
    assertThat(BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_LTR.computeForJava(ImmutableList.of(text)))
        .isEqualTo(StringData.forValue("\u200E"));
    text = SanitizedContents.unsanitizedText("\u05E0", Dir.RTL);
    assertThat(BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_LTR.computeForJava(ImmutableList.of(text)))
        .isEqualTo(StringData.forValue("\u200E"));
    text = SanitizedContents.unsanitizedText("\u05E0", Dir.NEUTRAL);
    assertThat(BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_LTR.computeForJava(ImmutableList.of(text)))
        .isEqualTo(StringData.forValue("\u200E"));
    text = SanitizedContents.unsanitizedText("\u05E0", Dir.LTR);
    assertThat(BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_LTR.computeForJava(ImmutableList.of(text)))
        .isEqualTo(StringData.forValue("\u200E"));

    text = StringData.EMPTY_STRING;
    assertThat(BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_RTL.computeForJava(ImmutableList.of(text)))
        .isEqualTo(StringData.EMPTY_STRING);
    text = StringData.forValue("\u05E0");
    assertThat(BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_RTL.computeForJava(ImmutableList.of(text)))
        .isEqualTo(StringData.EMPTY_STRING);
    text = StringData.forValue("a");
    assertThat(BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_RTL.computeForJava(ImmutableList.of(text)))
        .isEqualTo(StringData.forValue("\u200F"));

    text = SanitizedContents.unsanitizedText("\u05E0");
    assertThat(BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_RTL.computeForJava(ImmutableList.of(text)))
        .isEqualTo(StringData.EMPTY_STRING);
    text = SanitizedContents.unsanitizedText("\u05E0", Dir.RTL);
    assertThat(BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_RTL.computeForJava(ImmutableList.of(text)))
        .isEqualTo(StringData.EMPTY_STRING);
    text = SanitizedContents.unsanitizedText("\u05E0", Dir.NEUTRAL);
    assertThat(BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_RTL.computeForJava(ImmutableList.of(text)))
        .isEqualTo(StringData.EMPTY_STRING);
    text = SanitizedContents.unsanitizedText("\u05E0", Dir.LTR);
    assertThat(BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_RTL.computeForJava(ImmutableList.of(text)))
        .isEqualTo(StringData.forValue("\u200F"));
    text = SanitizedContents.unsanitizedText("a");
    assertThat(BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_RTL.computeForJava(ImmutableList.of(text)))
        .isEqualTo(StringData.forValue("\u200F"));
    text = SanitizedContents.unsanitizedText("a", Dir.LTR);
    assertThat(BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_RTL.computeForJava(ImmutableList.of(text)))
        .isEqualTo(StringData.forValue("\u200F"));
    text = SanitizedContents.unsanitizedText("a", Dir.NEUTRAL);
    assertThat(BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_RTL.computeForJava(ImmutableList.of(text)))
        .isEqualTo(StringData.forValue("\u200F"));
    text = SanitizedContents.unsanitizedText("a", Dir.RTL);
    assertThat(BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_RTL.computeForJava(ImmutableList.of(text)))
        .isEqualTo(StringData.forValue("\u200F"));
  }

  @Test
  public void testComputeForJsSrc() {
    BidiMarkAfterFunction codeSnippet =
        new BidiMarkAfterFunction(
            SharedRestrictedTestUtils.BIDI_GLOBAL_DIR_FOR_JS_ISRTL_CODE_SNIPPET_PROVIDER);

    JsExpr textExpr = new JsExpr("TEXT_JS_CODE", Integer.MAX_VALUE);
    assertThat(BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_LTR.computeForJsSrc(ImmutableList.of(textExpr)))
        .isEqualTo(new JsExpr("soy.$$bidiMarkAfter(1, TEXT_JS_CODE)", Integer.MAX_VALUE));
    assertThat(codeSnippet.computeForJsSrc(ImmutableList.of(textExpr)))
        .isEqualTo(new JsExpr("soy.$$bidiMarkAfter(IS_RTL?-1:1, TEXT_JS_CODE)", Integer.MAX_VALUE));

    JsExpr isHtmlExpr = new JsExpr("IS_HTML_JS_CODE", Integer.MAX_VALUE);
    assertThat(
            BIDI_MARK_AFTER_FUNCTION_FOR_STATIC_RTL.computeForJsSrc(
                ImmutableList.of(textExpr, isHtmlExpr)))
        .isEqualTo(
            new JsExpr(
                "soy.$$bidiMarkAfter(-1, TEXT_JS_CODE, IS_HTML_JS_CODE)", Integer.MAX_VALUE));
    assertThat(codeSnippet.computeForJsSrc(ImmutableList.of(textExpr, isHtmlExpr)))
        .isEqualTo(
            new JsExpr(
                "soy.$$bidiMarkAfter(IS_RTL?-1:1, TEXT_JS_CODE, IS_HTML_JS_CODE)",
                Integer.MAX_VALUE));
  }

  @Test
  public void testComputeForPySrc() {
    BidiMarkAfterFunction codeSnippet =
        new BidiMarkAfterFunction(
            SharedRestrictedTestUtils.BIDI_GLOBAL_DIR_FOR_PY_ISRTL_CODE_SNIPPET_PROVIDER);

    PyExpr textExpr = new PyStringExpr("'data'");
    assertThat(codeSnippet.computeForPySrc(ImmutableList.of(textExpr)).getText())
        .isEqualTo("bidi.mark_after(-1 if IS_RTL else 1, 'data')");

    PyExpr isHtmlExpr = new PyExpr("is_html", Integer.MAX_VALUE);
    assertThat(codeSnippet.computeForPySrc(ImmutableList.of(textExpr, isHtmlExpr)).getText())
        .isEqualTo("bidi.mark_after(-1 if IS_RTL else 1, 'data', is_html)");
  }
}
