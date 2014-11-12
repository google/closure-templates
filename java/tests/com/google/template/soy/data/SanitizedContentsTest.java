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

import com.google.template.soy.data.SanitizedContent.ContentKind;

import junit.framework.TestCase;


/**
 * Unit tests for SanitizedContents utility class.
 *
 */
public class SanitizedContentsTest extends TestCase {

  public void testUnsanitizedText() {
    assertEquals(
        new SanitizedContent("Hello World", ContentKind.TEXT, null),
        SanitizedContents.unsanitizedText("Hello World"));
  }

  public void testConcatCombinesHtml() throws Exception {
    String text1 = "one";
    String text2 = "two";
    String text3 = "three";

    SanitizedContent content1 = new SanitizedContent(text1, ContentKind.HTML, null);
    SanitizedContent content2 = new SanitizedContent(text2, ContentKind.HTML, null);
    SanitizedContent content3 = new SanitizedContent(text3, ContentKind.HTML, null);

    assertEquals(new SanitizedContent(text1 + text2 + text3, ContentKind.HTML, null),
        SanitizedContents.concatHtml(content1, content2, content3));
  }

  public void testConcatReturnsEmpty() throws Exception {
    assertEquals(new SanitizedContent("", ContentKind.HTML, Dir.NEUTRAL),
        SanitizedContents.concatHtml());
  }

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

  public void testConcatCombinesHtmlDir() throws Exception {
    SanitizedContent EMPTY_HTML = SanitizedContents.emptyString(ContentKind.HTML);
    SanitizedContent LTR_HTML = new SanitizedContent(".", ContentKind.HTML, Dir.LTR);
    SanitizedContent RTL_HTML = new SanitizedContent(".", ContentKind.HTML, Dir.RTL);
    SanitizedContent NEUTRAL_HTML = new SanitizedContent(".", ContentKind.HTML, Dir.NEUTRAL);
    SanitizedContent UNKNOWN_DIR_HTML = new SanitizedContent(".", ContentKind.HTML, null);

    // empty -> neutral
    assertEquals(Dir.NEUTRAL, SanitizedContents.concatHtml(EMPTY_HTML).getContentDirection());

    // x -> x
    assertEquals(Dir.LTR, SanitizedContents.concatHtml(LTR_HTML).getContentDirection());
    assertEquals(Dir.RTL, SanitizedContents.concatHtml(RTL_HTML).getContentDirection());
    assertEquals(Dir.NEUTRAL, SanitizedContents.concatHtml(NEUTRAL_HTML).getContentDirection());
    assertEquals(null, SanitizedContents.concatHtml(UNKNOWN_DIR_HTML).getContentDirection());

    // x + unknown -> unknown
    assertEquals(null,
        SanitizedContents.concatHtml(LTR_HTML, UNKNOWN_DIR_HTML).getContentDirection());
    assertEquals(null,
        SanitizedContents.concatHtml(UNKNOWN_DIR_HTML, LTR_HTML).getContentDirection());
    assertEquals(null,
        SanitizedContents.concatHtml(RTL_HTML, UNKNOWN_DIR_HTML).getContentDirection());
    assertEquals(null,
        SanitizedContents.concatHtml(UNKNOWN_DIR_HTML, RTL_HTML).getContentDirection());
    assertEquals(null,
        SanitizedContents.concatHtml(NEUTRAL_HTML, UNKNOWN_DIR_HTML).getContentDirection());
    assertEquals(null,
        SanitizedContents.concatHtml(UNKNOWN_DIR_HTML, NEUTRAL_HTML).getContentDirection());
    assertEquals(null,
        SanitizedContents.concatHtml(UNKNOWN_DIR_HTML, UNKNOWN_DIR_HTML).getContentDirection());

    // x + neutral -> x
    assertEquals(Dir.LTR,
        SanitizedContents.concatHtml(LTR_HTML, NEUTRAL_HTML).getContentDirection());
    assertEquals(Dir.LTR,
        SanitizedContents.concatHtml(NEUTRAL_HTML, LTR_HTML).getContentDirection());
    assertEquals(Dir.RTL,
        SanitizedContents.concatHtml(RTL_HTML, NEUTRAL_HTML).getContentDirection());
    assertEquals(Dir.RTL,
        SanitizedContents.concatHtml(NEUTRAL_HTML, RTL_HTML).getContentDirection());
    assertEquals(Dir.NEUTRAL,
        SanitizedContents.concatHtml(NEUTRAL_HTML, NEUTRAL_HTML).getContentDirection());

    // x + x -> x
    assertEquals(Dir.LTR, SanitizedContents.concatHtml(LTR_HTML, LTR_HTML).getContentDirection());
    assertEquals(Dir.RTL, SanitizedContents.concatHtml(RTL_HTML, RTL_HTML).getContentDirection());

    // LTR + RTL -> unknown
    assertEquals(null, SanitizedContents.concatHtml(LTR_HTML, RTL_HTML).getContentDirection());
    assertEquals(null, SanitizedContents.concatHtml(LTR_HTML, RTL_HTML).getContentDirection());
  }

  private void assertResourceNameValid(boolean valid, String resourceName, ContentKind kind) {
    try {
      SanitizedContents.pretendValidateResource(resourceName, kind);
      assertTrue("No exception was thrown, but wasn't expected to be valid.", valid);
    } catch (IllegalArgumentException e) {
      assertFalse("Exception was thrown, but was expected to be valid.", valid);
    }
  }

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

  public void testGetDefaultDir() {
    assertEquals(Dir.LTR, SanitizedContents.getDefaultDir(ContentKind.JS));
    assertEquals(Dir.LTR, SanitizedContents.getDefaultDir(ContentKind.CSS));
    assertEquals(Dir.LTR, SanitizedContents.getDefaultDir(ContentKind.ATTRIBUTES));
    assertEquals(Dir.LTR, SanitizedContents.getDefaultDir(ContentKind.URI));

    assertNull(SanitizedContents.getDefaultDir(ContentKind.TEXT));
    assertNull(SanitizedContents.getDefaultDir(ContentKind.HTML));
  }
}
