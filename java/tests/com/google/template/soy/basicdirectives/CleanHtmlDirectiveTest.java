/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.basicdirectives;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.shared.AbstractSoyPrintDirectiveTestCase;


/**
 * Unit tests for {@link CleanHtmlDirective}.
 */
public class CleanHtmlDirectiveTest extends AbstractSoyPrintDirectiveTestCase {


  private SanitizedContent sanitizedHtml(String s) {
    return UnsafeSanitizedContentOrdainer.ordainAsSafe(s, ContentKind.HTML);
  }

  public void testApplyForTofu() {
    CleanHtmlDirective cleanHtml = new CleanHtmlDirective();
    assertTofuOutput(sanitizedHtml("boo hoo"), "boo hoo", cleanHtml);
    assertTofuOutput(sanitizedHtml("3 &lt; 5"), "3 < 5", cleanHtml);
    assertTofuOutput(sanitizedHtml(""), "<script type=\"text/javascript\">", cleanHtml);
    assertTofuOutput(sanitizedHtml(""), "<script><!--\nbeEvil();//--></script>", cleanHtml);
    // Known safe content is preserved.
    assertTofuOutput(sanitizedHtml("<script>beAwesome()</script>"),
        sanitizedHtml("<script>beAwesome()</script>"), cleanHtml);
    // Entities are preserved
    assertTofuOutput(sanitizedHtml("&nbsp;&nbsp;"), "&nbsp;&nbsp;", cleanHtml);
    // Safe tags are preserved. Others are not.
    assertTofuOutput(
        sanitizedHtml("Hello, <b>World!</b>"), "Hello, <b>World!<object></b>", cleanHtml);
  }

  public void testApplyForJsSrc() {
    CleanHtmlDirective cleanHtml = new CleanHtmlDirective();
    JsExpr dataRef = new JsExpr("opt_data.myKey", Integer.MAX_VALUE);
    assertEquals(
        "soy.$$cleanHtml(opt_data.myKey)",
        cleanHtml.applyForJsSrc(dataRef, ImmutableList.<JsExpr>of()).getText());
  }
}
