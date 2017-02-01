/*
 * Copyright 2012 Google Inc.
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

package com.google.template.soy.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.html.types.SafeHtml;
import com.google.common.html.types.SafeHtmlBuilder;
import com.google.common.html.types.SafeHtmlProto;
import com.google.common.html.types.SafeHtmls;
import com.google.common.html.types.SafeScript;
import com.google.common.html.types.SafeScriptProto;
import com.google.common.html.types.SafeScripts;
import com.google.common.html.types.SafeStyleSheet;
import com.google.common.html.types.SafeStyleSheetProto;
import com.google.common.html.types.SafeStyleSheets;
import com.google.common.html.types.SafeUrl;
import com.google.common.html.types.SafeUrlProto;
import com.google.common.html.types.SafeUrls;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for SanitizedContents utility class.
 *
 */
@RunWith(JUnit4.class)
public class SanitizedContentsTest {

  @Test
  public void testUnsanitizedText() {
    assertEquals(
        SanitizedContent.create("Hello World", ContentKind.TEXT, null),
        SanitizedContents.unsanitizedText("Hello World"));
  }

  @Test
  public void testConcatCombinesHtml() throws Exception {
    String text1 = "one";
    String text2 = "two";
    String text3 = "three";

    SanitizedContent content1 = SanitizedContent.create(text1, ContentKind.HTML, null);
    SanitizedContent content2 = SanitizedContent.create(text2, ContentKind.HTML, null);
    SanitizedContent content3 = SanitizedContent.create(text3, ContentKind.HTML, null);

    assertEquals(
        SanitizedContent.create(text1 + text2 + text3, ContentKind.HTML, null),
        SanitizedContents.concatHtml(content1, content2, content3));
  }

  @Test
  public void testConcatReturnsEmpty() throws Exception {
    assertEquals(
        SanitizedContent.create("", ContentKind.HTML, Dir.NEUTRAL), SanitizedContents.concatHtml());
  }

  @Test
  public void testConcatThrowsExceptionOnDifferentNonHtml() throws Exception {
    try {
      SanitizedContents.concatHtml(
          SanitizedContents.emptyString(ContentKind.HTML),
          SanitizedContents.emptyString(ContentKind.CSS));
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("Can only concat HTML", e.getMessage());
    }
  }

  @Test
  public void testConcatCombinesHtmlDir() throws Exception {
    SanitizedContent EMPTY_HTML = SanitizedContents.emptyString(ContentKind.HTML);
    SanitizedContent LTR_HTML = SanitizedContent.create(".", ContentKind.HTML, Dir.LTR);
    SanitizedContent RTL_HTML = SanitizedContent.create(".", ContentKind.HTML, Dir.RTL);
    SanitizedContent NEUTRAL_HTML = SanitizedContent.create(".", ContentKind.HTML, Dir.NEUTRAL);
    SanitizedContent UNKNOWN_DIR_HTML = SanitizedContent.create(".", ContentKind.HTML, null);

    // empty -> neutral
    assertEquals(Dir.NEUTRAL, SanitizedContents.concatHtml(EMPTY_HTML).getContentDirection());

    // x -> x
    assertEquals(Dir.LTR, SanitizedContents.concatHtml(LTR_HTML).getContentDirection());
    assertEquals(Dir.RTL, SanitizedContents.concatHtml(RTL_HTML).getContentDirection());
    assertEquals(Dir.NEUTRAL, SanitizedContents.concatHtml(NEUTRAL_HTML).getContentDirection());
    assertEquals(null, SanitizedContents.concatHtml(UNKNOWN_DIR_HTML).getContentDirection());

    // x + unknown -> unknown
    assertNull(SanitizedContents.concatHtml(LTR_HTML, UNKNOWN_DIR_HTML).getContentDirection());
    assertNull(SanitizedContents.concatHtml(UNKNOWN_DIR_HTML, LTR_HTML).getContentDirection());
    assertNull(SanitizedContents.concatHtml(RTL_HTML, UNKNOWN_DIR_HTML).getContentDirection());
    assertNull(SanitizedContents.concatHtml(UNKNOWN_DIR_HTML, RTL_HTML).getContentDirection());
    assertNull(SanitizedContents.concatHtml(NEUTRAL_HTML, UNKNOWN_DIR_HTML).getContentDirection());
    assertNull(SanitizedContents.concatHtml(UNKNOWN_DIR_HTML, NEUTRAL_HTML).getContentDirection());
    assertNull(
        SanitizedContents.concatHtml(UNKNOWN_DIR_HTML, UNKNOWN_DIR_HTML).getContentDirection());

    // x + neutral -> x
    assertEquals(
        Dir.LTR, SanitizedContents.concatHtml(LTR_HTML, NEUTRAL_HTML).getContentDirection());
    assertEquals(
        Dir.LTR, SanitizedContents.concatHtml(NEUTRAL_HTML, LTR_HTML).getContentDirection());
    assertEquals(
        Dir.RTL, SanitizedContents.concatHtml(RTL_HTML, NEUTRAL_HTML).getContentDirection());
    assertEquals(
        Dir.RTL, SanitizedContents.concatHtml(NEUTRAL_HTML, RTL_HTML).getContentDirection());
    assertEquals(
        Dir.NEUTRAL,
        SanitizedContents.concatHtml(NEUTRAL_HTML, NEUTRAL_HTML).getContentDirection());

    // x + x -> x
    assertEquals(Dir.LTR, SanitizedContents.concatHtml(LTR_HTML, LTR_HTML).getContentDirection());
    assertEquals(Dir.RTL, SanitizedContents.concatHtml(RTL_HTML, RTL_HTML).getContentDirection());

    // LTR + RTL -> unknown
    assertNull(SanitizedContents.concatHtml(LTR_HTML, RTL_HTML).getContentDirection());
    assertNull(SanitizedContents.concatHtml(LTR_HTML, RTL_HTML).getContentDirection());
  }

