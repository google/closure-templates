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

import java.util.Arrays;
import java.util.Queue;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import junit.framework.TestCase;

public class RawTextContextUpdaterTest extends TestCase {

  public final void testPcdata() throws Exception {
    assertTransition("HTML_PCDATA", "", "HTML_PCDATA");
    assertTransition("HTML_PCDATA", "Hello, World!", "HTML_PCDATA");
    assertTransition("HTML_PCDATA", "Jad loves ponies <3 <3 <3 !!!", "HTML_PCDATA");
    assertTransition("HTML_PCDATA", "OMG! Ponies, Ponies, Ponies &lt;3", "HTML_PCDATA");
    // Entering a tag
    assertTransition("HTML_PCDATA", "<", "HTML_BEFORE_TAG_NAME");
    assertTransition("HTML_PCDATA", "Hello, <", "HTML_BEFORE_TAG_NAME");
    assertTransition("HTML_PCDATA", "<h", "HTML_TAG_NAME");
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
        "HTML_PCDATA", "<a href=mailto:", "URI NORMAL URI SPACE_OR_TAG_END PRE_QUERY");
    assertTransition(
        "HTML_PCDATA", "<input type=button value= onclick=",
        "HTML_BEFORE_ATTRIBUTE_VALUE NORMAL SCRIPT");
    assertTransition("HTML_PCDATA", "<input type=button value=>", "HTML_PCDATA");
  }

  public final void testBeforeTagName() throws Exception {
    assertTransition("HTML_BEFORE_TAG_NAME", "", "HTML_BEFORE_TAG_NAME");
    assertTransition("HTML_BEFORE_TAG_NAME", "h", "HTML_TAG_NAME");
    assertTransition("HTML_BEFORE_TAG_NAME", "svg:font-face id='x'", "HTML_TAG NORMAL");
    assertTransition("HTML_BEFORE_TAG_NAME", ">", "HTML_PCDATA");
    assertTransition("HTML_BEFORE_TAG_NAME", "><", "HTML_BEFORE_TAG_NAME");
  }

  public final void testTagName() throws Exception {
    assertTransition("HTML_TAG_NAME", "", "HTML_TAG_NAME");
    assertTransition("HTML_TAG_NAME", "1", "HTML_TAG_NAME");
    assertTransition("HTML_TAG_NAME", "-foo", "HTML_TAG_NAME");
    assertTransition("HTML_TAG_NAME", " id='x'", "HTML_TAG NORMAL");
    assertTransition("HTML_TAG_NAME", "\rid='x'", "HTML_TAG NORMAL");
    assertTransition("HTML_TAG_NAME", "\tid='x'", "HTML_TAG NORMAL");
    assertTransition("HTML_TAG_NAME", ">", "HTML_PCDATA");
    assertTransition("HTML_TAG_NAME", "/>", "HTML_PCDATA");
    assertTransition("HTML_TAG_NAME", " href=", "HTML_BEFORE_ATTRIBUTE_VALUE NORMAL URI");
    assertTransition("HTML_TAG_NAME", " href=\"", "URI NORMAL URI DOUBLE_QUOTE START");
    assertTransition("HTML_TAG_NAME", " href='", "URI NORMAL URI SINGLE_QUOTE START");
    assertTransition("HTML_TAG_NAME", " href=#", "URI NORMAL URI SPACE_OR_TAG_END FRAGMENT");
    assertTransition("HTML_TAG_NAME", " href=>", "HTML_PCDATA");
    assertTransition("HTML_TAG_NAME", " onclick=\"", "JS NORMAL SCRIPT DOUBLE_QUOTE REGEX");
    assertTransition("HTML_TAG_NAME", " style=\"", "CSS NORMAL STYLE DOUBLE_QUOTE");
    assertTransition(
        "HTML_TAG_NAME", " stylez=\"", "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT DOUBLE_QUOTE");
    assertTransition(
        "HTML_TAG_NAME", " title=\"", "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT DOUBLE_QUOTE");
    assertTransition("HTML_TAG_NAME", "=foo>", "ERROR");
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
    assertTransition("HTML_TAG NORMAL", " on", "HTML_ATTRIBUTE_NAME NORMAL SCRIPT");
    assertTransition("HTML_TAG NORMAL", " ONCLICK", "HTML_ATTRIBUTE_NAME NORMAL SCRIPT");
    assertTransition("HTML_TAG NORMAL", " style", "HTML_ATTRIBUTE_NAME NORMAL STYLE");
    assertTransition("HTML_TAG NORMAL", " HREF", "HTML_ATTRIBUTE_NAME NORMAL URI");
    assertTransition("HTML_TAG XMP", " title", "HTML_ATTRIBUTE_NAME XMP PLAIN_TEXT");
    assertTransition("HTML_TAG NORMAL", " checked ", "HTML_TAG NORMAL");
    assertTransition("HTML_TAG NORMAL", " xlink:href", "HTML_ATTRIBUTE_NAME NORMAL URI");
    assertTransition("HTML_TAG NORMAL", " g:url", "HTML_ATTRIBUTE_NAME NORMAL URI");
    assertTransition("HTML_TAG NORMAL", " g:iconUri", "HTML_ATTRIBUTE_NAME NORMAL URI");
    assertTransition("HTML_TAG NORMAL", " g:urlItem", "HTML_ATTRIBUTE_NAME NORMAL URI");
    assertTransition("HTML_TAG NORMAL", " g:hourly", "HTML_ATTRIBUTE_NAME NORMAL PLAIN_TEXT");
    assertTransition("HTML_TAG NORMAL", " xmlns", "HTML_ATTRIBUTE_NAME NORMAL URI");
    assertTransition("HTML_TAG NORMAL", " xmlns:foo", "HTML_ATTRIBUTE_NAME NORMAL URI");
    assertTransition("HTML_TAG NORMAL", " xmlnsxyz", "HTML_ATTRIBUTE_NAME NORMAL PLAIN_TEXT");
    assertTransition("HTML_TAG NORMAL", " svg:style='", "CSS NORMAL STYLE SINGLE_QUOTE");
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
        "URI NORMAL URI SPACE_OR_TAG_END PRE_QUERY");
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
    assertTransition("CSS", "url('//", "CSS_SQ_URI PRE_QUERY");
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
    assertTransition("CSS_URI START", "/do+not+panic", "CSS_URI PRE_QUERY");
    assertTransition("CSS_SQ_URI START", "/don%27t+panic", "CSS_SQ_URI PRE_QUERY");
    assertTransition("CSS_SQ_URI START", "Muhammed+\"The+Greatest!\"+Ali", "CSS_SQ_URI PRE_QUERY");
    assertTransition("CSS_SQ_URI START", "(/don%27t+panic)", "CSS_SQ_URI PRE_QUERY");
    assertTransition(
        "CSS_DQ_URI START", "Muhammed+%22The+Greatest!%22+Ali", "CSS_DQ_URI PRE_QUERY");
    assertTransition("CSS_DQ_URI START", "/don't+panic", "CSS_DQ_URI PRE_QUERY");
    assertTransition("CSS_SQ_URI START", "#foo'", "CSS");
    assertTransition(
        "CSS_URI NORMAL STYLE SPACE_OR_TAG_END START", ")", "CSS NORMAL STYLE SPACE_OR_TAG_END");
    assertTransition(
        "CSS_DQ_URI NORMAL STYLE SINGLE_QUOTE PRE_QUERY", "\"", "CSS NORMAL STYLE SINGLE_QUOTE");
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
    assertTransition("URI START", ".", "URI PRE_QUERY");
    assertTransition("URI START", "/", "URI PRE_QUERY");
    assertTransition("URI START", "#", "URI FRAGMENT");
    assertTransition("URI START", "x", "URI PRE_QUERY");
    assertTransition("URI START", "?", "URI QUERY");
    assertTransition("URI QUERY", "", "URI QUERY");
    assertTransition("URI QUERY", ".", "URI QUERY");
    assertTransition("URI QUERY", "/", "URI QUERY");
    assertTransition("URI QUERY", "#", "URI FRAGMENT");
    assertTransition("URI QUERY", "x", "URI QUERY");
    assertTransition("URI QUERY", "&", "URI QUERY");
    assertTransition("URI FRAGMENT", "", "URI FRAGMENT");
    assertTransition("URI FRAGMENT", "?", "URI FRAGMENT");
  }

  public final void testError() throws Exception {
    assertTransition("ERROR", "/*//'\"\r\n\f\n\rFoo", "ERROR");
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

  private static void assertTransition(String from, String rawText, String to) throws Exception {
    Context after = RawTextContextUpdater.processRawText(rawText, parseContext(from));
    assertEquals(rawText, parseContext(to), after);
  }

  private static Context parseContext(String text) {
    Queue<String> parts = Lists.newLinkedList(Arrays.asList(text.split(" ")));
    Context.State state = Context.State.valueOf(parts.remove());
    Context.ElementType el = Context.ElementType.NONE;
    Context.AttributeType attr = Context.AttributeType.NONE;
    Context.AttributeEndDelimiter delim = Context.AttributeEndDelimiter.NONE;
    Context.JsFollowingSlash slash = Context.JsFollowingSlash.NONE;
    Context.UriPart uriPart = Context.UriPart.NONE;
    if (!parts.isEmpty()) {
      try {
        el = Context.ElementType.valueOf(parts.element());
        parts.remove();
      } catch (IllegalArgumentException ex) {
        // OK
      }
      if (!parts.isEmpty()) {
        try {
          attr = Context.AttributeType.valueOf(parts.element());
          parts.remove();
        } catch (IllegalArgumentException ex) {
          // OK
        }
        if (!parts.isEmpty()) {
          try {
            delim = Context.AttributeEndDelimiter.valueOf(parts.element());
            parts.remove();
          } catch (IllegalArgumentException ex) {
            // OK
          }
          if (!parts.isEmpty()) {
            try {
              slash = Context.JsFollowingSlash.valueOf(parts.element());
              parts.remove();
            } catch (IllegalArgumentException ex) {
              // OK
            }
            if (!parts.isEmpty()) {
              try {
                uriPart = Context.UriPart.valueOf(parts.element());
                parts.remove();
              } catch (IllegalArgumentException ex) {
                // OK
              }
            }
          }
        }
      }
    }
    assertTrue(
        "Got [" + text + "] but didn't use [" + Joiner.on(' ').join(parts) + "]", parts.isEmpty());
    return new Context(state, el, attr, delim, slash, uriPart);
  }
}
