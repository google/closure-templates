/*
 * Copyright 2011 Google Inc.
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

package com.google.template.soy.sharedpasses;

import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.internal.SharedTestUtils;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.TemplateNode;

import junit.framework.TestCase;


/**
 * Unit tests for RenameCssVisitor.
 *
 * @author Kai Huang
 */
public class RenameCssVisitorTest extends TestCase {


  private static final String TEST_FILE_CONTENT =
      "{namespace boo}\n" +
      "\n" +
      "/** Test template. @param goo */\n" +
      "{template name=\".foo\"}\n" +
      "  <div class=\"{css AAA} {css $goo, AAA} {css BBB} {css $goo, BBB}\">\n" +
      "{/template}\n";


  public void testWithoutCssRenamingMap() {

    TemplateNode template =
        (TemplateNode) SharedTestUtils.getNode(SharedTestUtils.parseSoyFiles(TEST_FILE_CONTENT));

    // Before.
    assertEquals(9, template.numChildren());
    CssNode cn1 = (CssNode) template.getChild(1);
    assertEquals("AAA", cn1.getSelectorText());
    CssNode cn7 = (CssNode) template.getChild(7);
    assertEquals("$goo", cn7.getComponentNameText());
    assertEquals("BBB", cn7.getSelectorText());

    (new RenameCssVisitor(null)).exec(template);
    (new CombineConsecutiveRawTextNodesVisitor()).exec(template);

    // After.
    assertEquals(5, template.numChildren());
    RawTextNode rtn0 = (RawTextNode) template.getChild(0);
    assertEquals("<div class=\"AAA ", rtn0.getRawText());
    PrintNode pn1 = (PrintNode) template.getChild(1);
    assertEquals("$goo", pn1.getExprText());
    RawTextNode rtn2 = (RawTextNode) template.getChild(2);
    assertEquals("-AAA BBB ", rtn2.getRawText());
    PrintNode pn3 = (PrintNode) template.getChild(3);
    assertEquals("$goo", pn3.getExprText());
    RawTextNode rtn4 = (RawTextNode) template.getChild(4);
    assertEquals("-BBB\">", rtn4.getRawText());
  }


  public void testWithCssRenamingMap() {

    TemplateNode template =
        (TemplateNode) SharedTestUtils.getNode(SharedTestUtils.parseSoyFiles(TEST_FILE_CONTENT));

    // Before.
    assertEquals(9, template.numChildren());
    CssNode cn1 = (CssNode) template.getChild(1);
    assertEquals("AAA", cn1.getSelectorText());
    CssNode cn7 = (CssNode) template.getChild(7);
    assertEquals("$goo", cn7.getComponentNameText());
    assertEquals("BBB", cn7.getSelectorText());

    // Use a CSS renaming map that only renames 'AAA'.
    SoyCssRenamingMap cssRenamingMap =
        new SoyCssRenamingMap() {
          @Override public String get(String key) {
            return key.equals("AAA") ? "XXX" : null;
          }
        };

    (new RenameCssVisitor(cssRenamingMap)).exec(template);
    (new CombineConsecutiveRawTextNodesVisitor()).exec(template);

    // After.
    assertEquals(5, template.numChildren());
    RawTextNode rtn0 = (RawTextNode) template.getChild(0);
    assertEquals("<div class=\"XXX ", rtn0.getRawText());
    PrintNode pn1 = (PrintNode) template.getChild(1);
    assertEquals("$goo", pn1.getExprText());
    RawTextNode rtn2 = (RawTextNode) template.getChild(2);
    assertEquals("-XXX BBB ", rtn2.getRawText());
    PrintNode pn3 = (PrintNode) template.getChild(3);
    assertEquals("$goo", pn3.getExprText());
    RawTextNode rtn4 = (RawTextNode) template.getChild(4);
    assertEquals("-BBB\">", rtn4.getRawText());
  }

}