  private void assertResourceNameValid(boolean valid, String resourceName, ContentKind kind) {
    try {
      SanitizedContents.pretendValidateResource(resourceName, kind);
      assertTrue("No exception was thrown, but wasn't expected to be valid.", valid);
    } catch (IllegalArgumentException e) {
      assertFalse("Exception was thrown, but was expected to be valid.", valid);
    }
  }

  @Test
  public void testPretendValidateResource() {
    // Correct resources.
    assertResourceNameValid(true, "test.js", ContentKind.JS);
    assertResourceNameValid(true, "/test/foo.bar.js", ContentKind.JS);
    assertResourceNameValid(true, "test.html", ContentKind.HTML);
    assertResourceNameValid(true, "test.svg", ContentKind.HTML);
    assertResourceNameValid(true, "test.css", ContentKind.CSS);

    // Wrong resource kind.
    assertResourceNameValid(false, "test.css", ContentKind.HTML);
    assertResourceNameValid(false, "test.html", ContentKind.JS);
    assertResourceNameValid(false, "test.js", ContentKind.CSS);

    // No file extensions supported for these kinds.
    assertResourceNameValid(false, "test.attributes", ContentKind.ATTRIBUTES);

    // Missing extension entirely.
    assertResourceNameValid(false, "test", ContentKind.JS);
  }

  @Test
  public void testGetDefaultDir() {
    assertEquals(Dir.LTR, SanitizedContents.getDefaultDir(ContentKind.JS));
    assertEquals(Dir.LTR, SanitizedContents.getDefaultDir(ContentKind.CSS));
    assertEquals(Dir.LTR, SanitizedContents.getDefaultDir(ContentKind.ATTRIBUTES));
    assertEquals(Dir.LTR, SanitizedContents.getDefaultDir(ContentKind.URI));

    assertNull(SanitizedContents.getDefaultDir(ContentKind.TEXT));
    assertNull(SanitizedContents.getDefaultDir(ContentKind.HTML));
  }

  @Test
  public void testConstantUri() {
    // Passing case. We actually can't test a failing case because it won't compile.
    SanitizedContent uri = SanitizedContents.constantUri("itms://blahblah");
    assertEquals("itms://blahblah", uri.getContent());
    assertEquals(ContentKind.URI, uri.getContentKind());
    assertEquals(SanitizedContents.getDefaultDir(ContentKind.URI), uri.getContentDirection());
  }

  @Test
  public void testCommonSafeHtmlTypeConversions() {
    final String helloWorldHtml = "Hello <em>World</em>";
    final SafeHtml safeHtml =
        SafeHtmls.concat(
            SafeHtmls.htmlEscape("Hello "),
            new SafeHtmlBuilder("em").escapeAndAppendContent("World").build());
    final SanitizedContent sanitizedHtml = SanitizedContents.fromSafeHtml(safeHtml);
    assertEquals(ContentKind.HTML, sanitizedHtml.getContentKind());
    assertEquals(helloWorldHtml, sanitizedHtml.getContent());
    assertEquals(safeHtml, sanitizedHtml.toSafeHtml());

    // Proto conversions.
    final SafeHtmlProto safeHtmlProto = sanitizedHtml.toSafeHtmlProto();
    assertEquals(safeHtml, SafeHtmls.fromProto(safeHtmlProto));
    assertEquals(helloWorldHtml, SanitizedContents.fromSafeHtmlProto(safeHtmlProto).getContent());
  }

  @Test
  public void testCommonSafeScriptTypeConversions() {
    final String testScript = "window.alert('hello');";
    final SafeScript safeScript = SafeScripts.fromConstant(testScript);
    final SanitizedContent sanitizedScript = SanitizedContents.fromSafeScript(safeScript);
    assertEquals(ContentKind.JS, sanitizedScript.getContentKind());
    assertEquals(testScript, sanitizedScript.getContent());
    assertEquals(safeScript.getSafeScriptString(), sanitizedScript.getContent());

    // Proto conversions.
    final SafeScriptProto safeScriptProto = SafeScripts.toProto(safeScript);
    assertEquals(safeScript, SafeScripts.fromProto(safeScriptProto));
    assertEquals(testScript, SanitizedContents.fromSafeScriptProto(safeScriptProto).getContent());
  }

