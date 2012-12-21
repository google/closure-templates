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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.shared.AbstractSoyPrintDirectiveTestCase;
import com.google.template.soy.shared.restricted.SharedRestrictedTestUtils;


/**
 * Unit tests for BidiSpanWrapDirective.
 *
 * @author Kai Huang
 */
public class BidiSpanWrapDirectiveTest extends AbstractSoyPrintDirectiveTestCase {


  private static final BidiSpanWrapDirective BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_LTR =
      new BidiSpanWrapDirective(SharedRestrictedTestUtils.BIDI_GLOBAL_DIR_FOR_STATIC_LTR_PROVIDER);

  private static final BidiSpanWrapDirective BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_RTL =
      new BidiSpanWrapDirective(SharedRestrictedTestUtils.BIDI_GLOBAL_DIR_FOR_STATIC_RTL_PROVIDER);

  private static final BidiSpanWrapDirective BIDI_SPAN_WRAP_DIRECTIVE_FOR_ISRTL_CODE_SNIPPET =
      new BidiSpanWrapDirective(
          SharedRestrictedTestUtils.BIDI_GLOBAL_DIR_FOR_ISRTL_CODE_SNIPPET_PROVIDER);


  public void testApplyForTofu() {

    assertTofuOutput("", "", BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_LTR);
    assertTofuOutput("blah", "blah", BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_LTR);
    assertTofuOutput("<span dir=\"rtl\">\u05E0</span>\u200E", "\u05E0",
        BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_LTR);

    assertTofuOutput("", "", BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_RTL);
    assertTofuOutput("<span dir=\"ltr\">blah</span>\u200F", "blah",
        BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_RTL);
    assertTofuOutput("\u05E0", "\u05E0", BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_RTL);
  }


  public void testApplyForJsSrc() {

    JsExpr dataRef = new JsExpr("opt_data.myKey", Integer.MAX_VALUE);
    assertEquals(
        "soy.$$bidiSpanWrap(1, opt_data.myKey)",
        BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_LTR.applyForJsSrc(
            dataRef, ImmutableList.<JsExpr>of()).getText());
    assertEquals(
        "soy.$$bidiSpanWrap(-1, opt_data.myKey)",
        BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_RTL.applyForJsSrc(
            dataRef, ImmutableList.<JsExpr>of()).getText());
    assertEquals(
        "soy.$$bidiSpanWrap(IS_RTL?-1:1, opt_data.myKey)",
        BIDI_SPAN_WRAP_DIRECTIVE_FOR_ISRTL_CODE_SNIPPET.applyForJsSrc(
            dataRef, ImmutableList.<JsExpr>of()).getText());
  }


  public void testApplyForJavaSrc() {

    JavaExpr dataRef = new JavaExpr("stringValue", StringData.class, Integer.MAX_VALUE);
    assertEquals(
        "com.google.template.soy.data.restricted.StringData.forValue(" +
            "com.google.template.soy.internal.i18n.SoyBidiUtils.getBidiFormatter(" +
            "1).spanWrap(stringValue.toString(), true))",
        BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_LTR.applyForJavaSrc(
            dataRef, ImmutableList.<JavaExpr>of()).getText());
    assertEquals(
        "com.google.template.soy.data.restricted.StringData.forValue(" +
            "com.google.template.soy.internal.i18n.SoyBidiUtils.getBidiFormatter(" +
            "-1).spanWrap(stringValue.toString(), true))",
        BIDI_SPAN_WRAP_DIRECTIVE_FOR_STATIC_RTL.applyForJavaSrc(
            dataRef, ImmutableList.<JavaExpr>of()).getText());
    assertEquals(
        "com.google.template.soy.data.restricted.StringData.forValue(" +
            "com.google.template.soy.internal.i18n.SoyBidiUtils.getBidiFormatter(" +
            "IS_RTL?-1:1).spanWrap(stringValue.toString(), true))",
        BIDI_SPAN_WRAP_DIRECTIVE_FOR_ISRTL_CODE_SNIPPET.applyForJavaSrc(
            dataRef, ImmutableList.<JavaExpr>of()).getText());
  }

}
