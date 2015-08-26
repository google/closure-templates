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

package com.google.template.soy.parsepasses.contextautoesc;

import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.soytree.RawTextNode;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Queue;

public class RawTextContextUpdaterTest extends TestCase {
  // The letter 'M' repeated 1500 times.
  private static final String M1500 = Strings.repeat("M", 1500);
  private static final int ANY_SLICES = -1;

  public final void testPcdata() throws Exception {
    assertTransition("HTML_PCDATA", "", "HTML_PCDATA");
    assertTransition("HTML_PCDATA", "Hello, World!", "HTML_PCDATA");
    assertTransition("HTML_PCDATA", "Jad loves ponies <3 <3 <3 !!!", "HTML_PCDATA");
    assertTransition("HTML_PCDATA", "OMG! Ponies, Ponies, Ponies &lt;3", "HTML_PCDATA");
    // Entering a tag
    assertTransition("HTML_PCDATA", "<", "HTML_BEFORE_OPEN_TAG_NAME");
    assertTransition("HTML_PCDATA", "Hello, <", "HTML_BEFORE_OPEN_TAG_NAME");
    assertTransition("HTML_PCDATA", "<h1", "HTML_TAG_NAME NORMAL");
    // Make sure that encoded HTML doesn't enter TAG.
    assertTransition("HTML_PCDATA", "&lt;a", "HTML_PCDATA");
    assertTransition("HTML_PCDATA", "<!--", "HTML_COMMENT");
    // Test special tags.
    assertTransition("HTML_PCDATA", "<script type='text/javascript'", "HTML_TAG SCRIPT");
    assertTransition("HTML_PCDATA", "<SCRIPT type='text/javascript'", "HTML_TAG SCRIPT");
    assertTransition("HTML_PCDATA", "<style type='text/css'", "HTML_TAG STYLE");
    assertTransition("HTML_PCDATA", "<sTyLe type='text/css'", "HTML_TAG STYLE");
    assertTransition("HTML_PCDATA", "<textarea name='text'", "HTML_TAG TEXTAREA");
    assertTransition("HTML_PCDATA", "<Title lang='en'", "HTML_TAG TITLE");
    assertTransition("HTML_PCDATA", "<xmp id='x'", "HTML_TAG XMP");
    // Into tag
    assertTransition("HTML_PCDATA", "<script>", "JS REGEX");
    assertTransition("HTML_PCDATA", "<script >", "JS REGEX");
    assertTransition("HTML_PCDATA", "<script type=\"text/javascript\">", "JS REGEX");
    assertTransition("HTML_PCDATA", "<a ", "HTML_TAG NORMAL");
    assertTransition("HTML_PCDATA", "<a title=foo id='x'", "HTML_TAG NORMAL");
    assertTransition("HTML_PCDATA", "<a title=\"foo\"", "HTML_TAG NORMAL");
    assertTransition("HTML_PCDATA", "<a title='foo'", "HTML_TAG NORMAL");
    assertTransition("HTML_PCDATA", "<a title='",
        "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT SINGLE_QUOTE");
    assertTransition("HTML_PCDATA", "<a data-foo='",
        "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT SINGLE_QUOTE");
    // Into attributes
    assertTransition("HTML_PCDATA", "<a onclick=\"", "JS NORMAL SCRIPT DOUBLE_QUOTE REGEX");
    assertTransition("HTML_PCDATA", "<a onclick=\'", "JS NORMAL SCRIPT SINGLE_QUOTE REGEX");
    assertTransition("HTML_PCDATA", "<a onclick=", "HTML_BEFORE_ATTRIBUTE_VALUE NORMAL SCRIPT");
    assertTransition(
        "HTML_PCDATA", "<a onclick=\"</script>", "JS_REGEX NORMAL SCRIPT DOUBLE_QUOTE");
    assertTransition("HTML_PCDATA", "<xmp style=\"", "CSS XMP STYLE DOUBLE_QUOTE");
    assertTransition("HTML_PCDATA", "<xmp style='/*", "CSS_COMMENT XMP STYLE SINGLE_QUOTE");
    assertTransition("HTML_PCDATA", "<script src=", "HTML_BEFORE_ATTRIBUTE_VALUE SCRIPT URI");
    assertTransition(
        "HTML_PCDATA", "<script src=/search?q=", "URI SCRIPT URI SPACE_OR_TAG_END QUERY");
    assertTransition(
        "HTML_PCDATA", "<script src=/foo#", "URI SCRIPT URI SPACE_OR_TAG_END FRAGMENT");
    assertTransition("HTML_PCDATA", "<img src=", "HTML_BEFORE_ATTRIBUTE_VALUE NORMAL URI");
    assertTransition(
        "HTML_PCDATA", "<a href=mailto:", "URI NORMAL URI SPACE_OR_TAG_END AUTHORITY_OR_PATH");
    assertTransition(
        "HTML_PCDATA", "<input type=button value= onclick=",
        "HTML_BEFORE_ATTRIBUTE_VALUE NORMAL SCRIPT");
    assertTransition("HTML_PCDATA", "<input type=button value=>", "HTML_PCDATA");
  }

