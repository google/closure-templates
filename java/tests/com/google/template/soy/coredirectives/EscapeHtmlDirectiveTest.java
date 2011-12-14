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

package com.google.template.soy.coredirectives;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.shared.AbstractSoyPrintDirectiveTestCase;


/**
 * Unit tests for EscapeHtmlDirective.
 *
 */
public class EscapeHtmlDirectiveTest extends AbstractSoyPrintDirectiveTestCase {


  public void testApplyForTofu() {

    EscapeHtmlDirective escapeHtmlDirective = new EscapeHtmlDirective();
    assertTofuOutput("", "", escapeHtmlDirective);
    assertTofuOutput("a&amp;b &gt; c", "a&b > c", escapeHtmlDirective);
    assertTofuOutput(
        "&lt;script&gt;alert(&#39;boo&#39;);&lt;/script&gt;",
        "<script>alert('boo');</script>", escapeHtmlDirective);
    assertTofuOutput(
        "<foo>",
        // Sanitized HTML is not escaped.
        new SanitizedContent("<foo>", SanitizedContent.ContentKind.HTML),
        escapeHtmlDirective);
    assertTofuOutput(
        "&lt;foo&gt;",
        // But JS_STR_CHARS are.
        new SanitizedContent("<foo>", SanitizedContent.ContentKind.JS_STR_CHARS),
        escapeHtmlDirective);
  }


  public void testApplyForJsSrc() {

    EscapeHtmlDirective escapeHtmlDirective = new EscapeHtmlDirective();
    JsExpr dataRef = new JsExpr("opt_data.myKey", Integer.MAX_VALUE);
    assertEquals("soy.$$escapeHtml(opt_data.myKey)",
                 escapeHtmlDirective.applyForJsSrc(dataRef, ImmutableList.<JsExpr>of()).getText());
  }

}
