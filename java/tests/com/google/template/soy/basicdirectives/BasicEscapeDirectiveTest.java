/*
 * Copyright 2010 Google Inc.
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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.coredirectives.EscapeHtmlDirective;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import com.google.template.soy.shared.AbstractSoyPrintDirectiveTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BasicEscapeDirectiveTest extends AbstractSoyPrintDirectiveTestCase {

  @Test
  public final void testApplyEscapeJsString() {
    BasicEscapeDirective escapeJsString = new BasicEscapeDirective.EscapeJsString();
    assertTofuOutput("", "", escapeJsString);
    assertTofuOutput("foo", "foo", escapeJsString);
    assertTofuOutput("foo\\\\bar", "foo\\bar", escapeJsString);
    assertTofuOutput(
        "foo\\\\bar",
        // Sanitized HTML is not exempt from escaping when embedded in JS.
        UnsafeSanitizedContentOrdainer.ordainAsSafe("foo\\bar", SanitizedContent.ContentKind.HTML),
        escapeJsString);
    assertTofuOutput("\\\\", "\\", escapeJsString);
    assertTofuOutput("\\x27\\x27", "''", escapeJsString);
    assertTofuOutput("\\x22foo\\x22", "\"foo\"", escapeJsString);
    assertTofuOutput("42", 42, escapeJsString);

    new JsSrcPrintDirectiveTestBuilder()
        .addTest("", " '' ", escapeJsString)
        .addTest("foo", " 'foo' ", escapeJsString)
        .addTest("a\\\\b", " 'a\\\\b' ", escapeJsString)
        .addTest(
            "foo\\\\bar", " soydata.VERY_UNSAFE.ordainSanitizedHtml('foo\\\\bar') ", escapeJsString)
        .addTest("\\x27\\x27", " '\\'\\'' ", escapeJsString)
        .addTest("\\x22foo\\x22", " '\"foo\"' ", escapeJsString)
        .addTest("\\r\\n \\u2028", " '\\r\\n \\u2028' ", escapeJsString)
        .addTest("42", "42", escapeJsString)
        .runTests();
  }

  @Test
  public final void testApplyEscapeJsValue() {
    BasicEscapeDirective escapeJsValue = new BasicEscapeDirective.EscapeJsValue();
    assertTofuOutput("''", "", escapeJsValue);
    assertTofuOutput("'foo'", "foo", escapeJsValue);
    assertTofuOutput("'a\\\\b'", "a\\b", escapeJsValue);
    assertTofuOutput("'\\x27\\x27'", "''", escapeJsValue);
    assertTofuOutput("'\\x22foo\\x22'", "\"foo\"", escapeJsValue);
    assertTofuOutput("'\\r\\n \\u2028'", "\r\n \u2028", escapeJsValue);

    // Not quoted
    assertTofuOutput(" null ", null, escapeJsValue);
    assertTofuOutput(" 42.0 ", 42, escapeJsValue);
    assertTofuOutput(" true ", true, escapeJsValue);

    new JsSrcPrintDirectiveTestBuilder()
        .addTest("''", " '' ", escapeJsValue)
        .addTest("'foo'", " 'foo' ", escapeJsValue)
        .addTest("'a\\\\b'", " 'a\\\\b' ", escapeJsValue)
        .addTest("'\\x27\\x27'", " '\\'\\'' ", escapeJsValue)
        .addTest("'\\x22foo\\x22'", " '\"foo\"' ", escapeJsValue)
        .addTest("'\\r\\n \\u2028'", " '\\r\\n \\u2028' ", escapeJsValue)
        .addTest(" null ", "null", escapeJsValue)
        .addTest(" 42 ", "42", escapeJsValue)
        .addTest(" true ", "true", escapeJsValue)
        .runTests();
  }

  @Test
  public final void testApplyEscapeHtml() {
    EscapeHtmlDirective escapeHtml = new EscapeHtmlDirective();
    assertTofuOutput("", "", escapeHtml);
    assertTofuOutput("1 &lt; 2 &amp;amp;&amp;amp; 3 &lt; 4", "1 < 2 &amp;&amp; 3 < 4", escapeHtml);
    assertTofuOutput("42", 42, escapeHtml);

    new JsSrcPrintDirectiveTestBuilder()
        .addTest("", " '' ", escapeHtml)
        .addTest("1 &lt; 2 &amp;amp;&amp;amp; 3 &lt; 4", " '1 < 2 &amp;&amp; 3 < 4' ", escapeHtml)
        .addTest("42", " 42 ", escapeHtml)
        .runTests();
  }

  @Test
  public final void testApplyFilterNormalizeUri() {
    BasicEscapeDirective filterNormalizeUri = new BasicEscapeDirective.FilterNormalizeUri();
    assertTofuOutput("", "", filterNormalizeUri);
    assertTofuOutput(
        "http://www.google.com/a%20b", "http://www.google.com/a b", filterNormalizeUri);
    assertTofuOutput("about:invalid#zSoyz", "javascript:alert(1337)", filterNormalizeUri);
    assertTofuOutput("42", 42, filterNormalizeUri);

    new JsSrcPrintDirectiveTestBuilder()
        .addTest("", " '' ", filterNormalizeUri)
        .addTest("http://www.google.com/a%20b", " 'http://www.google.com/a b' ", filterNormalizeUri)
        .addTest("about:invalid#zSoyz", " 'javascript:alert(1337)' ", filterNormalizeUri)
        .addTest("42", " 42 ", filterNormalizeUri)
        .runTests();
  }

  @Test
  public final void testEscapeHtmlAttributeNospace() {
    BasicEscapeDirective htmlNospaceDirective =
        new BasicEscapeDirective.EscapeHtmlAttributeNospace();
    assertTofuOutput("", "", htmlNospaceDirective);
    assertTofuOutput("a&amp;b&#32;&gt;&#32;c", "a&b > c", htmlNospaceDirective);
    assertTofuOutput(
        "&lt;script&gt;alert(&#39;boo&#39;);&lt;&#47;script&gt;",
        "<script>alert('boo');</script>",
        htmlNospaceDirective);

    assertTofuOutput(
        "&#32;&lt;&#32;",
        // Sanitized HTML has tags stripped since this is directive is only used for attribute
        // values.
        UnsafeSanitizedContentOrdainer.ordainAsSafe(
            "<foo> < <bar>", SanitizedContent.ContentKind.HTML),
        htmlNospaceDirective);
    assertTofuOutput(
        "&lt;foo&gt;",
        // But JS_STR_CHARS are.
        UnsafeSanitizedContentOrdainer.ordainAsSafe("<foo>", SanitizedContent.ContentKind.JS),
        htmlNospaceDirective);

    new JsSrcPrintDirectiveTestBuilder()
        .addTest("", "''", htmlNospaceDirective)
        .addTest("a&amp;b&#32;&gt;&#32;c", " 'a&b > c' ", htmlNospaceDirective)
        .addTest(
            "&lt;script&gt;alert(&#39;boo&#39;);&lt;&#47;script&gt;",
            " '<script>alert(\\'boo\\');</script>' ",
            htmlNospaceDirective)
        .addTest(
            "&#32;&lt;&#32;",
            "soydata.VERY_UNSAFE.ordainSanitizedHtml('<foo> < <bar>')",
            htmlNospaceDirective)
        .addTest(
            "&lt;foo&gt;", "soydata.VERY_UNSAFE.ordainSanitizedJs('<foo>')", htmlNospaceDirective)
        .runTests();
  }

  @Test
  public final void testEscapeUri() {
    BasicEscapeDirective escapeUri = new BasicEscapeDirective.EscapeUri();
    assertTofuOutput("", "", escapeUri);
    assertTofuOutput("a%25b%20%3E%20c", "a%b > c", escapeUri);
    assertTofuOutput(
        "a%25bc%20%3E%20d",
        UnsafeSanitizedContentOrdainer.ordainAsSafe("a%bc > d", SanitizedContent.ContentKind.HTML),
        escapeUri);
    // NOTE: URIs are not treated specially (e.g. /redirect?continue={$url} should not allow $url
    // to break out and add other query params, and would be unexpected.)
    assertTofuOutput(
        "a%25bc%20%3E%20d",
        UnsafeSanitizedContentOrdainer.ordainAsSafe("a%bc > d", SanitizedContent.ContentKind.URI),
        escapeUri);

    new JsSrcPrintDirectiveTestBuilder()
        .addTest("", "''", escapeUri)
        .addTest("a%25b%20%3E%20c", " 'a%b > c' ", escapeUri)
        .addTest(
            "a%25bc%20%3E%20d", "soydata.VERY_UNSAFE.ordainSanitizedHtml('a%bc > d')", escapeUri)
        .addTest(
            "a%25bc%20%3E%20d", "soydata.VERY_UNSAFE.ordainSanitizedUri('a%bc > d')", escapeUri)
        .runTests();
  }

  @Test
  public final void testFilterCssValue() {
    BasicEscapeDirective filterCssValue = new BasicEscapeDirective.FilterCssValue();
    assertTofuOutput("", "", filterCssValue);
    assertTofuOutput("green", "green", filterCssValue);
    assertTofuOutput("zSoyz", "color:expression('foo')", filterCssValue);
    assertTofuOutput(
        "zSoyz",
        // NOTE: HTML content kind should not override CSS filtering :-)
        UnsafeSanitizedContentOrdainer.ordainAsSafe(
            "color:expression('foo')", SanitizedContent.ContentKind.HTML),
        filterCssValue);
    assertTofuOutput(
        "color:expression('foo')",
        UnsafeSanitizedContentOrdainer.ordainAsSafe(
            "color:expression('foo')", SanitizedContent.ContentKind.CSS),
        filterCssValue);

    new JsSrcPrintDirectiveTestBuilder()
        .addTest("", "''", filterCssValue)
        .addTest("green", "'green'", filterCssValue)
        .addTest("zSoyz", "'color:expression(\\'foo\\')'", filterCssValue)
        .addTest(
            "zSoyz",
            "soydata.VERY_UNSAFE.ordainSanitizedHtml('color:expression(\\'foo\\')')",
            filterCssValue)
        .addTest(
            "color:expression('foo')",
            "soydata.VERY_UNSAFE.ordainSanitizedCss('color:expression(\\'foo\\')')",
            filterCssValue)
        .runTests();
  }

  @Test
  public final void testPySrc() {
    PyExpr data = new PyStringExpr("'data'");

    // TODO(dcphillips): Add support for executing the sanitization call in Jython to verify it's
    // actual output. Currently the sanitization relies on integration tests for full verification.
    BasicEscapeDirective escapeJsString = new BasicEscapeDirective.EscapeJsString();
    assertThat(escapeJsString.applyForPySrc(data, ImmutableList.of()))
        .isEqualTo(new PyExpr("sanitize.escape_js_string('data')", Integer.MAX_VALUE));

    BasicEscapeDirective escapeHtmlAttribute = new BasicEscapeDirective.EscapeHtmlAttribute();
    assertThat(escapeHtmlAttribute.applyForPySrc(data, ImmutableList.of()))
        .isEqualTo(new PyExpr("sanitize.escape_html_attribute('data')", Integer.MAX_VALUE));

    BasicEscapeDirective filterCssValue = new BasicEscapeDirective.FilterCssValue();
    assertThat(filterCssValue.applyForPySrc(data, ImmutableList.of()))
        .isEqualTo(new PyExpr("sanitize.filter_css_value('data')", Integer.MAX_VALUE));
  }
}