  public final void testBeforeTagName() throws Exception {
    assertTransition("HTML_BEFORE_OPEN_TAG_NAME", "", "HTML_BEFORE_OPEN_TAG_NAME");
    assertTransition("HTML_BEFORE_OPEN_TAG_NAME", "/", "HTML_BEFORE_CLOSE_TAG_NAME");
    assertTransition("HTML_BEFORE_OPEN_TAG_NAME", "h1", "HTML_TAG_NAME NORMAL");
    assertTransition("HTML_BEFORE_OPEN_TAG_NAME", "svg:font-face id='x'", "HTML_TAG NORMAL");
    assertTransition("HTML_BEFORE_OPEN_TAG_NAME", ">", "HTML_PCDATA");
    assertTransition("HTML_BEFORE_OPEN_TAG_NAME", "><", "HTML_BEFORE_OPEN_TAG_NAME");
    // Abort tag name if we see things that aren't really tag names.
    assertTransition("HTML_BEFORE_OPEN_TAG_NAME", "3 Kitties!", "HTML_PCDATA");
    assertTransition("HTML_BEFORE_OPEN_TAG_NAME", " script", "HTML_PCDATA");

    assertTransition("HTML_BEFORE_CLOSE_TAG_NAME", "", "HTML_BEFORE_CLOSE_TAG_NAME");
    assertTransition("HTML_BEFORE_CLOSE_TAG_NAME", "9", "ERROR");
    assertTransition("HTML_BEFORE_CLOSE_TAG_NAME", "/", "ERROR");
    assertTransition("HTML_BEFORE_CLOSE_TAG_NAME", "h1", "HTML_TAG_NAME NORMAL");
    assertTransition("HTML_BEFORE_CLOSE_TAG_NAME", "svg:font-face", "HTML_TAG_NAME NORMAL");
    assertTransition("HTML_BEFORE_CLOSE_TAG_NAME", "div><", "HTML_BEFORE_OPEN_TAG_NAME");
    assertTransition("HTML_BEFORE_CLOSE_TAG_NAME", ">", "ERROR");
    assertTransition("HTML_BEFORE_CLOSE_TAG_NAME", "><", "ERROR");
  }

  public final void testTagName() throws Exception {
    assertTransition("HTML_TAG_NAME NORMAL", "", "HTML_TAG_NAME NORMAL");
    // Now, it's banned to do something like: <h{if 1}1{/if}>; instead the full tag name must be
    // specified.
    assertTransition("HTML_TAG_NAME NORMAL", "1", "ERROR");
    assertTransition("HTML_TAG_NAME NORMAL", "-foo", "ERROR");
    assertTransition("HTML_TAG_NAME NORMAL", " id='x'", "HTML_TAG NORMAL");
    assertTransition("HTML_TAG_NAME NORMAL", "\rid='x'", "HTML_TAG NORMAL");
    assertTransition("HTML_TAG_NAME NORMAL", "\tid='x'", "HTML_TAG NORMAL");
    assertTransition("HTML_TAG_NAME NORMAL", ">", "HTML_PCDATA");
    assertTransition("HTML_TAG_NAME NORMAL", "/>", "HTML_PCDATA");
    assertTransition("HTML_TAG_NAME NORMAL", " href=", "HTML_BEFORE_ATTRIBUTE_VALUE NORMAL URI");
    assertTransition("HTML_TAG_NAME NORMAL", " href=\"", "URI NORMAL URI DOUBLE_QUOTE START");
    assertTransition("HTML_TAG_NAME NORMAL", " href='", "URI NORMAL URI SINGLE_QUOTE START");
    assertTransition("HTML_TAG_NAME NORMAL", " href=#", "URI NORMAL URI SPACE_OR_TAG_END FRAGMENT");
    assertTransition("HTML_TAG_NAME NORMAL", " href=>", "HTML_PCDATA");
    assertTransition("HTML_TAG_NAME NORMAL", " onclick=\"", "JS NORMAL SCRIPT DOUBLE_QUOTE REGEX");
    assertTransition("HTML_TAG_NAME NORMAL", " style=\"", "CSS NORMAL STYLE DOUBLE_QUOTE");
    assertTransition(
        "HTML_TAG_NAME NORMAL", " stylez=\"",
        "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT DOUBLE_QUOTE");
    assertTransition(
        "HTML_TAG_NAME NORMAL", " title=\"",
        "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT DOUBLE_QUOTE");
    assertTransition("HTML_TAG_NAME NORMAL", "=foo>", "ERROR");
  }

  public final void testTag() throws Exception {
    assertTransition("HTML_TAG NORMAL", "", "HTML_TAG NORMAL");
    assertTransition("HTML_TAG NORMAL", ">", "HTML_PCDATA");
    assertTransition("HTML_TAG TEXTAREA", ">", "HTML_RCDATA TEXTAREA");
    assertTransition("HTML_TAG TITLE", ">", "HTML_RCDATA TITLE");
    assertTransition("HTML_TAG SCRIPT", ">", "JS REGEX");
    assertTransition("HTML_TAG STYLE", ">", "CSS");
    assertTransition("HTML_TAG NORMAL", "-->", "ERROR");
    assertTransition("HTML_TAG NORMAL", " -->", "ERROR");
    assertTransition("HTML_TAG NORMAL", "=foo>", "ERROR");
    // As in <foo on{$handlerType}="jsHere()">
    assertTransition("HTML_TAG NORMAL", " on", "HTML_ATTRIBUTE_NAME NORMAL SCRIPT", 1);
    assertTransition("HTML_TAG NORMAL", " ONCLICK", "HTML_ATTRIBUTE_NAME NORMAL SCRIPT", 1);
    assertTransition("HTML_TAG NORMAL", " style", "HTML_ATTRIBUTE_NAME NORMAL STYLE", 1);
    assertTransition("HTML_TAG NORMAL", " HREF", "HTML_ATTRIBUTE_NAME NORMAL URI", 1);
    assertTransition("HTML_TAG XMP", " title", "HTML_ATTRIBUTE_NAME XMP PLAIN_TEXT", 1);
    assertTransition("HTML_TAG NORMAL", " checked ", "HTML_TAG NORMAL", 3);
    assertTransition("HTML_TAG NORMAL", " xlink:href", "HTML_ATTRIBUTE_NAME NORMAL URI", 1);
    assertTransition("HTML_TAG NORMAL", " g:url", "HTML_ATTRIBUTE_NAME NORMAL URI", 1);
    assertTransition("HTML_TAG NORMAL", " g:iconUri", "HTML_ATTRIBUTE_NAME NORMAL URI", 1);
    assertTransition("HTML_TAG NORMAL", " g:urlItem", "HTML_ATTRIBUTE_NAME NORMAL URI", 1);
    assertTransition("HTML_TAG NORMAL", " g:hourly", "HTML_ATTRIBUTE_NAME NORMAL PLAIN_TEXT", 1);
    assertTransition("HTML_TAG NORMAL", " xmlns", "HTML_ATTRIBUTE_NAME NORMAL URI", 1);
    assertTransition("HTML_TAG NORMAL", " xmlns:foo", "HTML_ATTRIBUTE_NAME NORMAL URI", 1);
    assertTransition("HTML_TAG NORMAL", " xmlnsxyz", "HTML_ATTRIBUTE_NAME NORMAL PLAIN_TEXT", 1);
    assertTransition("HTML_TAG NORMAL", " xmlnsxyz?", "HTML_ATTRIBUTE_NAME NORMAL PLAIN_TEXT", 1);
    assertTransition("HTML_TAG NORMAL", " xml?nsxyz", "HTML_ATTRIBUTE_NAME NORMAL PLAIN_TEXT", 1);
    assertTransition("HTML_TAG NORMAL", " xmlnsxyz$", "HTML_ATTRIBUTE_NAME NORMAL PLAIN_TEXT", 1);
    assertTransition("HTML_TAG NORMAL", " xml$nsxyz", "HTML_ATTRIBUTE_NAME NORMAL PLAIN_TEXT", 1);
    assertTransition("HTML_TAG NORMAL", " svg:style='", "CSS NORMAL STYLE SINGLE_QUOTE", 3);
  }

