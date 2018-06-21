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
import com.google.template.soy.data.SanitizedContents;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
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

    SoyValue text = StringData.EMPTY_STRING;
    assertThat(bidiTextDirFunction.computeForJava(ImmutableList.of(text)))
        .isEqualTo(IntegerData.ZERO);
    text = StringData.forValue("a");
    assertThat(bidiTextDirFunction.computeForJava(ImmutableList.of(text)))
        .isEqualTo(IntegerData.ONE);
    text = StringData.forValue("\u05E0");
    assertThat(bidiTextDirFunction.computeForJava(ImmutableList.of(text)))
        .isEqualTo(IntegerData.MINUS_ONE);

    text = SanitizedContents.unsanitizedText("a");
    assertThat(bidiTextDirFunction.computeForJava(ImmutableList.of(text)))
        .isEqualTo(IntegerData.ONE);
    text = SanitizedContents.unsanitizedText("a", Dir.LTR);
    assertThat(bidiTextDirFunction.computeForJava(ImmutableList.of(text)))
        .isEqualTo(IntegerData.ONE);
    text = SanitizedContents.unsanitizedText("a", Dir.RTL);
    assertThat(bidiTextDirFunction.computeForJava(ImmutableList.of(text)))
        .isEqualTo(IntegerData.MINUS_ONE);
    text = SanitizedContents.unsanitizedText("a", Dir.NEUTRAL);
    assertThat(bidiTextDirFunction.computeForJava(ImmutableList.of(text)))
        .isEqualTo(IntegerData.ZERO);
  }

  @Test
  public void testComputeForJsSrc() {
    BidiTextDirFunction bidiTextDirFunction = new BidiTextDirFunction();

    JsExpr textExpr = new JsExpr("TEXT_JS_CODE", Integer.MAX_VALUE);
    assertThat(bidiTextDirFunction.computeForJsSrc(ImmutableList.of(textExpr)))
        .isEqualTo(new JsExpr("soy.$$bidiTextDir(TEXT_JS_CODE)", Integer.MAX_VALUE));

    JsExpr isHtmlExpr = new JsExpr("IS_HTML_JS_CODE", Integer.MAX_VALUE);
    assertThat(bidiTextDirFunction.computeForJsSrc(ImmutableList.of(textExpr, isHtmlExpr)))
        .isEqualTo(
            new JsExpr("soy.$$bidiTextDir(TEXT_JS_CODE, IS_HTML_JS_CODE)", Integer.MAX_VALUE));
  }

  @Test
  public void testComputeForPySrc() {
    BidiTextDirFunction bidiTextDirFunction = new BidiTextDirFunction();

    PyExpr data = new PyStringExpr("'data'");
    assertThat(bidiTextDirFunction.computeForPySrc(ImmutableList.of(data)).getText())
        .isEqualTo("bidi.text_dir('data')");

    PyExpr isHtml = new PyExpr("is_html", Integer.MAX_VALUE);
    assertThat(bidiTextDirFunction.computeForPySrc(ImmutableList.of(data, isHtml)).getText())
        .isEqualTo("bidi.text_dir('data', is_html)");
  }
}
