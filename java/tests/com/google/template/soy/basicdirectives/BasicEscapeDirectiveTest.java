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

import com.google.template.soy.coredirectives.EscapeHtmlDirective;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.shared.AbstractSoyPrintDirectiveTestCase;


/**
 */
public class BasicEscapeDirectiveTest extends AbstractSoyPrintDirectiveTestCase {

  public final void testApplyEscapeJsString() {
    BasicEscapeDirective escapeJsString = new BasicEscapeDirective.EscapeJsString();
    assertTofuOutput("", "", escapeJsString);
    assertTofuOutput("foo", "foo", escapeJsString);
    assertTofuOutput("foo\\\\bar", "foo\\bar", escapeJsString);
    assertTofuOutput(
        "foo\\\\bar",
        // Sanitized HTML is not exempt from escaping when embedded in JS.
        new SanitizedContent("foo\\bar", SanitizedContent.ContentKind.HTML), escapeJsString);
    assertTofuOutput(
        "foo\\bar",
        // But JS_STR_CHARS are.
        new SanitizedContent("foo\\bar", SanitizedContent.ContentKind.JS_STR_CHARS),
        escapeJsString);
    assertTofuOutput("\\\\", "\\", escapeJsString);
    assertTofuOutput("\\x27\\x27", "''", escapeJsString);
    assertTofuOutput("\\x22foo\\x22", "\"foo\"", escapeJsString);
    assertTofuOutput("42", 42, escapeJsString);

    new JsSrcPrintDirectiveTestBuilder()
        .addTest("", " '' ", escapeJsString)
        .addTest("foo", " 'foo' ", escapeJsString)
        .addTest("a\\\\b", " 'a\\\\b' ", escapeJsString)
        .addTest("foo\\\\bar", " new soydata.SanitizedHtml('foo\\\\bar') ", escapeJsString)
        .addTest("foo\\bar", " new soydata.SanitizedJsStrChars('foo\\\\bar') ", escapeJsString)
        .addTest("\\x27\\x27", " '\\'\\'' ", escapeJsString)
        .addTest("\\x22foo\\x22", " '\"foo\"' ", escapeJsString)
        .addTest("\\r\\n \\u2028", " '\\r\\n \\u2028' ", escapeJsString)
        .addTest("42", "42", escapeJsString)
        .runTests();
  }


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


  public final void testApplyFilterNormalizeUri() {
    BasicEscapeDirective filterNormalizeUri = new BasicEscapeDirective.FilterNormalizeUri();
    assertTofuOutput("", "", filterNormalizeUri);
    assertTofuOutput(
        "http://www.google.com/a%20b", "http://www.google.com/a b", filterNormalizeUri);
    assertTofuOutput("#zSoyz", "javascript:alert(1337)", filterNormalizeUri);
    assertTofuOutput("42", 42, filterNormalizeUri);

    new JsSrcPrintDirectiveTestBuilder()
        .addTest("", " '' ", filterNormalizeUri)
        .addTest(
            "http://www.google.com/a%20b", " 'http://www.google.com/a b' ", filterNormalizeUri)
        .addTest("zSoyz", " 'javascript:alert(1337)' ", filterNormalizeUri)  // TODO #zSoyz
        .addTest("42", " 42 ", filterNormalizeUri)
        .runTests();
  }


  public final void testEscapeHtmlAttributeNospace() {
    BasicEscapeDirective htmlNospaceDirective =
        new BasicEscapeDirective.EscapeHtmlAttributeNospace();
    assertTofuOutput("", "", htmlNospaceDirective);
    assertTofuOutput("a&amp;b&#32;&gt;&#32;c", "a&b > c", htmlNospaceDirective);
    assertTofuOutput(
        "&lt;script&gt;alert(&#39;boo&#39;);&lt;&#47;script&gt;",
        "<script>alert('boo');</script>",  htmlNospaceDirective);

    assertTofuOutput(
        "&#32;&lt;&#32;",
        // Sanitized HTML has tags stripped since this is directive is only used for attribute
        // values.
        new SanitizedContent("<foo> < <bar>", SanitizedContent.ContentKind.HTML),
        htmlNospaceDirective);
    assertTofuOutput(
        "&lt;foo&gt;",
        // But JS_STR_CHARS are.
        new SanitizedContent("<foo>", SanitizedContent.ContentKind.JS_STR_CHARS),
        htmlNospaceDirective);

    new JsSrcPrintDirectiveTestBuilder()
        .addTest("", "''", htmlNospaceDirective)
        .addTest("a&amp;b&#32;&gt;&#32;c", " 'a&b > c' ", htmlNospaceDirective)
        .addTest(
            "&lt;script&gt;alert(&#39;boo&#39;);&lt;&#47;script&gt;",
            " '<script>alert(\\'boo\\');</script>' ", htmlNospaceDirective)
        .addTest(
            "&#32;&lt;&#32;", "new soydata.SanitizedHtml('<foo> < <bar>')", htmlNospaceDirective)
        .addTest("&lt;foo&gt;", "new soydata.SanitizedJsStrChars('<foo>')", htmlNospaceDirective)
        .runTests();
  }


  public final void testEscapeUri() {
    BasicEscapeDirective escapeUri = new BasicEscapeDirective.EscapeUri();
    assertTofuOutput("", "", escapeUri);
    assertTofuOutput("a%25b%20%3E%20c", "a%b > c", escapeUri);
    assertTofuOutput(
        "a%25bc%20%3E%20d",
        new SanitizedContent("a%bc > d", SanitizedContent.ContentKind.HTML), escapeUri);
    assertTofuOutput(
        "a%bc%20%3E%20d",
        new SanitizedContent("a%bc > d", SanitizedContent.ContentKind.URI), escapeUri);

    new JsSrcPrintDirectiveTestBuilder()
        .addTest("", "''", escapeUri)
        .addTest("a%25b%20%3E%20c", " 'a%b > c' ", escapeUri)
        .addTest("a%25bc%20%3E%20d", "new soydata.SanitizedHtml('a%bc > d')", escapeUri)
        .addTest("a%bc%20%3E%20d", "new soydata.SanitizedUri('a%bc > d')", escapeUri)
        .runTests();
  }

}