  public final void testHtmlComment() throws Exception {
    assertTransition("HTML_COMMENT", "", "HTML_COMMENT");
    assertTransition("HTML_COMMENT", " ", "HTML_COMMENT");
    assertTransition("HTML_COMMENT", "\r", "HTML_COMMENT");
    assertTransition("HTML_COMMENT", "/", "HTML_COMMENT");
    assertTransition("HTML_COMMENT", "x", "HTML_COMMENT");
    assertTransition("HTML_COMMENT", ">", "HTML_COMMENT");
    assertTransition("HTML_COMMENT", "-", "HTML_COMMENT");
    assertTransition("HTML_COMMENT", "-- >", "HTML_COMMENT");
    assertTransition("HTML_COMMENT", "->", "HTML_COMMENT");
    assertTransition("HTML_COMMENT", "-->", "HTML_PCDATA");
    assertTransition("HTML_COMMENT", "--->", "HTML_PCDATA");
    assertTransition("HTML_COMMENT", "<!--", "HTML_COMMENT");
  }

  public final void testAttrName() throws Exception {
    assertTransition("HTML_ATTRIBUTE_NAME XMP URI", "=", "HTML_BEFORE_ATTRIBUTE_VALUE XMP URI");
    assertTransition(
        "HTML_ATTRIBUTE_NAME TEXTAREA PLAIN_TEXT", "=",
        "HTML_BEFORE_ATTRIBUTE_VALUE TEXTAREA PLAIN_TEXT");
    assertTransition(
        "HTML_ATTRIBUTE_NAME NORMAL PLAIN_TEXT", " = ",
        "HTML_BEFORE_ATTRIBUTE_VALUE NORMAL PLAIN_TEXT");
  }

  public final void testBeforeAttrValue() throws Exception {
    assertTransition(
        "HTML_BEFORE_ATTRIBUTE_VALUE NORMAL URI", "\"", "URI NORMAL URI DOUBLE_QUOTE START");
    assertTransition(
        "HTML_BEFORE_ATTRIBUTE_VALUE NORMAL SCRIPT", "'", "JS NORMAL SCRIPT SINGLE_QUOTE REGEX");
    assertTransition(
        "HTML_BEFORE_ATTRIBUTE_VALUE NORMAL STYLE", "\"", "CSS NORMAL STYLE DOUBLE_QUOTE");
    assertTransition(
        "HTML_BEFORE_ATTRIBUTE_VALUE TEXTAREA STYLE", "color",
        "CSS TEXTAREA STYLE SPACE_OR_TAG_END");
    assertTransition(
        "HTML_BEFORE_ATTRIBUTE_VALUE NORMAL URI", "/",
        "URI NORMAL URI SPACE_OR_TAG_END AUTHORITY_OR_PATH");
    assertTransition(
        "HTML_BEFORE_ATTRIBUTE_VALUE TITLE PLAIN_TEXT", "\"",
        "HTML_NORMAL_ATTR_VALUE TITLE PLAIN_TEXT DOUBLE_QUOTE");
    assertTransition("HTML_BEFORE_ATTRIBUTE_VALUE NORMAL PLAIN_TEXT", ">", "HTML_PCDATA");
    assertTransition("HTML_BEFORE_ATTRIBUTE_VALUE TITLE PLAIN_TEXT", ">", "HTML_RCDATA TITLE");
  }

