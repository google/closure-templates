/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.basicfunctions;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SanitizedContents;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.plugin.java.restricted.testing.SoyJavaSourceFunctionTester;
import com.google.template.soy.pysrc.restricted.PyExpr;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HtmlToTextFunctionTest {
  private static final String HTML = "a<br>b";
  private static final String TEXT = "a\nb";

  @Test
  public void testComputeForJavaSource() {
    HtmlToTextFunction htmlToTextFunction = new HtmlToTextFunction();
    SoyJavaSourceFunctionTester tester = new SoyJavaSourceFunctionTester(htmlToTextFunction);
    SoyValue html = SanitizedContents.constantHtml(HTML);
    assertThat(tester.callFunction(html)).isEqualTo(TEXT);
  }

  @Test
  public void testComputeForJsSrc() {
    HtmlToTextFunction htmlToTextFunction = new HtmlToTextFunction();
    JsExpr expr = new JsExpr("JS_CODE", Integer.MAX_VALUE);
    assertThat(htmlToTextFunction.computeForJsSrc(ImmutableList.of(expr)))
        .isEqualTo(new JsExpr("soy.$$htmlToText(String(JS_CODE))", Integer.MAX_VALUE));
  }

  @Test
  public void testComputeForPySrc() {
    HtmlToTextFunction htmlToTextFunction = new HtmlToTextFunction();
    PyExpr expr = new PyExpr("PY_CODE", Integer.MAX_VALUE);
    assertThat(htmlToTextFunction.computeForPySrc(ImmutableList.of(expr)))
        .isEqualTo(new PyExpr("sanitize.html_to_text(str(PY_CODE))", Integer.MAX_VALUE));
  }
}
