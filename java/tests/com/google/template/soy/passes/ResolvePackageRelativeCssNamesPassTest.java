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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.template.soy.css.CssRegistry;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.testing.SharedTestUtils;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ResolvePackageRelativeCssNamesPass}. */
@RunWith(JUnit4.class)
public final class ResolvePackageRelativeCssNamesPassTest {

  @Test
  public void testBaseCssOnNamespace() {
    TemplateNode template =
        compileTemplate(
            "{namespace boo cssbase=\"some.test.package\"}\n\n"
                + "/** Test template.*/\n"
                + "{template .foo}\n"
                + "  <p class=\"{css('%AAA')}\">\n"
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
                + "  <p class=\"{css('%AAA')}\">\n"
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
                + "  <p class=\"{css('%AAA')}\">\n"
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
                + "  <p class=\"{css('AAA')}\">\n"
                + "{/template}\n");
    PrintNode printNode =
        Iterables.getOnlyElement(SoyTreeUtils.getAllNodesOfType(template, PrintNode.class));
    FunctionNode cssFn = (FunctionNode) printNode.getExpr().getRoot();
    assertThat(((StringNode) cssFn.getChild(0)).getValue()).isEqualTo("AAA");
  }

  @Test
  public void testWithComponentName() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    compileTemplate(
        "{namespace boo}\n\n"
            + "/** Test template. */\n"
            + "{template .foo cssbase=\"ns.bar\"}\n"
            + "  {@param goo: string}\n"
            + "  <p class=\"{css($goo, '%AAA')}\">\n"
            + "{/template}\n",
        errorReporter);
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(errorReporter.getErrors().get(0).message())
        .isEqualTo("Package-relative class name '%AAA' cannot be used with component expression.");
  }

  @Test
  public void testNamespaceCssBaseOverRequireCss() {
    TemplateNode template =
        compileTemplate(
            "{namespace boo requirecss='some.test.package' cssbase='some.other.package'}\n\n"
                + "/** Test template. */\n"
                + "{template .foo}\n"
                + "  <p class=\"{css('%AAA')}\">\n"
                + "{/template}\n");
    PrintNode printNode =
        Iterables.getOnlyElement(SoyTreeUtils.getAllNodesOfType(template, PrintNode.class));
    FunctionNode cssFn = (FunctionNode) printNode.getExpr().getRoot();
    assertThat(((StringNode) cssFn.getChild(0)).getValue()).isEqualTo("someOtherPackageAAA");
  }

  @Test
  public void testTemplateCssBaseOverRequireCssAndCssBase() {
    TemplateNode template =
        compileTemplate(
            "{namespace boo requirecss='some.test.package' cssbase='some.other.package'}\n\n"
                + "/** Test template. */\n"
                + "{template .foo cssbase='the.actual.package'}\n"
                + "  <p class=\"{css('%AAA')}\">\n"
                + "{/template}\n");
    PrintNode printNode =
        Iterables.getOnlyElement(SoyTreeUtils.getAllNodesOfType(template, PrintNode.class));
    FunctionNode cssFn = (FunctionNode) printNode.getExpr().getRoot();
    assertThat(((StringNode) cssFn.getChild(0)).getValue()).isEqualTo("theActualPackageAAA");
  }

  @Test
  public void testIgnoreTemplateRequireCss() {
    TemplateNode template =
        compileTemplate(
            "{namespace boo requirecss='some.test.package'}\n\n"
                + "/** Test template. */\n"
                + "{template .foo requirecss='some.other.package'}\n"
                + "  <p class=\"{css('%AAA')}\">\n"
                + "{/template}\n");
    PrintNode printNode =
        Iterables.getOnlyElement(SoyTreeUtils.getAllNodesOfType(template, PrintNode.class));
    FunctionNode cssFn = (FunctionNode) printNode.getExpr().getRoot();
    assertThat(((StringNode) cssFn.getChild(0)).getValue()).isEqualTo("someTestPackageAAA");
  }

  private static TemplateNode compileTemplate(String templateText) {
    return compileTemplate(templateText, ErrorReporter.exploding());
  }

  private static TemplateNode compileTemplate(String templateText, ErrorReporter errorReporter) {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(templateText)
            .errorReporter(errorReporter)
            .cssRegistry(
                CssRegistry.create(
                    ImmutableSet.of("some.test.package", "some.other.package"), ImmutableMap.of()))
            .parse()
            .fileSet();
    return (TemplateNode) SharedTestUtils.getNode(soyTree);
  }
}