  public final void testAttr() throws Exception {
    assertTransition(
        "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT DOUBLE_QUOTE", "",
        "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT DOUBLE_QUOTE");
    assertTransition(
        "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT SINGLE_QUOTE", "",
        "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT SINGLE_QUOTE");
    assertTransition(
        "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT SPACE_OR_TAG_END", "",
        "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT SPACE_OR_TAG_END");
    assertTransition(
        "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT DOUBLE_QUOTE", "foo",
        "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT DOUBLE_QUOTE");
    assertTransition(
        "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT SINGLE_QUOTE", "foo",
        "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT SINGLE_QUOTE");
    assertTransition(
        "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT SPACE_OR_TAG_END", "foo",
        "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT SPACE_OR_TAG_END");
    assertTransition(
        "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT DOUBLE_QUOTE", "\"", "HTML_TAG NORMAL");
    assertTransition(
        "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT SINGLE_QUOTE", "'", "HTML_TAG NORMAL");
    assertTransition(
        "HTML_NORMAL_ATTR_VALUE SCRIPT PLAIN_TEXT SINGLE_QUOTE", "'", "HTML_TAG SCRIPT");
    assertTransition(
        "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT SPACE_OR_TAG_END", " x='",
        "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT SINGLE_QUOTE");
    assertTransition(
        "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT SPACE_OR_TAG_END", " x='y'", "HTML_TAG NORMAL");
    assertTransition(
        "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT SPACE_OR_TAG_END", ">", "HTML_PCDATA");
    assertTransition(
        "HTML_NORMAL_ATTR_VALUE SCRIPT PLAIN_TEXT SPACE_OR_TAG_END", ">", "JS REGEX");
  }

  public final void testCss() throws Exception {
    assertTransition("CSS", "", "CSS");
    assertTransition("CSS", " p { color: red; }", "CSS");
    assertTransition("CSS", "p.clazz#id {\r\n  border: 2px;\n}", "CSS");
    assertTransition("CSS", "/*\nHello, World! */", "CSS");
    assertTransition("CSS", "/*", "CSS_COMMENT");
    assertTransition("CSS", "/**", "CSS_COMMENT");
    assertTransition("CSS", "/** '", "CSS_COMMENT");
    assertTransition("CSS", "/** \"foo", "CSS_COMMENT");
    assertTransition("CSS", "'", "CSS_SQ_STRING");
    assertTransition("CSS", "\"", "CSS_DQ_STRING");
    assertTransition("CSS", "\" /* hello", "CSS_DQ_STRING");
    assertTransition("CSS", "url(", "CSS_URI START");
    assertTransition("CSS", "url(/search?q=", "CSS_URI QUERY");
    assertTransition("CSS", "url(  ", "CSS_URI START");
    assertTransition("CSS", "url('", "CSS_SQ_URI START");
    assertTransition("CSS", "url('//", "CSS_SQ_URI AUTHORITY_OR_PATH");
    assertTransition("CSS", "url('/search?q=", "CSS_SQ_URI QUERY");
    assertTransition("CSS", "url(\"", "CSS_DQ_URI START");
    assertTransition("CSS", "url(\"/search?q=", "CSS_DQ_URI QUERY");
    assertTransition("CSS", "url(\"/foo#bar", "CSS_DQ_URI FRAGMENT");
    assertTransition("CSS", "</style", "HTML_TAG NORMAL");  // Not a start tag so NORMAL.
    assertTransition("CSS", "</Style", "HTML_TAG NORMAL");
    // Close style tag in attribute value is not a break.  Ok to transition to ERROR.
    assertTransition("CSS NORMAL STYLE DOUBLE_QUOTE", "</style", "CSS NORMAL STYLE DOUBLE_QUOTE");
  }

  public final void testCssComment() throws Exception {
    assertTransition("CSS_COMMENT", "", "CSS_COMMENT");
    assertTransition("CSS_COMMENT", "\r\n\n\r", "CSS_COMMENT");
    assertTransition("CSS_COMMENT", " * /", "CSS_COMMENT");
    assertTransition("CSS_COMMENT", " */", "CSS");
    assertTransition("CSS_COMMENT", "**/", "CSS");
    assertTransition("CSS_COMMENT", "\\*/", "CSS");
    assertTransition(
        "CSS_COMMENT NORMAL STYLE SPACE_OR_TAG_END", "*/", "CSS NORMAL STYLE SPACE_OR_TAG_END");
  }

  public final void testCssDqString() throws Exception {
    assertTransition("CSS_DQ_STRING", "", "CSS_DQ_STRING");
    assertTransition("CSS_DQ_STRING", "Hello, World!", "CSS_DQ_STRING");
    assertTransition("CSS_DQ_STRING", "Don't", "CSS_DQ_STRING");
    assertTransition("CSS_DQ_STRING", "\"", "CSS");
    assertTransition("CSS_DQ_STRING", "\\22", "CSS_DQ_STRING");
    assertTransition("CSS_DQ_STRING", "\\22 ", "CSS_DQ_STRING");
    assertTransition("CSS_DQ_STRING", "\\27", "CSS_DQ_STRING");
    assertTransition("CSS_DQ_STRING", "\r", "ERROR");
    assertTransition("CSS_DQ_STRING", "\n", "ERROR");
    assertTransition("CSS_DQ_STRING", "</style>", "HTML_PCDATA");  // Or error.
  }

  public final void testCssSqString() throws Exception {
    assertTransition("CSS_SQ_STRING", "", "CSS_SQ_STRING");
    assertTransition("CSS_SQ_STRING", "Hello, World!", "CSS_SQ_STRING");
    assertTransition("CSS_SQ_STRING", "M. \"The Greatest!\" Ali", "CSS_SQ_STRING");
    assertTransition("CSS_SQ_STRING", "'", "CSS");
    assertTransition("CSS_SQ_STRING", "\\22", "CSS_SQ_STRING");
    assertTransition("CSS_SQ_STRING", "\\22 ", "CSS_SQ_STRING");
    assertTransition("CSS_SQ_STRING", "\\27", "CSS_SQ_STRING");
    assertTransition("CSS_SQ_STRING", "\r", "ERROR");
    assertTransition("CSS_SQ_STRING", "\n", "ERROR");
    assertTransition("CSS_SQ_STRING", "</style>", "HTML_PCDATA");  // Or error.
    assertTransition(
        "CSS_SQ_STRING NORMAL STYLE SPACE_OR_TAG_END", "'", "CSS NORMAL STYLE SPACE_OR_TAG_END");
  }

