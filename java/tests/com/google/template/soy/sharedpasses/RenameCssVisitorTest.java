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

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;

import junit.framework.TestCase;

/**
 * Unit tests for RenameCssVisitor.
 *
 */
public final class RenameCssVisitorTest extends TestCase {


  private static final String TEST_FILE_CONTENT =
      "{namespace boo autoescape=\"deprecated-noncontextual\"}\n" +
      "\n" +
      "/** Test template. @param goo */\n" +
      "{template name=\".foo\"}\n" +
      "  <div class=\"{css AAA} {css $goo, AAA} {css BBB} {css $goo, BBB}\">\n" +
      "{/template}\n";


  public void testWithoutCssRenamingMap() {
    ErrorReporter boom = ExplodingErrorReporter.get();
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(TEST_FILE_CONTENT)
        .errorReporter(boom)
        .parse();
    TemplateNode template = (TemplateNode) SharedTestUtils.getNode(soyTree);

    // Before.
    assertThat(template.numChildren()).isEqualTo(9);
    CssNode cn1 = (CssNode) template.getChild(1);
    assertThat(cn1.getSelectorText()).isEqualTo("AAA");
    CssNode cn7 = (CssNode) template.getChild(7);
    assertThat(cn7.getComponentNameText()).isEqualTo("$goo");
    assertThat(cn7.getSelectorText()).isEqualTo("BBB");

    new RenameCssVisitor(null /* cssRenamingMap */, boom).exec(template);
    new CombineConsecutiveRawTextNodesVisitor(boom).exec(template);

    // After.
    assertThat(template.numChildren()).isEqualTo(5);
    RawTextNode rtn0 = (RawTextNode) template.getChild(0);
    assertThat(rtn0.getRawText()).isEqualTo("<div class=\"AAA ");
    PrintNode pn1 = (PrintNode) template.getChild(1);
    assertThat(pn1.getExprText()).isEqualTo("$goo");
    RawTextNode rtn2 = (RawTextNode) template.getChild(2);
    assertThat(rtn2.getRawText()).isEqualTo("-AAA BBB ");
    PrintNode pn3 = (PrintNode) template.getChild(3);
    assertThat(pn3.getExprText()).isEqualTo("$goo");
    RawTextNode rtn4 = (RawTextNode) template.getChild(4);
    assertThat(rtn4.getRawText()).isEqualTo("-BBB\">");
  }


  public void testWithCssRenamingMap() {
    ErrorReporter boom = ExplodingErrorReporter.get();
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(TEST_FILE_CONTENT)
        .errorReporter(boom)
        .parse();
    TemplateNode template = (TemplateNode) SharedTestUtils.getNode(soyTree);

    // Before.
    assertThat(template.numChildren()).isEqualTo(9);
    CssNode cn1 = (CssNode) template.getChild(1);
    assertThat(cn1.getSelectorText()).isEqualTo("AAA");
    CssNode cn7 = (CssNode) template.getChild(7);
    assertThat(cn7.getComponentNameText()).isEqualTo("$goo");
    assertThat(cn7.getSelectorText()).isEqualTo("BBB");

    // Use a CSS renaming map that only renames 'AAA'.
    SoyCssRenamingMap cssRenamingMap =
        new SoyCssRenamingMap() {
          @Override public String get(String key) {
            return key.equals("AAA") ? "XXX" : null;
          }
        };

    new RenameCssVisitor(cssRenamingMap, boom).exec(template);
    new CombineConsecutiveRawTextNodesVisitor(boom).exec(template);

    // After.
    assertThat(template.numChildren()).isEqualTo(5);
    RawTextNode rtn0 = (RawTextNode) template.getChild(0);
    assertThat(rtn0.getRawText()).isEqualTo("<div class=\"XXX ");
    PrintNode pn1 = (PrintNode) template.getChild(1);
    assertThat(pn1.getExprText()).isEqualTo("$goo");
    RawTextNode rtn2 = (RawTextNode) template.getChild(2);
    assertThat(rtn2.getRawText()).isEqualTo("-XXX BBB ");
    PrintNode pn3 = (PrintNode) template.getChild(3);
    assertThat(pn3.getExprText()).isEqualTo("$goo");
    RawTextNode rtn4 = (RawTextNode) template.getChild(4);
    assertThat(rtn4.getRawText()).isEqualTo("-BBB\">");
  }

}