  @Test
  public void testCommonSafeStyleSheetTypeConversions() {
    final String testCss = "div { display: none; }";
    final SafeStyleSheet safeCss = SafeStyleSheets.fromConstant(testCss);
    final SanitizedContent sanitizedCss = SanitizedContents.fromSafeStyleSheet(safeCss);
    assertEquals(ContentKind.CSS, sanitizedCss.getContentKind());
    assertEquals(testCss, sanitizedCss.getContent());
    assertEquals(safeCss, sanitizedCss.toSafeStyleSheet());

    // Proto conversions.
    final SafeStyleSheetProto safeCssProto = sanitizedCss.toSafeStyleSheetProto();
    assertEquals(safeCss, SafeStyleSheets.fromProto(safeCssProto));
    assertEquals(testCss, SanitizedContents.fromSafeStyleSheetProto(safeCssProto).getContent());
  }

  @Test
  public void testCommonSafeUrlTypeConversions() {
    final String testUrl = "http://blahblah";
    final SanitizedContent sanitizedConstantUri = SanitizedContents.constantUri(testUrl);
    final SafeUrl safeUrl = SafeUrls.fromConstant(testUrl);

    final SanitizedContent sanitizedUrl = SanitizedContents.fromSafeUrl(safeUrl);
    assertEquals(ContentKind.URI, sanitizedUrl.getContentKind());
    assertEquals(testUrl, sanitizedUrl.getContent());
    assertEquals(sanitizedConstantUri, sanitizedUrl);

    // Proto conversions.
    final SafeUrlProto safeUrlProto = SafeUrls.toProto(safeUrl);
    final SanitizedContent sanitizedUrlProto = SanitizedContents.fromSafeUrlProto(safeUrlProto);
    assertEquals(testUrl, sanitizedUrlProto.getContent());
    assertEquals(sanitizedConstantUri, sanitizedUrlProto);
  }

  @Test
  public void testWrongContentKindThrows_html() {
    final SanitizedContent notHtml =
        UnsafeSanitizedContentOrdainer.ordainAsSafe("not HTML", ContentKind.CSS);
    try {
      notHtml.toSafeHtml();
      fail("Should have thrown on SanitizedContent of kind other than HTML");
    } catch (IllegalStateException expected) {
    }
    try {
      notHtml.toSafeHtmlProto();
      fail("Should have thrown on SanitizedContent of kind other than HTML");
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testWrongContentKindThrows_script() {
    final SanitizedContent notJs =
        UnsafeSanitizedContentOrdainer.ordainAsSafe("not JS", ContentKind.URI);
    try {
      notJs.toSafeScriptProto();
      fail("Should have thrown on SanitizedContent of kind other than JS");
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testWrongContentKindThrows_css() {
    final SanitizedContent notCss =
        UnsafeSanitizedContentOrdainer.ordainAsSafe("not CSS", ContentKind.HTML);
    try {
      notCss.toSafeStyleProto();
      fail("Should have thrown on SanitizedContent of kind other than CSS");
    } catch (IllegalStateException expected) {
    }
    try {
      notCss.toSafeStyleSheet();
      fail("Should have thrown on SanitizedContent of kind other than CSS");
    } catch (IllegalStateException expected) {
    }
    try {
      notCss.toSafeStyleSheetProto();
      fail("Should have thrown on SanitizedContent of kind other than CSS");
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testWrongContentKindThrows_uri() {
    final SanitizedContent notUri =
        UnsafeSanitizedContentOrdainer.ordainAsSafe("not URI", ContentKind.HTML);
    try {
      notUri.toSafeUrlProto();
      fail("Should have thrown on SanitizedContent of kind other than URI");
    } catch (IllegalStateException expected) {
    }
    try {
      notUri.toTrustedResourceUrlProto();
      fail("Should have thrown on SanitizedContent of kind other than URI");
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testInvalidStyleSheetContentThrows() {
    for (String css :
        new String[] {
          "display: none;", "{ display: none; }", "/* a comment */", "@import url('test.css');"
        }) {
      final SanitizedContent cssStyle =
          UnsafeSanitizedContentOrdainer.ordainAsSafe(css, ContentKind.CSS);
      try {
        cssStyle.toSafeStyleSheet();
        fail("Should have thrown on CSS SanitizedContent with invalid stylesheet:" + css);
      } catch (IllegalStateException expected) {
      }
      try {
        cssStyle.toSafeStyleSheetProto();
        fail("Should have thrown on CSS SanitizedContent with invalid stylesheet:" + css);
      } catch (IllegalStateException expected) {
      }
    }
  }
}