  public final void testCssUri() throws Exception {
    assertTransition("CSS_URI START", "", "CSS_URI START");
    assertTransition("CSS_URI START", "/search?q=cute+bunnies", "CSS_URI QUERY");
    assertTransition("CSS_URI START", "#anchor)", "CSS");
    assertTransition("CSS_URI START", "#anchor )", "CSS");
    assertTransition("CSS_URI START", "/do+not+panic", "CSS_URI AUTHORITY_OR_PATH");
    assertTransition("CSS_SQ_URI START", "/don%27t+panic", "CSS_SQ_URI AUTHORITY_OR_PATH");
    assertTransition("CSS_SQ_URI START", "Muhammed+\"The+Greatest!\"+Ali",
        "CSS_SQ_URI MAYBE_SCHEME");
    assertTransition("CSS_SQ_URI START", "(/don%27t+panic)", "CSS_SQ_URI AUTHORITY_OR_PATH");
    assertTransition(
        "CSS_DQ_URI START", "Muhammed+%22The+Greatest!%22+Ali", "CSS_DQ_URI MAYBE_SCHEME");
    assertTransition("CSS_DQ_URI START", "/don't+panic", "CSS_DQ_URI AUTHORITY_OR_PATH");
    assertTransition("CSS_SQ_URI START", "#foo'", "CSS");
    assertTransition(
        "CSS_URI NORMAL STYLE SPACE_OR_TAG_END START", ")", "CSS NORMAL STYLE SPACE_OR_TAG_END");
    assertTransition("CSS_DQ_URI NORMAL STYLE SINGLE_QUOTE AUTHORITY_OR_PATH", "\"",
        "CSS NORMAL STYLE SINGLE_QUOTE");
    assertTransition(
        "CSS_SQ_URI NORMAL STYLE DOUBLE_QUOTE FRAGMENT", "#x'", "CSS NORMAL STYLE DOUBLE_QUOTE");
  }

  public final void testJsBeforeRegex() throws Exception {
    assertTransition("JS REGEX", "", "JS REGEX");
    assertTransition("JS REGEX", "/*", "JS_BLOCK_COMMENT REGEX");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END REGEX", "/*",
        "JS_BLOCK_COMMENT NORMAL SCRIPT SPACE_OR_TAG_END REGEX");
    assertTransition("JS REGEX", "//", "JS_LINE_COMMENT REGEX");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END REGEX", "//",
        "JS_LINE_COMMENT NORMAL SCRIPT SPACE_OR_TAG_END REGEX");
    assertTransition("JS REGEX", "'", "JS_SQ_STRING");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END REGEX", "'",
        "JS_SQ_STRING NORMAL SCRIPT SPACE_OR_TAG_END");
    assertTransition("JS REGEX", "\"", "JS_DQ_STRING");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END REGEX", "\"",
        "JS_DQ_STRING NORMAL SCRIPT SPACE_OR_TAG_END");
    assertTransition("JS REGEX", "42", "JS DIV_OP");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END REGEX", "42",
        "JS NORMAL SCRIPT SPACE_OR_TAG_END DIV_OP");
    assertTransition("JS REGEX", "0.", "JS DIV_OP");
    assertTransition("JS REGEX", "x", "JS DIV_OP");
    assertTransition("JS REGEX", "-", "JS REGEX");
    assertTransition("JS REGEX", "--", "JS DIV_OP");
    assertTransition("JS REGEX", " \t \n ", "JS REGEX");
    assertTransition("JS REGEX", ")", "JS DIV_OP");
    assertTransition("JS REGEX", "/", "JS_REGEX");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END REGEX", "/",
        "JS_REGEX NORMAL SCRIPT SPACE_OR_TAG_END");
    assertTransition("JS REGEX", "/[xy]/", "JS DIV_OP");
    assertTransition("JS REGEX", "</script>", "HTML_PCDATA");
  }

  public final void testJsBeforeDivOp() throws Exception {
    assertTransition("JS DIV_OP", "", "JS DIV_OP");
    assertTransition("JS DIV_OP", "/*", "JS_BLOCK_COMMENT DIV_OP");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END DIV_OP", "/*",
        "JS_BLOCK_COMMENT NORMAL SCRIPT SPACE_OR_TAG_END DIV_OP");
    assertTransition("JS DIV_OP", "//", "JS_LINE_COMMENT DIV_OP");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END DIV_OP", "//",
        "JS_LINE_COMMENT NORMAL SCRIPT SPACE_OR_TAG_END DIV_OP");
    assertTransition("JS DIV_OP", "'", "JS_SQ_STRING");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END DIV_OP", "'",
        "JS_SQ_STRING NORMAL SCRIPT SPACE_OR_TAG_END");
    assertTransition("JS DIV_OP", "\"", "JS_DQ_STRING");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END DIV_OP", "\"",
        "JS_DQ_STRING NORMAL SCRIPT SPACE_OR_TAG_END");
    assertTransition("JS DIV_OP", "42", "JS DIV_OP");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END DIV_OP", "42",
        "JS NORMAL SCRIPT SPACE_OR_TAG_END DIV_OP");
    assertTransition("JS DIV_OP", "0.", "JS DIV_OP");
    assertTransition("JS DIV_OP", "x", "JS DIV_OP");
    assertTransition("JS DIV_OP", "-", "JS REGEX");
    assertTransition("JS DIV_OP", "--", "JS DIV_OP");
    assertTransition("JS DIV_OP", "  \n ", "JS DIV_OP");
    assertTransition("JS DIV_OP", ")", "JS DIV_OP");
    assertTransition("JS DIV_OP", "/", "JS REGEX");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END DIV_OP", "/",
        "JS NORMAL SCRIPT SPACE_OR_TAG_END REGEX");
    assertTransition("JS DIV_OP", "/[xy]/", "JS REGEX");
    assertTransition("JS DIV_OP", "</script>", "HTML_PCDATA");
  }

  public final void testJsLineComment() throws Exception {
    assertTransition("JS_LINE_COMMENT DIV_OP", "", "JS_LINE_COMMENT DIV_OP");
    assertTransition("JS_LINE_COMMENT DIV_OP", "*/", "JS_LINE_COMMENT DIV_OP");
    assertTransition("JS_LINE_COMMENT DIV_OP", "Hello, World!", "JS_LINE_COMMENT DIV_OP");
    assertTransition("JS_LINE_COMMENT DIV_OP", "\"'/", "JS_LINE_COMMENT DIV_OP");
    assertTransition("JS_LINE_COMMENT DIV_OP", "\n", "JS DIV_OP");
    assertTransition(
        "JS_LINE_COMMENT NORMAL SCRIPT DOUBLE_QUOTE DIV_OP", "\n",
        "JS NORMAL SCRIPT DOUBLE_QUOTE DIV_OP");
    assertTransition("JS_LINE_COMMENT DIV_OP", "</script>", "HTML_PCDATA");
    assertTransition("JS_LINE_COMMENT REGEX", "", "JS_LINE_COMMENT REGEX");
    assertTransition("JS_LINE_COMMENT REGEX", "*/", "JS_LINE_COMMENT REGEX");
    assertTransition("JS_LINE_COMMENT REGEX", "Hello, World!", "JS_LINE_COMMENT REGEX");
    assertTransition("JS_LINE_COMMENT REGEX", "\"'/", "JS_LINE_COMMENT REGEX");
    assertTransition("JS_LINE_COMMENT REGEX", "\n", "JS REGEX");
    assertTransition("JS_LINE_COMMENT REGEX", "</script>", "HTML_PCDATA");
  }

