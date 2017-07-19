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

import com.google.common.collect.Iterables;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ResolvePackageRelativeCssNamesVisitor}. */
@RunWith(JUnit4.class)
public final class ResolvePackageRelativeCssNamesVisitorTest {

  @Test
  public void testBaseCssOnNamespace() {
    TemplateNode template =
        compileTemplate(
            "{namespace boo cssbase=\"some.test.package\"}\n\n"
                + "/** Test template.*/\n"
                + "{template .foo}\n"
                + "  <div class=\"{css('%AAA')}\">\n"
                + "{/template}\n");
    PrintNode printNode =
        Iterables.getOnlyElement(SoyTreeUtils.getAllNodesOfType(template, PrintNode.class));
    FunctionNode cssFn = (FunctionNode) printNode.getExpr().getRoot();
    assertThat(((StringNode) cssFn.getChild(0)).getValue()).isEqualTo("someTestPackageAAA");
  }

  @Test
  public void testBaseCssOnTemplate() {
    TemplateNode template =
        compileTemplate(
            "{namespace boo}\n\n"
                + "/** Test template.  */\n"
                + "{template .foo cssbase=\"some.test.package\"}\n"
                + "  <div class=\"{css('%AAA')}\">\n"
                + "{/template}\n");
    PrintNode printNode =
        Iterables.getOnlyElement(SoyTreeUtils.getAllNodesOfType(template, PrintNode.class));
    FunctionNode cssFn = (FunctionNode) printNode.getExpr().getRoot();
    assertThat(((StringNode) cssFn.getChild(0)).getValue()).isEqualTo("someTestPackageAAA");
  }

  @Test
  public void testRequireCssOnNamespace() {
    TemplateNode template =
        compileTemplate(
            "{namespace boo requirecss=\"some.test.package,some.other.package\"}\n\n"
                + "/** Test template. */\n"
                + "{template .foo}\n"
                + "  <div class=\"{css('%AAA')}\">\n"
                + "{/template}\n");
    PrintNode printNode =
        Iterables.getOnlyElement(SoyTreeUtils.getAllNodesOfType(template, PrintNode.class));
    FunctionNode cssFn = (FunctionNode) printNode.getExpr().getRoot();
    assertThat(((StringNode) cssFn.getChild(0)).getValue()).isEqualTo("someTestPackageAAA");
  }

  @Test
  public void testUnprefixedNode() {
    TemplateNode template =
        compileTemplate(
            "{namespace boo cssbase=\"some.test.package\"}\n\n"
                + "/** Test template. */\n"
                + "{template .foo}\n"
                + "  <div class=\"{css('AAA')}\">\n"
                + "{/template}\n");
    PrintNode printNode =
        Iterables.getOnlyElement(SoyTreeUtils.getAllNodesOfType(template, PrintNode.class));
    FunctionNode cssFn = (FunctionNode) printNode.getExpr().getRoot();
    assertThat(((StringNode) cssFn.getChild(0)).getValue()).isEqualTo("AAA");
  }

  @Test
  public void testMissingCssBase() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    compileTemplate(
        "{namespace boo}\n\n"
            + "/** Test template. */\n"
            + "{template .foo}\n"
            + "  <div class=\"{css('%AAA')}\">\n"
            + "{/template}\n",
        errorReporter);
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(errorReporter.getErrors().get(0).message())
        .isEqualTo("No CSS package defined for package-relative class name '%AAA'.");
  }

  @Test
  public void testWithComponentName() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    compileTemplate(
        "{namespace boo}\n\n"
            + "/** Test template. */\n"
            + "{template .foo}\n"
            + "  {@param goo: string}\n"
            + "  <div class=\"{css($goo, '%AAA')}\">\n"
            + "{/template}\n",
        errorReporter);
    assertThat(errorReporter.getErrors()).hasSize(2);
    assertThat(errorReporter.getErrors().get(0).message())
        .isEqualTo("Package-relative class name '%AAA' cannot be used with component expression.");
    assertThat(errorReporter.getErrors().get(1).message())
        .isEqualTo("No CSS package defined for package-relative class name '%AAA'.");
  }

  private static TemplateNode compileTemplate(String templateText) {
    return compileTemplate(templateText, ErrorReporter.exploding());
  }

  private static TemplateNode compileTemplate(String templateText, ErrorReporter errorReporter) {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(templateText)
            .errorReporter(errorReporter)
            .parse()
            .fileSet();
    return (TemplateNode) SharedTestUtils.getNode(soyTree);
  }
}
