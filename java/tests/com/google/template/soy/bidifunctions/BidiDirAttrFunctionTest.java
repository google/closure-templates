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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.shared.restricted.SharedRestrictedTestUtils;

import junit.framework.TestCase;


/**
 * Unit tests for BidiDirAttrFunction.
 *
 */
public class BidiDirAttrFunctionTest extends TestCase {


  private static final BidiDirAttrFunction BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_LTR =
      new BidiDirAttrFunction(SharedRestrictedTestUtils.BIDI_GLOBAL_DIR_FOR_STATIC_LTR_PROVIDER);

  private static final BidiDirAttrFunction BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_RTL =
      new BidiDirAttrFunction(SharedRestrictedTestUtils.BIDI_GLOBAL_DIR_FOR_STATIC_RTL_PROVIDER);

  private static final BidiDirAttrFunction BIDI_DIR_ATTR_FUNCTION_FOR_ISRTL_CODE_SNIPPET =
      new BidiDirAttrFunction(
          SharedRestrictedTestUtils.BIDI_GLOBAL_DIR_FOR_ISRTL_CODE_SNIPPET_PROVIDER);


  public void testComputeForTofu() {

    SoyData text = StringData.EMPTY_STRING;
    assertEquals(new SanitizedContent("", SanitizedContent.ContentKind.HTML_ATTRIBUTE),
                 BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_LTR.computeForTofu(ImmutableList.of(text)));
    assertEquals(new SanitizedContent("", SanitizedContent.ContentKind.HTML_ATTRIBUTE),
                 BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_RTL.computeForTofu(ImmutableList.of(text)));

    text = StringData.forValue("a");
    assertEquals(new SanitizedContent("", SanitizedContent.ContentKind.HTML_ATTRIBUTE),
                 BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_LTR.computeForTofu(ImmutableList.of(text)));
    assertEquals(new SanitizedContent("dir=ltr", SanitizedContent.ContentKind.HTML_ATTRIBUTE),
                 BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_RTL.computeForTofu(ImmutableList.of(text)));

    text = StringData.forValue("\u05E0");
    assertEquals(new SanitizedContent("dir=rtl", SanitizedContent.ContentKind.HTML_ATTRIBUTE),
                 BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_LTR.computeForTofu(ImmutableList.of(text)));
    assertEquals(new SanitizedContent("", SanitizedContent.ContentKind.HTML_ATTRIBUTE),
                 BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_RTL.computeForTofu(ImmutableList.of(text)));
  }


  public void testComputeForJsSrc() {

    JsExpr textExpr = new JsExpr("TEXT_JS_CODE", Integer.MAX_VALUE);
    assertEquals(new JsExpr("soy.$$bidiDirAttr(1, TEXT_JS_CODE)", Integer.MAX_VALUE),
                 BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_LTR.computeForJsSrc(ImmutableList.of(textExpr)));
    assertEquals(new JsExpr("soy.$$bidiDirAttr(IS_RTL?-1:1, TEXT_JS_CODE)", Integer.MAX_VALUE),
                 BIDI_DIR_ATTR_FUNCTION_FOR_ISRTL_CODE_SNIPPET.computeForJsSrc(
                     ImmutableList.of(textExpr)));

    JsExpr isHtmlExpr = new JsExpr("IS_HTML_JS_CODE", Integer.MAX_VALUE);
    assertEquals(new JsExpr("soy.$$bidiDirAttr(-1, TEXT_JS_CODE, IS_HTML_JS_CODE)",
                            Integer.MAX_VALUE),
                 BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_RTL.computeForJsSrc(
                     ImmutableList.of(textExpr, isHtmlExpr)));
    assertEquals(new JsExpr("soy.$$bidiDirAttr(IS_RTL?-1:1, TEXT_JS_CODE, IS_HTML_JS_CODE)",
                            Integer.MAX_VALUE),
                 BIDI_DIR_ATTR_FUNCTION_FOR_ISRTL_CODE_SNIPPET.computeForJsSrc(
                     ImmutableList.of(textExpr, isHtmlExpr)));
  }


  public void testComputeForJavaSrc() {
    JavaExpr textExpr = new JavaExpr("TEXT_JAVA_CODE", StringData.class, Integer.MAX_VALUE);
    assertEquals(
        new JavaExpr(
            "new com.google.template.soy.data.SanitizedContent(" +
                "com.google.template.soy.internal.i18n.SoyBidiUtils.getBidiFormatter(" +
                "1).dirAttr(TEXT_JAVA_CODE.toString(), false), " +
                "com.google.template.soy.data.SanitizedContent.ContentKind.HTML_ATTRIBUTE" +
            ")",
            StringData.class, Integer.MAX_VALUE),
        BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_LTR.computeForJavaSrc(ImmutableList.of(textExpr)));
    assertEquals(
        new JavaExpr(
            "new com.google.template.soy.data.SanitizedContent(" +
                "com.google.template.soy.internal.i18n.SoyBidiUtils.getBidiFormatter(" +
                "IS_RTL?-1:1).dirAttr(TEXT_JAVA_CODE.toString(), false), " +
                "com.google.template.soy.data.SanitizedContent.ContentKind.HTML_ATTRIBUTE" +
            ")",
            StringData.class, Integer.MAX_VALUE),
        BIDI_DIR_ATTR_FUNCTION_FOR_ISRTL_CODE_SNIPPET.computeForJavaSrc(
            ImmutableList.of(textExpr)));

    JavaExpr isHtmlExpr = new JavaExpr("IS_HTML_JAVA_CODE", BooleanData.class, Integer.MAX_VALUE);
    assertEquals(
        new JavaExpr(
            "new com.google.template.soy.data.SanitizedContent(" +
                "com.google.template.soy.internal.i18n.SoyBidiUtils.getBidiFormatter(" +
                "-1).dirAttr(TEXT_JAVA_CODE.toString(), IS_HTML_JAVA_CODE.toBoolean()), " +
                "com.google.template.soy.data.SanitizedContent.ContentKind.HTML_ATTRIBUTE" +
            ")",
            StringData.class, Integer.MAX_VALUE),
        BIDI_DIR_ATTR_FUNCTION_FOR_STATIC_RTL.computeForJavaSrc(
            ImmutableList.of(textExpr, isHtmlExpr)));
    assertEquals(
        new JavaExpr(
            "new com.google.template.soy.data.SanitizedContent(" +
                "com.google.template.soy.internal.i18n.SoyBidiUtils.getBidiFormatter(" +
                "IS_RTL?-1:1).dirAttr(TEXT_JAVA_CODE.toString(), IS_HTML_JAVA_CODE.toBoolean()), " +
                "com.google.template.soy.data.SanitizedContent.ContentKind.HTML_ATTRIBUTE" +
            ")",
            StringData.class, Integer.MAX_VALUE),
        BIDI_DIR_ATTR_FUNCTION_FOR_ISRTL_CODE_SNIPPET.computeForJavaSrc(
            ImmutableList.of(textExpr, isHtmlExpr)));
  }

}