  public final void testJsBlockComment() throws Exception {
    assertTransition("JS_BLOCK_COMMENT DIV_OP", "", "JS_BLOCK_COMMENT DIV_OP");
    assertTransition("JS_BLOCK_COMMENT DIV_OP", "\n", "JS_BLOCK_COMMENT DIV_OP");
    assertTransition("JS_BLOCK_COMMENT DIV_OP", "Hello, World!", "JS_BLOCK_COMMENT DIV_OP");
    assertTransition("JS_BLOCK_COMMENT DIV_OP", "\"'/", "JS_BLOCK_COMMENT DIV_OP");
    assertTransition("JS_BLOCK_COMMENT DIV_OP", "*/", "JS DIV_OP");
    assertTransition(
        "JS_BLOCK_COMMENT NORMAL SCRIPT DOUBLE_QUOTE DIV_OP", "*/",
        "JS NORMAL SCRIPT DOUBLE_QUOTE DIV_OP");
    assertTransition("JS_BLOCK_COMMENT DIV_OP", "</script>", "HTML_PCDATA");
    assertTransition("JS_BLOCK_COMMENT REGEX", "", "JS_BLOCK_COMMENT REGEX");
    assertTransition("JS_BLOCK_COMMENT REGEX", "\r\n", "JS_BLOCK_COMMENT REGEX");
    assertTransition("JS_BLOCK_COMMENT REGEX", "Hello, World!", "JS_BLOCK_COMMENT REGEX");
    assertTransition("JS_BLOCK_COMMENT REGEX", "\"'/", "JS_BLOCK_COMMENT REGEX");
    assertTransition("JS_BLOCK_COMMENT REGEX", "*/", "JS REGEX");
    assertTransition("JS_BLOCK_COMMENT REGEX", "</script>", "HTML_PCDATA");  // Or error.
  }

  public final void testJsDqString() throws Exception {
    assertTransition("JS_DQ_STRING", "", "JS_DQ_STRING");
    assertTransition("JS_DQ_STRING", "Hello, World!", "JS_DQ_STRING");
    assertTransition("JS_DQ_STRING", M1500, "JS_DQ_STRING"); // Check for stack overflow
    assertTransition("JS_DQ_STRING", "\"", "JS DIV_OP");
    assertTransition(
        "JS_DQ_STRING NORMAL SCRIPT SINGLE_QUOTE", "Hello, World!",
        "JS_DQ_STRING NORMAL SCRIPT SINGLE_QUOTE");
    assertTransition(
        "JS_DQ_STRING NORMAL SCRIPT SINGLE_QUOTE", "\"", "JS NORMAL SCRIPT SINGLE_QUOTE DIV_OP");
    assertTransition("JS_DQ_STRING", "</script>", "HTML_PCDATA");  // Or error.
    assertTransition("JS_DQ_STRING", "</p>", "JS_DQ_STRING");
  }

  public final void testJsSqString() throws Exception {
    assertTransition("JS_SQ_STRING", "", "JS_SQ_STRING");
    assertTransition("JS_SQ_STRING", "Hello, World!", "JS_SQ_STRING");
    assertTransition("JS_SQ_STRING", M1500, "JS_SQ_STRING"); // Check for stack overflow
    assertTransition("JS_SQ_STRING", "/*", "JS_SQ_STRING");
    assertTransition("JS_SQ_STRING", "\"", "JS_SQ_STRING");
    assertTransition("JS_SQ_STRING", "\\x27", "JS_SQ_STRING");
    assertTransition("JS_SQ_STRING", "\\'", "JS_SQ_STRING");
    assertTransition("JS_SQ_STRING", "\r", "ERROR");
    assertTransition("JS_SQ_STRING", "\\\rn", "JS_SQ_STRING");
    assertTransition("JS_SQ_STRING", "'", "JS DIV_OP");
    assertTransition(
        "JS_SQ_STRING NORMAL SCRIPT DOUBLE_QUOTE", "Hello, World!",
        "JS_SQ_STRING NORMAL SCRIPT DOUBLE_QUOTE");
    assertTransition(
        "JS_SQ_STRING NORMAL SCRIPT DOUBLE_QUOTE", "'", "JS NORMAL SCRIPT DOUBLE_QUOTE DIV_OP");
    assertTransition("JS_SQ_STRING", "</script>", "HTML_PCDATA");  // Or error.
    assertTransition("JS_SQ_STRING", "</s>", "JS_SQ_STRING");
  }

