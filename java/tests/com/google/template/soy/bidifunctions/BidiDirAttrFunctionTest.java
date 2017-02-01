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
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContents;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
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
 * Unit tests for BidiDirAttrFunction.
 *
 */
@RunWith(JUnit4.class)
public class BidiDirAttrFunctionTest {

  private static final BidiDirAttrFunction BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_LTR =
      new BidiDirAttrFunction(Providers.of(BidiGlobalDir.LTR));

  private static final BidiDirAttrFunction BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_RTL =
      new BidiDirAttrFunction(Providers.of(BidiGlobalDir.RTL));

  @Test
  public void testComputeForJava() {
    SoyValue text = StringData.EMPTY_STRING;

    assertThat(BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_LTR.computeForJava(ImmutableList.of(text)))
        .isEqualTo(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "", SanitizedContent.ContentKind.ATTRIBUTES));
    text = StringData.forValue("a");
    assertThat(BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_LTR.computeForJava(ImmutableList.of(text)))
        .isEqualTo(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "", SanitizedContent.ContentKind.ATTRIBUTES));
    text = StringData.forValue("\u05E0");
    assertThat(BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_LTR.computeForJava(ImmutableList.of(text)))
        .isEqualTo(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "dir=\"rtl\"", SanitizedContent.ContentKind.ATTRIBUTES));

    text = SanitizedContents.unsanitizedText("\u05E0");
    assertThat(BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_LTR.computeForJava(ImmutableList.of(text)))
        .isEqualTo(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "dir=\"rtl\"", SanitizedContent.ContentKind.ATTRIBUTES));
    text = SanitizedContents.unsanitizedText("\u05E0", Dir.RTL);
    assertThat(BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_LTR.computeForJava(ImmutableList.of(text)))
        .isEqualTo(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "dir=\"rtl\"", SanitizedContent.ContentKind.ATTRIBUTES));
    text = SanitizedContents.unsanitizedText("\u05E0", Dir.LTR);
    assertThat(BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_LTR.computeForJava(ImmutableList.of(text)))
        .isEqualTo(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "", SanitizedContent.ContentKind.ATTRIBUTES));
    text = SanitizedContents.unsanitizedText("\u05E0", Dir.NEUTRAL);
    assertThat(BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_LTR.computeForJava(ImmutableList.of(text)))
        .isEqualTo(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "", SanitizedContent.ContentKind.ATTRIBUTES));

    text = StringData.EMPTY_STRING;
    assertThat(BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_RTL.computeForJava(ImmutableList.of(text)))
        .isEqualTo(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "", SanitizedContent.ContentKind.ATTRIBUTES));
    text = StringData.forValue("\u05E0");
    assertThat(BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_RTL.computeForJava(ImmutableList.of(text)))
        .isEqualTo(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "", SanitizedContent.ContentKind.ATTRIBUTES));
    text = StringData.forValue("a");
    assertThat(BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_RTL.computeForJava(ImmutableList.of(text)))
        .isEqualTo(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "dir=\"ltr\"", SanitizedContent.ContentKind.ATTRIBUTES));

    text = SanitizedContents.unsanitizedText("a");
    assertThat(BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_RTL.computeForJava(ImmutableList.of(text)))
        .isEqualTo(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "dir=\"ltr\"", SanitizedContent.ContentKind.ATTRIBUTES));
    text = SanitizedContents.unsanitizedText("a", Dir.LTR);
    assertThat(BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_RTL.computeForJava(ImmutableList.of(text)))
        .isEqualTo(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "dir=\"ltr\"", SanitizedContent.ContentKind.ATTRIBUTES));
    text = SanitizedContents.unsanitizedText("a", Dir.RTL);
    assertThat(BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_RTL.computeForJava(ImmutableList.of(text)))
        .isEqualTo(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "", SanitizedContent.ContentKind.ATTRIBUTES));
    text = SanitizedContents.unsanitizedText("a", Dir.NEUTRAL);
    assertThat(BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_RTL.computeForJava(ImmutableList.of(text)))
        .isEqualTo(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "", SanitizedContent.ContentKind.ATTRIBUTES));
  }

  @Test
  public void testComputeForJsSrc() {
    BidiDirAttrFunction codeSnippet =
        new BidiDirAttrFunction(
            SharedRestrictedTestUtils.BIDI_GLOBAL_DIR_FOR_JS_ISRTL_CODE_SNIPPET_PROVIDER);

    JsExpr textExpr = new JsExpr("TEXT_JS_CODE", Integer.MAX_VALUE);
    assertThat(BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_LTR.computeForJsSrc(ImmutableList.of(textExpr)))
        .isEqualTo(new JsExpr("soy.$$bidiDirAttr(1, TEXT_JS_CODE)", Integer.MAX_VALUE));
    assertThat(codeSnippet.computeForJsSrc(ImmutableList.of(textExpr)))
        .isEqualTo(new JsExpr("soy.$$bidiDirAttr(IS_RTL?-1:1, TEXT_JS_CODE)", Integer.MAX_VALUE));

    JsExpr isHtmlExpr = new JsExpr("IS_HTML_JS_CODE", Integer.MAX_VALUE);
    assertThat(
            BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_RTL.computeForJsSrc(
                ImmutableList.of(textExpr, isHtmlExpr)))
        .isEqualTo(
            new JsExpr("soy.$$bidiDirAttr(-1, TEXT_JS_CODE, IS_HTML_JS_CODE)", Integer.MAX_VALUE));
    assertThat(codeSnippet.computeForJsSrc(ImmutableList.of(textExpr, isHtmlExpr)))
        .isEqualTo(
            new JsExpr(
                "soy.$$bidiDirAttr(IS_RTL?-1:1, TEXT_JS_CODE, IS_HTML_JS_CODE)",
                Integer.MAX_VALUE));
  }

  @Test
  public void testComputeForPySrc() {
    BidiDirAttrFunction codeSnippet =
        new BidiDirAttrFunction(
            SharedRestrictedTestUtils.BIDI_GLOBAL_DIR_FOR_PY_ISRTL_CODE_SNIPPET_PROVIDER);

    PyExpr textExpr = new PyStringExpr("'data'", Integer.MAX_VALUE);
    assertThat(codeSnippet.computeForPySrc(ImmutableList.of(textExpr)).getText())
        .isEqualTo("bidi.dir_attr(-1 if IS_RTL else 1, 'data')");

    PyExpr isHtmlExpr = new PyExpr("is_html", Integer.MAX_VALUE);
    assertThat(codeSnippet.computeForPySrc(ImmutableList.of(textExpr, isHtmlExpr)).getText())
        .isEqualTo("bidi.dir_attr(-1 if IS_RTL else 1, 'data', is_html)");
  }
}
