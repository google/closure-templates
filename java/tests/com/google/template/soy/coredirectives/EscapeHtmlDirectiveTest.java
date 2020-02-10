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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.testing.AbstractSoyPrintDirectiveTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for EscapeHtmlDirective.
 *
 */
@RunWith(JUnit4.class)
public class EscapeHtmlDirectiveTest extends AbstractSoyPrintDirectiveTestCase {

  @Test
  public void testApplyForTofu() {
    EscapeHtmlDirective escapeHtmlDirective = new EscapeHtmlDirective();

    assertTofuOutput("", "", escapeHtmlDirective);
    assertTofuOutput("a&amp;b &gt; c", "a&b > c", escapeHtmlDirective);
    assertTofuOutput(
        "&lt;script&gt;alert(&#39;boo&#39;);&lt;/script&gt;",
        "<script>alert('boo');</script>",
        escapeHtmlDirective);
    SanitizedContent fooTagHtml =
        UnsafeSanitizedContentOrdainer.ordainAsSafe("<foo>", SanitizedContent.ContentKind.HTML);
    assertTofuOutput(
        fooTagHtml,
        // Sanitized HTML is not escaped.
        fooTagHtml,
        escapeHtmlDirective);
    assertTofuOutput(
        "&lt;foo&gt;",
        // But JS_STR_CHARS are.
        UnsafeSanitizedContentOrdainer.ordainAsSafe("<foo>", SanitizedContent.ContentKind.JS),
        escapeHtmlDirective);
    assertTofuOutput(
        "&lt;foo&gt;",
        // But CSS is.
        UnsafeSanitizedContentOrdainer.ordainAsSafe("<foo>", SanitizedContent.ContentKind.CSS),
        escapeHtmlDirective);
  }

  @Test
  public void testApplyForJsSrc() {
    EscapeHtmlDirective escapeHtmlDirective = new EscapeHtmlDirective();
    JsExpr dataRef = new JsExpr("opt_data.myKey", Integer.MAX_VALUE);
    assertThat(escapeHtmlDirective.applyForJsSrc(dataRef, ImmutableList.of()).getText())
        .isEqualTo("soy.$$escapeHtml(opt_data.myKey)");
  }

  @Test
  public void testApplyForPySrc() {
    EscapeHtmlDirective escapeHtmlDirective = new EscapeHtmlDirective();
    PyExpr data = new PyExpr("'data'", Integer.MAX_VALUE);
    assertThat(escapeHtmlDirective.applyForPySrc(data, ImmutableList.of()).getText())
        .isEqualTo("sanitize.escape_html('data')");
  }
}