  public final void testJsRegex() throws Exception {
    assertTransition("JS_REGEX", "", "JS_REGEX");
    assertTransition("JS_REGEX", "Hello, World!", "JS_REGEX");
    assertTransition("JS_REGEX", "\\/*", "JS_REGEX");
    assertTransition("JS_REGEX", "[/*]", "JS_REGEX");
    assertTransition("JS_REGEX", "\"", "JS_REGEX");
    assertTransition("JS_REGEX", "\\x27", "JS_REGEX");
    assertTransition("JS_REGEX", "\\'", "JS_REGEX");
    assertTransition("JS_REGEX", "\r", "ERROR");
    assertTransition("JS_REGEX", "\\\rn", "ERROR");  // Line continuations not allowed in RegExps.
    assertTransition("JS_REGEX", "/", "JS DIV_OP");
    assertTransition(
        "JS_REGEX NORMAL SCRIPT DOUBLE_QUOTE", "Hello, World!",
        "JS_REGEX NORMAL SCRIPT DOUBLE_QUOTE");
    assertTransition(
        "JS_REGEX NORMAL SCRIPT DOUBLE_QUOTE", "/", "JS NORMAL SCRIPT DOUBLE_QUOTE DIV_OP");
    assertTransition("JS_REGEX", "</script>", "HTML_PCDATA");  // Or error.
  }

  public final void testUri() throws Exception {
    assertTransition("URI START", "", "URI START");
    assertTransition("URI START", ".", "URI MAYBE_SCHEME");
    assertTransition("URI START", "/", "URI AUTHORITY_OR_PATH");
    assertTransition("URI START", "#", "URI FRAGMENT");
    assertTransition("URI START", "x", "URI MAYBE_SCHEME");
    assertTransition("URI START", "x:", "URI AUTHORITY_OR_PATH");
    assertTransition("URI START", "?", "URI QUERY");
    assertTransition("URI START", "&", "URI MAYBE_SCHEME");
    assertTransition("URI START", "=", "URI MAYBE_SCHEME");
    assertTransition("URI START", "javascript:", "URI DANGEROUS_SCHEME");
    assertTransition("URI START", "JavaScript:", "URI DANGEROUS_SCHEME");
    assertTransition("URI START", "not-javascript:", "URI AUTHORITY_OR_PATH");

    assertTransition("URI QUERY", "", "URI QUERY");
    assertTransition("URI QUERY", ".", "URI QUERY");
    assertTransition("URI QUERY", "/", "URI QUERY");
    assertTransition("URI QUERY", "#", "URI FRAGMENT");
    assertTransition("URI QUERY", "x", "URI QUERY");
    assertTransition("URI QUERY", "&", "URI QUERY");
    assertTransition("URI QUERY", "javascript:", "URI QUERY");

    assertTransition("URI FRAGMENT", "", "URI FRAGMENT");
    assertTransition("URI FRAGMENT", "?", "URI FRAGMENT");
    assertTransition("URI FRAGMENT", "javascript:", "URI FRAGMENT");

    assertTransition("URI MAYBE_SCHEME", ":", "URI AUTHORITY_OR_PATH");
    assertTransition("URI MAYBE_SCHEME", ".", "URI MAYBE_SCHEME");
    assertTransition("URI MAYBE_SCHEME", "foo.bar", "URI MAYBE_SCHEME"); // Schemes can have a dot.
    assertTransition("URI MAYBE_SCHEME", "/", "URI AUTHORITY_OR_PATH");
    assertTransition("URI MAYBE_SCHEME", "/foo", "URI AUTHORITY_OR_PATH");
    assertTransition("URI MAYBE_SCHEME", "?", "URI QUERY");
    assertTransition("URI MAYBE_SCHEME", "blah?blah", "URI QUERY");
    assertTransition("URI MAYBE_SCHEME", "#", "URI FRAGMENT");
    // If we have a hard-coded prefix, & and = don't do anything.
    assertTransition("URI MAYBE_SCHEME", "=", "URI MAYBE_SCHEME");
    assertTransition("URI MAYBE_SCHEME", "&", "URI MAYBE_SCHEME");
    // We don't care about schemes that end with javascript:.
    assertTransition("URI MAYBE_SCHEME", "javascript:", "URI AUTHORITY_OR_PATH");

    assertTransition("URI MAYBE_VARIABLE_SCHEME", ".", "URI MAYBE_VARIABLE_SCHEME");
    assertTransition("URI MAYBE_VARIABLE_SCHEME", "foo.bar", "URI MAYBE_VARIABLE_SCHEME");
    assertTransition("URI MAYBE_VARIABLE_SCHEME", "/", "URI AUTHORITY_OR_PATH");
    assertTransition("URI MAYBE_VARIABLE_SCHEME", "foo/bar", "URI AUTHORITY_OR_PATH");
    assertTransition("URI MAYBE_VARIABLE_SCHEME", "?", "URI QUERY");
    assertTransition("URI MAYBE_VARIABLE_SCHEME", "#", "URI FRAGMENT");
    // If we have a variable prefix, we use & and = to heuristically transition.
    assertTransition("URI MAYBE_VARIABLE_SCHEME", "=", "URI QUERY");
    assertTransition("URI MAYBE_VARIABLE_SCHEME", "&", "URI QUERY");
    assertTransition("URI MAYBE_VARIABLE_SCHEME", "bah&foo=", "URI QUERY");
    assertTransition("URI MAYBE_VARIABLE_SCHEME", ":", "ERROR");
    assertTransition("URI MAYBE_VARIABLE_SCHEME", "javascript:", "ERROR");
  }

