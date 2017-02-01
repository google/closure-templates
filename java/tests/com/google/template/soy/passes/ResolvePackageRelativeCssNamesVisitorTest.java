/*
 * Copyright 2014 Google Inc.
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

package com.google.template.soy.passes;

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.error.FormattingErrorReporter;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ResolvePackageRelativeCssNamesVisitor}. */
@RunWith(JUnit4.class)
public final class ResolvePackageRelativeCssNamesVisitorTest {

  @Test
  public void testBaseCssOnNamespace() {
    List<CssNode> cssNodes =
        compileTemplate(
            "{namespace boo cssbase=\"some.test.package\"}\n\n"
                + "/** Test template.*/\n"
                + "{template .foo}\n"
                + "  <div class=\"{css %AAA}\">\n"
                + "{/template}\n");

    assertThat(cssNodes.get(0).getSelectorText()).isEqualTo("someTestPackageAAA");
  }

  @Test
  public void testBaseCssOnTemplate() {
    List<CssNode> cssNodes =
        compileTemplate(
            "{namespace boo}\n\n"
                + "/** Test template.  */\n"
                + "{template .foo cssbase=\"some.test.package\"}\n"
                + "  <div class=\"{css %AAA}\">\n"
                + "{/template}\n");

    assertThat(cssNodes.get(0).getSelectorText()).isEqualTo("someTestPackageAAA");
  }

  @Test
  public void testRequireCssOnNamespace() {
    List<CssNode> cssNodes =
        compileTemplate(
            "{namespace boo requirecss=\"some.test.package,some.other.package\"}\n\n"
                + "/** Test template. */\n"
                + "{template .foo}\n"
                + "  <div class=\"{css %AAA}\">\n"
                + "{/template}\n");

    assertThat(cssNodes.get(0).getSelectorText()).isEqualTo("someTestPackageAAA");
  }

  @Test
  public void testUnprefixedNode() {
    List<CssNode> cssNodes =
        compileTemplate(
            "{namespace boo cssbase=\"some.test.package\"}\n\n"
                + "/** Test template. */\n"
                + "{template .foo}\n"
                + "  <div class=\"{css AAA}\">\n"
                + "{/template}\n");

    assertThat(cssNodes.get(0).getSelectorText()).isEqualTo("AAA");
  }

  @Test
  public void testMissingCssBase() {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    compileTemplate(
        "{namespace boo}\n\n"
            + "/** Test template. */\n"
            + "{template .foo}\n"
            + "  <div class=\"{css %AAA}\">\n"
            + "{/template}\n",
        errorReporter);
    assertThat(errorReporter.getErrorMessages()).hasSize(1);
    assertThat(errorReporter.getErrorMessages().get(0))
        .isEqualTo("No CSS package defined for package-relative class name '%AAA'");
  }

  @Test
  public void testWithComponentName() {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    compileTemplate(
        "{namespace boo}\n\n"
            + "/** Test template. */\n"
            + "{template .foo}\n"
            + "  {@param goo: string}\n"
            + "  <div class=\"{css $goo, %AAA}\">\n"
            + "{/template}\n",
        errorReporter);
    assertThat(errorReporter.getErrorMessages()).hasSize(2);
    assertThat(errorReporter.getErrorMessages().get(0))
        .isEqualTo("Package-relative class name '%AAA' cannot be used with component expression");
    assertThat(errorReporter.getErrorMessages().get(1))
        .isEqualTo("No CSS package defined for package-relative class name '%AAA'");
  }

  private static List<CssNode> compileTemplate(String templateText, ErrorReporter errorReporter) {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(templateText)
            .errorReporter(errorReporter)
            .parse()
            .fileSet();
    TemplateNode template = (TemplateNode) SharedTestUtils.getNode(soyTree);
    return SoyTreeUtils.getAllNodesOfType(template, CssNode.class);
  }

  private List<CssNode> compileTemplate(String templateText) {
    return compileTemplate(templateText, ExplodingErrorReporter.get());
  }
}
