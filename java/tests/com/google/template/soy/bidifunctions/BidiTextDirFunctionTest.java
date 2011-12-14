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
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.jssrc.restricted.JsExpr;

import junit.framework.TestCase;


/**
 * Unit tests for BidiTextDirFunction.
 *
 */
public class BidiTextDirFunctionTest extends TestCase {


  public void testComputeForTofu() {

    BidiTextDirFunction bidiTextDirFunction = new BidiTextDirFunction();

    SoyData text = StringData.EMPTY_STRING;
    assertEquals(IntegerData.ZERO,
                 bidiTextDirFunction.computeForTofu(ImmutableList.of(text)));
    assertEquals(IntegerData.ZERO,
                 bidiTextDirFunction.computeForTofu(ImmutableList.of(text)));

    text = StringData.forValue("a");
    assertEquals(IntegerData.ONE,
                 bidiTextDirFunction.computeForTofu(ImmutableList.of(text)));
    assertEquals(IntegerData.ONE,
                 bidiTextDirFunction.computeForTofu(ImmutableList.of(text)));

    text = StringData.forValue("\u05E0");
    assertEquals(IntegerData.MINUS_ONE,
                 bidiTextDirFunction.computeForTofu(ImmutableList.of(text)));
    assertEquals(IntegerData.MINUS_ONE,
                 bidiTextDirFunction.computeForTofu(ImmutableList.of(text)));
  }


  public void testComputeForJsSrc() {

    BidiTextDirFunction bidiTextDirFunction = new BidiTextDirFunction();

    JsExpr textExpr = new JsExpr("TEXT_JS_CODE", Integer.MAX_VALUE);
    assertEquals(new JsExpr("soy.$$bidiTextDir(TEXT_JS_CODE)", Integer.MAX_VALUE),
                 bidiTextDirFunction.computeForJsSrc(ImmutableList.of(textExpr)));

    JsExpr isHtmlExpr = new JsExpr("IS_HTML_JS_CODE", Integer.MAX_VALUE);
    assertEquals(new JsExpr("soy.$$bidiTextDir(TEXT_JS_CODE, IS_HTML_JS_CODE)", Integer.MAX_VALUE),
                 bidiTextDirFunction.computeForJsSrc(
                     ImmutableList.of(textExpr, isHtmlExpr)));
  }


  public void testComputeForJavaSrc() {

    BidiTextDirFunction bidiTextDirFunction = new BidiTextDirFunction();

    JavaExpr textExpr = new JavaExpr("TEXT_JAVA_CODE", StringData.class, Integer.MAX_VALUE);
    assertEquals(
        new JavaExpr(
            "com.google.template.soy.data.restricted.IntegerData.forValue(" +
                "com.google.template.soy.internal.i18n.BidiUtils.estimateDirection(" +
                "TEXT_JAVA_CODE.toString(), false" +
                ").ord)",
            IntegerData.class, Integer.MAX_VALUE),
        bidiTextDirFunction.computeForJavaSrc(ImmutableList.of(textExpr)));

    JavaExpr isHtmlExpr = new JavaExpr("IS_HTML_JAVA_CODE", BooleanData.class, Integer.MAX_VALUE);
    assertEquals(
        new JavaExpr(
            "com.google.template.soy.data.restricted.IntegerData.forValue(" +
                "com.google.template.soy.internal.i18n.BidiUtils.estimateDirection(" +
                "TEXT_JAVA_CODE.toString(), IS_HTML_JAVA_CODE.toBoolean()" +
                ").ord)",
            IntegerData.class, Integer.MAX_VALUE),
        bidiTextDirFunction.computeForJavaSrc(ImmutableList.of(textExpr, isHtmlExpr)));
  }

}