  public final void testRcdata() throws Exception {
    assertTransition("HTML_RCDATA XMP", "", "HTML_RCDATA XMP");
    assertTransition("HTML_RCDATA XMP", "Hello, World!", "HTML_RCDATA XMP");
    assertTransition("HTML_RCDATA XMP", "<p", "HTML_RCDATA XMP");
    assertTransition("HTML_RCDATA XMP", "<p ", "HTML_RCDATA XMP");
    assertTransition("HTML_RCDATA XMP", "</textarea>", "HTML_RCDATA XMP");
    assertTransition("HTML_RCDATA XMP", "</xmp>", "HTML_PCDATA");
    assertTransition("HTML_RCDATA XMP", "</xMp>", "HTML_PCDATA");
    assertTransition("HTML_RCDATA TEXTAREA", "</xmp>", "HTML_RCDATA TEXTAREA");
    assertTransition("HTML_RCDATA TEXTAREA", "</textarea>", "HTML_PCDATA");
  }

  public final void testText() throws Exception {
    // Plain text's only edge should be back to itself.
    assertTransition("TEXT", "", "TEXT");
    assertTransition("TEXT", "Hello, World!", "TEXT");
    assertTransition("TEXT", "<p", "TEXT");
    assertTransition("TEXT", "&D*(@*(#*(AW*D(J*#(J*(JS!!!''\"", "TEXT");
    assertTransition("TEXT", "<script>var x='", "TEXT");
    assertTransition("TEXT", "<a href='", "TEXT");
  }

  public final void testTemplateElementNesting() throws Exception {
    assertTransition("HTML_PCDATA", "<template>", "HTML_PCDATA templateNestDepth=1");
    assertTransition("HTML_PCDATA", "<template id='i'>foo", "HTML_PCDATA templateNestDepth=1");
    assertTransition("HTML_PCDATA", "<template>foo<template>", "HTML_PCDATA templateNestDepth=2");
    assertTransition("HTML_PCDATA", "<template>foo</template>", "HTML_PCDATA");
    assertTransition("HTML_PCDATA", "<template>foo<template></template>",
                     "HTML_PCDATA templateNestDepth=1");
    assertTransition("HTML_PCDATA", "<template>foo<script>//</template></script>",
                     "HTML_PCDATA templateNestDepth=1");
    assertTransition("HTML_PCDATA", "<template>foo<!--</template>-->",
                     "HTML_PCDATA templateNestDepth=1");
    assertTransition("HTML_PCDATA", "</template>", "ERROR");
    assertTransition("HTML_PCDATA", "<template>foo</template></template>", "ERROR");
    assertTransition("HTML_PCDATA templateNestDepth=4", "</template>",
                     "HTML_PCDATA templateNestDepth=3");
    assertTransition("HTML_PCDATA templateNestDepth=4", "</TempLate>",
                     "HTML_PCDATA templateNestDepth=3");
  }

  private static void assertTransition(String from, String rawText, String to) throws Exception {
    assertTransition(from, rawText, to, ANY_SLICES);
  }

  private static void assertTransition(
      String from, String rawText, String to, int numSlices) throws Exception {
    SlicedRawTextNode node;
    try {
      node = RawTextContextUpdater.processRawText(
          new RawTextNode(0, rawText, SourceLocation.UNKNOWN), parseContext(from));
    } catch (SoyAutoescapeException e) {
      if (!to.equals("ERROR")) {
        throw new AssertionError("Expected context (" + to + ") but got an exception", e);
      } else {
        return; // Good!
      }
    }
    // Assert against the toString() for simpler test authoring -- if a developer misspells the
    // "to" context, they'll see a useful string-based diff.
    assertWithMessage(rawText)
        .that(node.getEndContext().toString())
        .isEqualTo("(Context " + to + ")");
    if (numSlices != ANY_SLICES) {
      assertWithMessage(rawText).that(node.getSlices().size()).isEqualTo(numSlices);
    }
  }

  private static Context parseContext(String text) {
    Queue<String> parts = Lists.newLinkedList(Arrays.asList(text.split(" ")));
    Context.Builder builder = Context.HTML_PCDATA.toBuilder();
    builder.withState(Context.State.valueOf(parts.remove()));
    if (!parts.isEmpty()) {
      try {
        builder.withElType(Context.ElementType.valueOf(parts.element()));
        parts.remove();
      } catch (IllegalArgumentException ex) {
        // OK
      }
      if (!parts.isEmpty()) {
        try {
          builder.withAttrType(Context.AttributeType.valueOf(parts.element()));
          parts.remove();
        } catch (IllegalArgumentException ex) {
          // OK
        }
        if (!parts.isEmpty()) {
          try {
            builder.withDelimType(Context.AttributeEndDelimiter.valueOf(parts.element()));
            parts.remove();
          } catch (IllegalArgumentException ex) {
            // OK
          }
          if (!parts.isEmpty()) {
            try {
              builder.withSlashType(Context.JsFollowingSlash.valueOf(parts.element()));
              parts.remove();
            } catch (IllegalArgumentException ex) {
              // OK
            }
            if (!parts.isEmpty()) {
              try {
                builder.withUriPart(Context.UriPart.valueOf(parts.element()));
                parts.remove();
              } catch (IllegalArgumentException ex) {
                // OK
              }
              if (!parts.isEmpty()) {
                String part = parts.element();
                String prefix = "templateNestDepth=";
                if (part.startsWith(prefix)) {
                  try {
                    builder.withTemplateNestDepth(
                        Integer.parseInt(part.substring(prefix.length())));
                    parts.remove();
                  } catch (NumberFormatException ex) {
                    // OK
                  }
                }
              }
            }
          }
        }
      }
    }
    assertWithMessage("Got [" + text + "]").that(parts).isEmpty();
    return builder.build();
  }
}
