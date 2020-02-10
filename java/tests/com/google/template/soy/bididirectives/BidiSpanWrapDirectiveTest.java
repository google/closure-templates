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

package com.google.template.soy.bididirectives;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.Dir;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import com.google.template.soy.testing.AbstractSoyPrintDirectiveTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for BidiSpanWrapDirective.
 *
 */
@RunWith(JUnit4.class)
public class BidiSpanWrapDirectiveTest extends AbstractSoyPrintDirectiveTestCase {

  private static final BidiSpanWrapDirective BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_LTR =
      new BidiSpanWrapDirective(Suppliers.ofInstance(BidiGlobalDir.LTR));

  private static final BidiSpanWrapDirective BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_RTL =
      new BidiSpanWrapDirective(Suppliers.ofInstance(BidiGlobalDir.RTL));

  @Test
  public void testApplyForTofu() {
    assertTofuOutput("", "", BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_LTR);
    assertTofuOutput("blah", "blah", BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_LTR);
    assertTofuOutput(
        "<span dir=\"rtl\">\u05E0</span>\u200E", "\u05E0", BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_LTR);

    assertTofuOutput(
        "<span dir=\"rtl\">\u05E0</span>\u200E",
        StringData.forValue("\u05E0"),
        BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_LTR);
    assertTofuOutput(
        "<span dir=\"rtl\">\u05E0</span>\u200E",
        UnsafeSanitizedContentOrdainer.ordainAsSafe("\u05E0", ContentKind.HTML, Dir.RTL),
        BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_LTR);
    assertTofuOutput(
        "\u05E0\u200E",
        UnsafeSanitizedContentOrdainer.ordainAsSafe("\u05E0", ContentKind.HTML, Dir.LTR),
        BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_LTR);
    assertTofuOutput(
        "\u05E0\u200E",
        UnsafeSanitizedContentOrdainer.ordainAsSafe("\u05E0", ContentKind.HTML, Dir.NEUTRAL),
        BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_LTR);
    assertTofuOutput(
        "blah",
        UnsafeSanitizedContentOrdainer.ordainAsSafe("blah", ContentKind.HTML, Dir.NEUTRAL),
        BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_LTR);

    assertTofuOutput("", "", BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_RTL);
    assertTofuOutput("\u05E0", "\u05E0", BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_RTL);
    assertTofuOutput(
        "<span dir=\"ltr\">blah</span>\u200F", "blah", BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_RTL);

    assertTofuOutput(
        "<span dir=\"ltr\">blah</span>\u200F",
        StringData.forValue("blah"),
        BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_RTL);
    assertTofuOutput(
        "<span dir=\"ltr\">blah</span>\u200F",
        UnsafeSanitizedContentOrdainer.ordainAsSafe("blah", ContentKind.HTML, Dir.LTR),
        BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_RTL);
    assertTofuOutput(
        "blah\u200F",
        UnsafeSanitizedContentOrdainer.ordainAsSafe("blah", ContentKind.HTML, Dir.RTL),
        BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_RTL);
    assertTofuOutput(
        "blah\u200F",
        UnsafeSanitizedContentOrdainer.ordainAsSafe("blah", ContentKind.HTML, Dir.NEUTRAL),
        BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_RTL);
    assertTofuOutput(
        "\u05E0",
        UnsafeSanitizedContentOrdainer.ordainAsSafe("\u05E0", ContentKind.HTML, Dir.NEUTRAL),
        BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_RTL);
  }

  @Test
  public void testApplyForJsSrc() {
    JsExpr dataRef = new JsExpr("opt_data.myKey", Integer.MAX_VALUE);
    assertThat(
            BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_LTR
                .applyForJsSrc(dataRef, ImmutableList.of())
                .getText())
        .isEqualTo("soy.$$bidiSpanWrap(1, opt_data.myKey)");
    assertThat(
            BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_RTL
                .applyForJsSrc(dataRef, ImmutableList.of())
                .getText())
        .isEqualTo("soy.$$bidiSpanWrap(-1, opt_data.myKey)");

    BidiSpanWrapDirective codeSnippet =
        new BidiSpanWrapDirective(BidiTestUtils.BIDI_GLOBAL_DIR_FOR_JS_ISRTL_CODE_SNIPPET_SUPPLIER);
    assertThat(codeSnippet.applyForJsSrc(dataRef, ImmutableList.of()).getText())
        .isEqualTo("soy.$$bidiSpanWrap(IS_RTL?-1:1, opt_data.myKey)");
  }

  @Test
  public void testApplyForPySrc() {
    BidiSpanWrapDirective codeSnippet =
        new BidiSpanWrapDirective(BidiTestUtils.BIDI_GLOBAL_DIR_FOR_PY_ISRTL_CODE_SNIPPET_SUPPLIER);

    PyExpr data = new PyStringExpr("'data'");
    assertThat(codeSnippet.applyForPySrc(data, ImmutableList.of()).getText())
        .isEqualTo("bidi.span_wrap(-1 if IS_RTL else 1, 'data')");
  }
}
