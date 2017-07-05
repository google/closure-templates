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
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.soytree.RawTextNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RawTextContextUpdaterTest {
  // The letter 'M' repeated 1500 times.
  private static final String M1500 = Strings.repeat("M", 1500);

  @Test
  public void testCssComment() throws Exception {
    assertTransition("CSS_COMMENT", "", "CSS_COMMENT");
    assertTransition("CSS_COMMENT", "\r\n\n\r", "CSS_COMMENT");
    assertTransition("CSS_COMMENT", " * /", "CSS_COMMENT");
    assertTransition("CSS_COMMENT", " */", "CSS");
    assertTransition("CSS_COMMENT", "**/", "CSS");
    assertTransition("CSS_COMMENT", "\\*/", "CSS");
    assertTransition(
        "CSS_COMMENT NORMAL STYLE SPACE_OR_TAG_END", "*/", "CSS NORMAL STYLE SPACE_OR_TAG_END");
  }

  @Test
  public void testCssDqString() throws Exception {
    assertTransition("CSS_DQ_STRING", "", "CSS_DQ_STRING");
    assertTransition("CSS_DQ_STRING", "Hello, World!", "CSS_DQ_STRING");
    assertTransition("CSS_DQ_STRING", "Don't", "CSS_DQ_STRING");
    assertTransition("CSS_DQ_STRING", "\"", "CSS");
    assertTransition("CSS_DQ_STRING", "\\22", "CSS_DQ_STRING");
    assertTransition("CSS_DQ_STRING", "\\22 ", "CSS_DQ_STRING");
    assertTransition("CSS_DQ_STRING", "\\27", "CSS_DQ_STRING");
    assertTransition("CSS_DQ_STRING", "\r", "ERROR");
    assertTransition("CSS_DQ_STRING", "\n", "ERROR");
  }

  @Test
  public void testCssSqString() throws Exception {
    assertTransition("CSS_SQ_STRING", "", "CSS_SQ_STRING");
    assertTransition("CSS_SQ_STRING", "Hello, World!", "CSS_SQ_STRING");
    assertTransition("CSS_SQ_STRING", "M. \"The Greatest!\" Ali", "CSS_SQ_STRING");
    assertTransition("CSS_SQ_STRING", "'", "CSS");
    assertTransition("CSS_SQ_STRING", "\\22", "CSS_SQ_STRING");
    assertTransition("CSS_SQ_STRING", "\\22 ", "CSS_SQ_STRING");
    assertTransition("CSS_SQ_STRING", "\\27", "CSS_SQ_STRING");
    assertTransition("CSS_SQ_STRING", "\r", "ERROR");
    assertTransition("CSS_SQ_STRING", "\n", "ERROR");
    assertTransition(
        "CSS_SQ_STRING NORMAL STYLE SPACE_OR_TAG_END", "'", "CSS NORMAL STYLE SPACE_OR_TAG_END");
  }

  @Test
  public void testCssUri() throws Exception {
    assertTransition("CSS_URI START NORMAL", "", "CSS_URI START NORMAL");
    assertTransition("CSS_URI START NORMAL", "/search?q=cute+bunnies", "CSS_URI QUERY NORMAL");
    assertTransition("CSS_URI START NORMAL", "#anchor)", "CSS");
    assertTransition("CSS_URI START NORMAL", "#anchor )", "CSS");
    assertTransition("CSS_URI START NORMAL", "/do+not+panic", "CSS_URI AUTHORITY_OR_PATH NORMAL");
    assertTransition(
        "CSS_SQ_URI START NORMAL", "/don%27t+panic", "CSS_SQ_URI AUTHORITY_OR_PATH NORMAL");
    assertTransition(
        "CSS_SQ_URI START NORMAL",
        "Muhammed+\"The+Greatest!\"+Ali",
        "CSS_SQ_URI MAYBE_SCHEME NORMAL");
    assertTransition(
        "CSS_SQ_URI START NORMAL", "(/don%27t+panic)", "CSS_SQ_URI AUTHORITY_OR_PATH NORMAL");
    assertTransition(
        "CSS_DQ_URI START NORMAL",
        "Muhammed+%22The+Greatest!%22+Ali",
        "CSS_DQ_URI MAYBE_SCHEME NORMAL");
    assertTransition(
        "CSS_DQ_URI START NORMAL", "/don't+panic", "CSS_DQ_URI AUTHORITY_OR_PATH NORMAL");
    assertTransition("CSS_SQ_URI START NORMAL", "#foo'", "CSS");
    assertTransition(
        "CSS_URI NORMAL STYLE SPACE_OR_TAG_END START NORMAL",
        ")",
        "CSS NORMAL STYLE SPACE_OR_TAG_END");
    assertTransition(
        "CSS_DQ_URI NORMAL STYLE SINGLE_QUOTE AUTHORITY_OR_PATH NORMAL",
        "\"",
        "CSS NORMAL STYLE SINGLE_QUOTE");
    assertTransition(
        "CSS_SQ_URI NORMAL STYLE DOUBLE_QUOTE FRAGMENT NORMAL",
        "x'",
        "CSS NORMAL STYLE DOUBLE_QUOTE");
    assertTransition(
        "CSS_SQ_URI NORMAL STYLE DOUBLE_QUOTE FRAGMENT NORMAL",
        "#x'",
        "CSS NORMAL STYLE DOUBLE_QUOTE");
    assertTransition("CSS", "url(", "CSS_URI START NORMAL");
    assertTransition("CSS", "url(/search?q=", "CSS_URI QUERY NORMAL");
    assertTransition("CSS", "url(  ", "CSS_URI START NORMAL");
    assertTransition("CSS", "url('", "CSS_SQ_URI START NORMAL");
    assertTransition("CSS", "url('//", "CSS_SQ_URI AUTHORITY_OR_PATH NORMAL");
    assertTransition("CSS", "url('/search?q=", "CSS_SQ_URI QUERY NORMAL");
    assertTransition("CSS", "url(\"", "CSS_DQ_URI START NORMAL");
    assertTransition("CSS", "url(\"/search?q=", "CSS_DQ_URI QUERY NORMAL");
    assertTransition("CSS", "url(\"/foo#bar", "CSS_DQ_URI FRAGMENT NORMAL");

    // TODO(gboyer): We may want to handle CSS comments, but because Soy already eliminates
    // /*...*/, we will assume this will be exceedingly rare, and can only result in getting
    // the less-permissive NORMAL state.
    assertTransition("CSS", "background-image:url('", "CSS_SQ_URI START MEDIA");
    assertTransition("CSS", "background-image:url('/search?q=", "CSS_SQ_URI QUERY MEDIA");
    assertTransition("CSS", "content:url('/search?q=", "CSS_SQ_URI QUERY MEDIA");
    assertTransition("CSS", "backgrounD-imagE:uRL('/search?q=", "CSS_SQ_URI QUERY MEDIA");
    assertTransition("CSS", "{ background:url(\"/search?q=", "CSS_DQ_URI QUERY MEDIA");
    assertTransition("CSS", " ;\t \nbackground \t\n : \t \nurl(", "CSS_URI START MEDIA");
    assertTransition("CSS", "{cursor:url(/search?q=", "CSS_URI QUERY MEDIA");
    assertTransition("CSS", "{list-style:url(/search?q=", "CSS_URI QUERY MEDIA");
    assertTransition("CSS", "{list-style-image:url(/search?q=", "CSS_URI QUERY MEDIA");
    // These are not image URLs.
    assertTransition("CSS", "{;src:url(/search?q=", "CSS_URI QUERY NORMAL");
    assertTransition("CSS", "not-background:url(/search?q=", "CSS_URI QUERY NORMAL");
    assertTransition("CSS", "{foo;not-background:url(/search?q=", "CSS_URI QUERY NORMAL");
    assertTransition("CSS", "{list-style-zmage:url(/search?q=", "CSS_URI QUERY NORMAL");
  }

  @Test
  public void testJsBeforeRegex() throws Exception {
    assertTransition("JS REGEX", "", "JS REGEX");
    assertTransition("JS REGEX", "/*", "JS_BLOCK_COMMENT REGEX");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END REGEX",
        "/*",
        "JS_BLOCK_COMMENT NORMAL SCRIPT SPACE_OR_TAG_END REGEX");
    assertTransition("JS REGEX", "//", "JS_LINE_COMMENT REGEX");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END REGEX",
        "//",
        "JS_LINE_COMMENT NORMAL SCRIPT SPACE_OR_TAG_END REGEX");
    assertTransition("JS REGEX", "'", "JS_SQ_STRING");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END REGEX",
        "'",
        "JS_SQ_STRING NORMAL SCRIPT SPACE_OR_TAG_END");
    assertTransition("JS REGEX", "\"", "JS_DQ_STRING");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END REGEX",
        "\"",
        "JS_DQ_STRING NORMAL SCRIPT SPACE_OR_TAG_END");
    assertTransition("JS REGEX", "42", "JS DIV_OP");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END REGEX",
        "42",
        "JS NORMAL SCRIPT SPACE_OR_TAG_END DIV_OP");
    assertTransition("JS REGEX", "0.", "JS DIV_OP");
    assertTransition("JS REGEX", "x", "JS DIV_OP");
    assertTransition("JS REGEX", "-", "JS REGEX");
    assertTransition("JS REGEX", "--", "JS DIV_OP");
    assertTransition("JS REGEX", " \t \n ", "JS REGEX");
    assertTransition("JS REGEX", ")", "JS DIV_OP");
    assertTransition("JS REGEX", "/", "JS_REGEX");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END REGEX", "/", "JS_REGEX NORMAL SCRIPT SPACE_OR_TAG_END");
    assertTransition("JS REGEX", "/[xy]/", "JS DIV_OP");
  }

  @Test
  public void testJsBeforeDivOp() throws Exception {
    assertTransition("JS DIV_OP", "", "JS DIV_OP");
    assertTransition("JS DIV_OP", "/*", "JS_BLOCK_COMMENT DIV_OP");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END DIV_OP",
        "/*",
        "JS_BLOCK_COMMENT NORMAL SCRIPT SPACE_OR_TAG_END DIV_OP");
    assertTransition("JS DIV_OP", "//", "JS_LINE_COMMENT DIV_OP");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END DIV_OP",
        "//",
        "JS_LINE_COMMENT NORMAL SCRIPT SPACE_OR_TAG_END DIV_OP");
    assertTransition("JS DIV_OP", "'", "JS_SQ_STRING");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END DIV_OP",
        "'",
        "JS_SQ_STRING NORMAL SCRIPT SPACE_OR_TAG_END");
    assertTransition("JS DIV_OP", "\"", "JS_DQ_STRING");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END DIV_OP",
        "\"",
        "JS_DQ_STRING NORMAL SCRIPT SPACE_OR_TAG_END");
    assertTransition("JS DIV_OP", "42", "JS DIV_OP");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END DIV_OP",
        "42",
        "JS NORMAL SCRIPT SPACE_OR_TAG_END DIV_OP");
    assertTransition("JS DIV_OP", "0.", "JS DIV_OP");
    assertTransition("JS DIV_OP", "x", "JS DIV_OP");
    assertTransition("JS DIV_OP", "-", "JS REGEX");
    assertTransition("JS DIV_OP", "--", "JS DIV_OP");
    assertTransition("JS DIV_OP", "  \n ", "JS DIV_OP");
    assertTransition("JS DIV_OP", ")", "JS DIV_OP");
    assertTransition("JS DIV_OP", "/", "JS REGEX");
    assertTransition(
        "JS NORMAL SCRIPT SPACE_OR_TAG_END DIV_OP", "/", "JS NORMAL SCRIPT SPACE_OR_TAG_END REGEX");
    assertTransition("JS DIV_OP", "/[xy]/", "JS REGEX");
  }

  @Test
  public void testJsLineComment() throws Exception {
    assertTransition("JS_LINE_COMMENT DIV_OP", "", "JS_LINE_COMMENT DIV_OP");
    assertTransition("JS_LINE_COMMENT DIV_OP", "*/", "JS_LINE_COMMENT DIV_OP");
    assertTransition("JS_LINE_COMMENT DIV_OP", "Hello, World!", "JS_LINE_COMMENT DIV_OP");
    assertTransition("JS_LINE_COMMENT DIV_OP", "\"'/", "JS_LINE_COMMENT DIV_OP");
    assertTransition("JS_LINE_COMMENT DIV_OP", "\n", "JS DIV_OP");
    assertTransition(
        "JS_LINE_COMMENT NORMAL SCRIPT DOUBLE_QUOTE DIV_OP",
        "\n",
        "JS NORMAL SCRIPT DOUBLE_QUOTE DIV_OP");
    assertTransition("JS_LINE_COMMENT REGEX", "", "JS_LINE_COMMENT REGEX");
    assertTransition("JS_LINE_COMMENT REGEX", "*/", "JS_LINE_COMMENT REGEX");
    assertTransition("JS_LINE_COMMENT REGEX", "Hello, World!", "JS_LINE_COMMENT REGEX");
    assertTransition("JS_LINE_COMMENT REGEX", "\"'/", "JS_LINE_COMMENT REGEX");
    assertTransition("JS_LINE_COMMENT REGEX", "\n", "JS REGEX");
  }

  @Test
  public void testJsBlockComment() throws Exception {
    assertTransition("JS_BLOCK_COMMENT DIV_OP", "", "JS_BLOCK_COMMENT DIV_OP");
    assertTransition("JS_BLOCK_COMMENT DIV_OP", "\n", "JS_BLOCK_COMMENT DIV_OP");
    assertTransition("JS_BLOCK_COMMENT DIV_OP", "Hello, World!", "JS_BLOCK_COMMENT DIV_OP");
    assertTransition("JS_BLOCK_COMMENT DIV_OP", "\"'/", "JS_BLOCK_COMMENT DIV_OP");
    assertTransition("JS_BLOCK_COMMENT DIV_OP", "*/", "JS DIV_OP");
    assertTransition(
        "JS_BLOCK_COMMENT NORMAL SCRIPT DOUBLE_QUOTE DIV_OP",
        "*/",
        "JS NORMAL SCRIPT DOUBLE_QUOTE DIV_OP");
    assertTransition("JS_BLOCK_COMMENT REGEX", "", "JS_BLOCK_COMMENT REGEX");
    assertTransition("JS_BLOCK_COMMENT REGEX", "\r\n", "JS_BLOCK_COMMENT REGEX");
    assertTransition("JS_BLOCK_COMMENT REGEX", "Hello, World!", "JS_BLOCK_COMMENT REGEX");
    assertTransition("JS_BLOCK_COMMENT REGEX", "\"'/", "JS_BLOCK_COMMENT REGEX");
    assertTransition("JS_BLOCK_COMMENT REGEX", "*/", "JS REGEX");
  }

  @Test
  public void testJsDqString() throws Exception {
    assertTransition("JS_DQ_STRING", "", "JS_DQ_STRING");
    assertTransition("JS_DQ_STRING", "Hello, World!", "JS_DQ_STRING");
    assertTransition("JS_DQ_STRING", M1500, "JS_DQ_STRING"); // Check for stack overflow
    assertTransition(
        "JS_DQ_STRING",
        Strings.repeat("foo \\t bar \\r baz \\\" quux", 10_000),
        "JS_DQ_STRING"); // Check for stack overflow
    assertTransition("JS_DQ_STRING", "\"", "JS DIV_OP");
    assertTransition(
        "JS_DQ_STRING NORMAL SCRIPT SINGLE_QUOTE",
        "Hello, World!",
        "JS_DQ_STRING NORMAL SCRIPT SINGLE_QUOTE");
    assertTransition(
        "JS_DQ_STRING NORMAL SCRIPT SINGLE_QUOTE", "\"", "JS NORMAL SCRIPT SINGLE_QUOTE DIV_OP");
    assertTransition("JS_DQ_STRING", "</p>", "JS_DQ_STRING");
  }

  @Test
  public void testJsSqString() throws Exception {
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
        "JS_SQ_STRING NORMAL SCRIPT DOUBLE_QUOTE",
        "Hello, World!",
        "JS_SQ_STRING NORMAL SCRIPT DOUBLE_QUOTE");
    assertTransition(
        "JS_SQ_STRING NORMAL SCRIPT DOUBLE_QUOTE", "'", "JS NORMAL SCRIPT DOUBLE_QUOTE DIV_OP");
    assertTransition("JS_SQ_STRING", "</s>", "JS_SQ_STRING");
  }

  @Test
  public void testJsRegex() throws Exception {
    assertTransition("JS_REGEX", "", "JS_REGEX");
    assertTransition("JS_REGEX", "Hello, World!", "JS_REGEX");
    assertTransition("JS_REGEX", "\\/*", "JS_REGEX");
    assertTransition("JS_REGEX", "[/*]", "JS_REGEX");
    assertTransition("JS_REGEX", "\"", "JS_REGEX");
    assertTransition("JS_REGEX", "\\x27", "JS_REGEX");
    assertTransition("JS_REGEX", "\\'", "JS_REGEX");
    assertTransition("JS_REGEX", "\r", "ERROR");
    assertTransition("JS_REGEX", "\\\rn", "ERROR"); // Line continuations not allowed in RegExps.
    assertTransition("JS_REGEX", "/", "JS DIV_OP");
    assertTransition(
        "JS_REGEX NORMAL SCRIPT DOUBLE_QUOTE",
        "Hello, World!",
        "JS_REGEX NORMAL SCRIPT DOUBLE_QUOTE");
    assertTransition(
        "JS_REGEX NORMAL SCRIPT DOUBLE_QUOTE", "/", "JS NORMAL SCRIPT DOUBLE_QUOTE DIV_OP");
  }

  @Test
  public void testUri() throws Exception {
    assertTransition("URI START NORMAL", "", "URI START NORMAL");
    assertTransition("URI START NORMAL", ".", "URI MAYBE_SCHEME NORMAL");
    assertTransition("URI START NORMAL", "/", "URI AUTHORITY_OR_PATH NORMAL");
    assertTransition("URI START NORMAL", "#", "URI FRAGMENT NORMAL");
    assertTransition("URI START NORMAL", "x", "URI MAYBE_SCHEME NORMAL");
    assertTransition("URI START NORMAL", "x:", "URI AUTHORITY_OR_PATH NORMAL");
    assertTransition("URI START NORMAL", "?", "URI QUERY NORMAL");
    assertTransition("URI START NORMAL", "&", "URI MAYBE_SCHEME NORMAL");
    assertTransition("URI START NORMAL", "=", "URI MAYBE_SCHEME NORMAL");
    assertTransition("URI START NORMAL", "javascript:", "URI DANGEROUS_SCHEME NORMAL");
    assertTransition("URI START NORMAL", "JavaScript:", "URI DANGEROUS_SCHEME NORMAL");
    assertTransition("URI START NORMAL", "not-javascript:", "URI AUTHORITY_OR_PATH NORMAL");
    assertTransition("URI START NORMAL", "data:", "URI DANGEROUS_SCHEME NORMAL");
    // NOTE(gboyer): Perhaps in media URIs we can consider allowing data if followed by an image
    // mime type, but it doesn't seem critical and is easy to work around.
    assertTransition("URI START MEDIA", "data:", "URI DANGEROUS_SCHEME MEDIA");
    assertTransition("URI START NORMAL", "bloB:", "URI DANGEROUS_SCHEME NORMAL");
    assertTransition("URI START NORMAL", "FiLeSystem:", "URI DANGEROUS_SCHEME NORMAL");

    assertTransition("URI QUERY NORMAL", "", "URI QUERY NORMAL");
    assertTransition("URI QUERY NORMAL", ".", "URI QUERY NORMAL");
    assertTransition("URI QUERY NORMAL", "/", "URI QUERY NORMAL");
    assertTransition("URI QUERY NORMAL", "#", "URI FRAGMENT NORMAL");
    assertTransition("URI QUERY NORMAL", "x", "URI QUERY NORMAL");
    assertTransition("URI QUERY NORMAL", "&", "URI QUERY NORMAL");
    assertTransition("URI QUERY NORMAL", "javascript:", "URI QUERY NORMAL");

    assertTransition("URI FRAGMENT NORMAL", "", "URI FRAGMENT NORMAL");
    assertTransition("URI FRAGMENT NORMAL", "?", "URI FRAGMENT NORMAL");
    assertTransition("URI FRAGMENT NORMAL", "javascript:", "URI FRAGMENT NORMAL");

    assertTransition("URI MAYBE_SCHEME NORMAL", ":", "URI AUTHORITY_OR_PATH NORMAL");
    // Schemes can have a dot.
    assertTransition("URI MAYBE_SCHEME NORMAL", ".", "URI MAYBE_SCHEME NORMAL");
    assertTransition("URI MAYBE_SCHEME NORMAL", "foo.bar", "URI MAYBE_SCHEME NORMAL");
    assertTransition("URI MAYBE_SCHEME NORMAL", "/", "URI AUTHORITY_OR_PATH NORMAL");
    assertTransition("URI MAYBE_SCHEME NORMAL", "/foo", "URI AUTHORITY_OR_PATH NORMAL");
    assertTransition("URI MAYBE_SCHEME NORMAL", "?", "URI QUERY NORMAL");
    assertTransition("URI MAYBE_SCHEME NORMAL", "blah?blah", "URI QUERY NORMAL");
    assertTransition("URI MAYBE_SCHEME NORMAL", "#", "URI FRAGMENT NORMAL");
    // If we have a hard-coded prefix, & and = don't do anything.
    assertTransition("URI MAYBE_SCHEME NORMAL", "=", "URI MAYBE_SCHEME NORMAL");
    assertTransition("URI MAYBE_SCHEME NORMAL", "&", "URI MAYBE_SCHEME NORMAL");
    // We don't care about schemes that end with javascript:.
    assertTransition("URI MAYBE_SCHEME NORMAL", "javascript:", "URI AUTHORITY_OR_PATH NORMAL");

    assertTransition("URI MAYBE_VARIABLE_SCHEME NORMAL", ".", "URI MAYBE_VARIABLE_SCHEME NORMAL");
    assertTransition(
        "URI MAYBE_VARIABLE_SCHEME NORMAL", "foo.bar", "URI MAYBE_VARIABLE_SCHEME NORMAL");
    assertTransition("URI MAYBE_VARIABLE_SCHEME NORMAL", "/", "URI AUTHORITY_OR_PATH NORMAL");
    assertTransition("URI MAYBE_VARIABLE_SCHEME NORMAL", "foo/bar", "URI AUTHORITY_OR_PATH NORMAL");
    assertTransition("URI MAYBE_VARIABLE_SCHEME NORMAL", "?", "URI QUERY NORMAL");
    assertTransition("URI MAYBE_VARIABLE_SCHEME NORMAL", "#", "URI FRAGMENT NORMAL");
    // If we have a variable prefix, we use & and = to heuristically transition.
    assertTransition("URI MAYBE_VARIABLE_SCHEME NORMAL", "=", "URI QUERY NORMAL");
    assertTransition("URI MAYBE_VARIABLE_SCHEME NORMAL", "&", "URI QUERY NORMAL");
    assertTransition("URI MAYBE_VARIABLE_SCHEME NORMAL", "bah&foo=", "URI QUERY NORMAL");
    assertTransition("URI MAYBE_VARIABLE_SCHEME NORMAL", ":", "ERROR");
    assertTransition("URI MAYBE_VARIABLE_SCHEME NORMAL", "javascript:", "ERROR");
  }

  @Test
  public void testText() throws Exception {
    // Plain text's only edge should be back to itself.
    assertTransition("TEXT", "", "TEXT");
    assertTransition("TEXT", "Hello, World!", "TEXT");
    assertTransition("TEXT", "<p", "TEXT");
    assertTransition("TEXT", "&D*(@*(#*(AW*D(J*#(J*(JS!!!''\"", "TEXT");
    assertTransition("TEXT", "<script>var x='", "TEXT");
    assertTransition("TEXT", "<a href='", "TEXT");
  }

  @Test
  public void testJsTemplateStringNesting() {
    assertTransition("JS", "`", "JS_TEMPLATE_LITERAL jsTemplateLiteralNestDepth=1");
    assertTransition("JS", "`${`${`", "JS_TEMPLATE_LITERAL jsTemplateLiteralNestDepth=3");
    assertTransition("JS", "`${`${`foo`}`}`", "JS REGEX");
    assertTransition(
        "JS", "`\\${\\`\\${\\`foo\\`}\\`}\\`", "JS_TEMPLATE_LITERAL jsTemplateLiteralNestDepth=1");
  }

  private static void assertTransition(String from, String rawText, String to) {
    Context endContext;
    try {
      endContext =
          RawTextContextUpdater.processRawText(
              new RawTextNode(0, rawText, SourceLocation.UNKNOWN), Context.parse(from));
    } catch (SoyAutoescapeException e) {
      if (!to.equals("ERROR")) {
        throw new AssertionError("Expected context (" + to + ") but got an exception", e);
      } else {
        return; // Good!
      }
    }
    // Assert against the toString() for simpler test authoring -- if a developer misspells the
    // "to" context, they'll see a useful string-based diff.
    String endContextString = endContext.toString();
    // remove the surrounding parens and leading 'Context'
    endContextString =
        endContextString.substring("(Context ".length(), endContextString.length() - 1);
    assertWithMessage(rawText).that(endContextString).isEqualTo(to);
  }
}
