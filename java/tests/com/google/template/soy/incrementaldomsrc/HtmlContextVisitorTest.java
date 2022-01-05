/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.incrementaldomsrc;

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.testing.SharedTestUtils.getNode;

import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.passes.CombineConsecutiveRawTextNodesPass;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.testing.SharedTestUtils;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link HtmlContextVisitor}. */
@RunWith(JUnit4.class)
public final class HtmlContextVisitorTest {
  private static final ErrorReporter FAIL = ErrorReporter.exploding();

  private static SoyFileSetNode performVisitor(String templateBody, ErrorReporter er) {
    return performVisitorFile(SharedTestUtils.buildTestSoyFileContent(templateBody), er);
  }

  private static SoyFileSetNode performVisitorFile(String fileContents, ErrorReporter er) {
    SoyFileSetNode sfsn =
        SoyFileSetParserBuilder.forFileContents(fileContents)
            .errorReporter(er)
            .desugarHtmlNodes(false)
            .parse()
            .fileSet();

    new CombineConsecutiveRawTextNodesPass().run(sfsn);
    new HtmlContextVisitor().exec(sfsn);

    return sfsn;
  }

  @Test
  public void testMultipleAttributes() {
    String templateBody = "<div id=\"foo-id\" data-bam=\"baz\"></div>\n";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);
    assertThat(((HtmlOpenTagNode) getNode(n, 0)).getTagName().getStaticTagNameAsLowerCase())
        .isEqualTo("div");
    assertThat(((HtmlAttributeNode) getNode(n, 0, 1)).getChild(0).toSourceString()).isEqualTo("id");
    assertThat(((RawTextNode) getNode(n, 0, 1, 1, 0)).getRawText()).isEqualTo("foo-id");
    assertThat(((HtmlAttributeNode) getNode(n, 0, 2)).getChild(0).toSourceString())
        .isEqualTo("data-bam");
    assertThat(getNode(n, 1)).isInstanceOf(HtmlCloseTagNode.class);
  }

  @Test
  public void testAttributeWithoutValue() {
    String templateBody = "<div id disabled></div>\n";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);
    assertThat(((HtmlOpenTagNode) getNode(n, 0)).getTagName().getStaticTagNameAsLowerCase())
        .isEqualTo("div");
    assertThat(((HtmlAttributeNode) getNode(n, 0, 1)).getChild(0).toSourceString()).isEqualTo("id");
    assertThat(((HtmlAttributeNode) getNode(n, 0, 2)).getChildren()).hasSize(1);
    assertThat(((HtmlAttributeNode) getNode(n, 0, 2)).getChild(0).toSourceString())
        .isEqualTo("disabled");
    assertThat(getNode(n, 1)).isInstanceOf(HtmlCloseTagNode.class);
  }

  @Test
  public void testAttributeWithPrintNodes() {
    String templateBody =
        "{@param foo : ?}\n"
            + "{@param bar : ?}\n"
            + "<div class=\"Class1 {$foo} Class2 {$bar}\">\n";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);
    assertThat(((HtmlOpenTagNode) getNode(n, 0)).getTagName().getStaticTagNameAsLowerCase())
        .isEqualTo("div");
    assertThat(((HtmlAttributeNode) getNode(n, 0, 1)).getChild(0).toSourceString())
        .isEqualTo("class");
    assertThat(((RawTextNode) getNode(n, 0, 1, 1, 0)).getRawText()).isEqualTo("Class1 ");
    assertThat(((PrintNode) getNode(n, 0, 1, 1, 1)).getExpr().toSourceString()).isEqualTo("$foo");
    assertThat(((RawTextNode) getNode(n, 0, 1, 1, 2)).getRawText()).isEqualTo(" Class2 ");
    assertThat(((PrintNode) getNode(n, 0, 1, 1, 3)).getExpr().toSourceString()).isEqualTo("$bar");
    assertThat(((TemplateNode) getNode(n)).getChildren()).hasSize(1);
  }

  @Test
  public void testCssNodeInAttributeValue() {
    String templateBody = "<div class=\"{css('Foobar')} Baz\"></div>\n";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);
    assertThat(((HtmlOpenTagNode) getNode(n, 0)).getTagName().getStaticTagNameAsLowerCase())
        .isEqualTo("div");
    assertThat(((PrintNode) getNode(n, 0, 1, 1, 0)).toSourceString()).isEqualTo("{css('Foobar')}");
    assertThat(((RawTextNode) getNode(n, 0, 1, 1, 1)).getRawText()).isEqualTo(" Baz");
  }

  @Test
  public void testPrintNodeInAttributeValue() {
    String templateBody = "{@param foo : ?}\n<div id=\"foo {$foo} bar\"></div>\n";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);
    assertThat(((RawTextNode) getNode(n, 0, 1, 1, 0)).getRawText()).isEqualTo("foo ");
    assertThat(((PrintNode) getNode(n, 0, 1, 1, 1)).getExpr().toSourceString()).isEqualTo("$foo");
    assertThat(((PrintNode) getNode(n, 0, 1, 1, 1)).getHtmlContext())
        .isEqualTo(HtmlContext.HTML_NORMAL_ATTR_VALUE);
    assertThat(((RawTextNode) getNode(n, 0, 1, 1, 2)).getRawText()).isEqualTo(" bar");
  }

  @Test
  public void testConditionalInAttributes() {
    String templateBody = "{@param foo : ?}\n<div{if $foo} id=\"foo {$foo}\"{/if}></div>\n";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);
    assertThat(((IfCondNode) getNode(n, 0, 1, 0)).getExpr().toSourceString()).isEqualTo("$foo");
    assertThat(((HtmlAttributeNode) getNode(n, 0, 1, 0, 0)).getChild(0).toSourceString())
        .isEqualTo("id");
    assertThat(((PrintNode) getNode(n, 0, 1, 0, 0, 1, 1)).getHtmlContext())
        .isEqualTo(HtmlContext.HTML_NORMAL_ATTR_VALUE);
  }

  @Test
  public void testParamInAttributeValue() {
    String templateBody =
        ""
            + "{@param x: ?}\n"
            + "<div id=\"{call blah}{param a kind=\"text\"}{$x}{/param}{/call}\"></div>\n";

    SoyFileSetNode n =
        performVisitorFile(
            SharedTestUtils.buildTestSoyFileContent(templateBody)
                + "{template blah}{@param a: string}{/template}",
            FAIL);
    assertThat(((HtmlAttributeNode) getNode(n, 0, 1)).getChild(0).toSourceString()).isEqualTo("id");
    assertThat(((PrintNode) getNode(n, 0, 1, 1, 0, 0, 0)).getHtmlContext())
        .isEqualTo(HtmlContext.TEXT);
  }

  @Test
  public void testPrintInMsgHtml() {
    String templateBody = "{@param x: ?}\n{msg desc=\"\"}<a href=\"{$x}\"></a>{/msg}\n";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);

    assertThat(getNode(n, 0, 0, 0, 0, 0, 1, 0).toSourceString()).isEqualTo("href");
    assertThat(((PrintNode) getNode(n, 0, 0, 0, 0, 0, 1, 1, 0)).getHtmlContext())
        .isEqualTo(HtmlContext.HTML_NORMAL_ATTR_VALUE);
  }

  @Test
  public void testConditionalInAttributeValue() {
    String templateBody = "{@param foo : ?}\n<div id=\"foo {if $foo}{$foo}{/if}\"></div>\n";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);
    assertThat(((HtmlAttributeNode) getNode(n, 0, 1)).getChild(0).toSourceString()).isEqualTo("id");
    assertThat(((IfCondNode) getNode(n, 0, 1, 1, 1, 0)).getExpr().toSourceString())
        .isEqualTo("$foo");
    assertThat(((PrintNode) getNode(n, 0, 1, 1, 1, 0, 0)).getHtmlContext())
        .isEqualTo(HtmlContext.HTML_NORMAL_ATTR_VALUE);
  }

  @Test
  public void testLetKindHtml() {
    String templateBody =
        "{let $content kind=\"html\"}\n" + "  <div id=\"foo-bar\"></div>\n" + "{/let}";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);
    assertThat(((LetContentNode) getNode(n, 0)).getContentKind())
        .isEqualTo(SanitizedContentKind.HTML);
    assertThat(((HtmlOpenTagNode) getNode(n, 0, 0)).getTagName().getStaticTagNameAsLowerCase())
        .isEqualTo("div");
    assertThat(getNode(n, 0, 1)).isInstanceOf(HtmlCloseTagNode.class);
  }

  @Test
  public void testLetKindAttributes() {
    String templateBody =
        ""
            + "{@param disabled : ?}\n"
            + "{let $content kind=\"attributes\"}\n"
            + "  foo=\"bar\"\n"
            + "  {if $disabled}\n"
            + "    disabled=\"true {$disabled}\"\n"
            + "  {/if}\n"
            + "{/let}";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);
    assertThat(((HtmlAttributeNode) getNode(n, 0, 0)).getChild(0).toSourceString())
        .isEqualTo("foo");
    assertThat(((IfCondNode) getNode(n, 0, 1, 0)).getExpr().toSourceString())
        .isEqualTo("$disabled");
    assertThat(((HtmlAttributeNode) getNode(n, 0, 1, 0, 0)).getChild(0).toSourceString())
        .isEqualTo("disabled");
    assertThat(((RawTextNode) getNode(n, 0, 1, 0, 0, 1, 0)).getRawText()).isEqualTo("true ");
    assertThat(((PrintNode) getNode(n, 0, 1, 0, 0, 1, 1)).getHtmlContext())
        .isEqualTo(HtmlContext.HTML_NORMAL_ATTR_VALUE);
  }

  @Test
  public void testLetKindText() {
    String templateBody =
        ""
            + "{@param x: string}\n"
            + "{let $content kind=\"text\"}\n"
            + "  <div id=\"foo\"></div>\n"
            + "  {$x}\n"
            + "{/let}";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);
    assertThat(((LetContentNode) getNode(n, 0)).getContentKind())
        .isEqualTo(SanitizedContentKind.TEXT);
    assertThat(((RawTextNode) getNode(n, 0, 0)).getRawText()).isEqualTo("<div id=\"foo\"></div>");
    assertThat(((PrintNode) getNode(n, 0, 1)).getHtmlContext()).isEqualTo(HtmlContext.TEXT);
  }

  @Test
  public void testTemplateKindText() {
    String fileBody =
        ""
            + "{namespace test}"
            + "/** */"
            + "{template foo kind=\"text\"}"
            + "  {@param x: string}\n"
            + "  <div id=\"foo\"></div>\n"
            + "  {$x}\n"
            + "  {msg desc=\"a\"}b{/msg}\n"
            + "{/template}";

    SoyFileSetNode n =
        SoyFileSetParserBuilder.forFileContents(fileBody).desugarHtmlNodes(false).parse().fileSet();
    new CombineConsecutiveRawTextNodesPass().run(n);
    new HtmlContextVisitor().exec(n);
    assertThat(((RawTextNode) getNode(n, 0)).getRawText()).isEqualTo("<div id=\"foo\"></div>");
    assertThat(((PrintNode) getNode(n, 1)).getHtmlContext()).isEqualTo(HtmlContext.TEXT);
    assertThat(((MsgFallbackGroupNode) getNode(n, 2)).getHtmlContext()).isEqualTo(HtmlContext.TEXT);
  }

  @Test
  public void testConsecutiveIf() {
    String templateBody =
        ""
            + "{@param foo : ?}\n"
            + "{let $content kind=\"html\"}\n"
            + "  <div>\n"
            + "    {if $foo}Hello world{/if}\n"
            + "    {if $foo}\n"
            + "      <div>Hello world</div>\n"
            + "    {/if}\n"
            + "  </div>\n"
            + "{/let}";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);
    assertThat(((IfCondNode) getNode(n, 0, 1, 0)).getExpr().toSourceString()).isEqualTo("$foo");
    assertThat(((RawTextNode) getNode(n, 0, 1, 0, 0)).getRawText()).isEqualTo("Hello world");
    assertThat(((RawTextNode) getNode(n, 0, 1, 0, 0)).getHtmlContext())
        .isEqualTo(HtmlContext.HTML_PCDATA);
    assertThat(((IfCondNode) getNode(n, 0, 2, 0)).getExpr().toSourceString()).isEqualTo("$foo");
    assertThat(
            ((HtmlOpenTagNode) getNode(n, 0, 2, 0, 0)).getTagName().getStaticTagNameAsLowerCase())
        .isEqualTo("div");
  }

  @Test
  public void testSelfClosingInput() {
    String templateBody = "<input />";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);
    assertThat(((HtmlOpenTagNode) getNode(n, 0)).getTagName().getStaticTagNameAsLowerCase())
        .isEqualTo("input");

    // also without the space, a previous bug made this ambiguous and parsed the tag name as
    // "input/"
    templateBody = "<input/>";

    n = performVisitor(templateBody, FAIL);
    assertThat(((HtmlOpenTagNode) getNode(n, 0)).getTagName().getStaticTagNameAsLowerCase())
        .isEqualTo("input");
  }

  @Test
  public void testSelfClosingSvgContent() {
    String templateBody = "<svg><g /></svg>";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);
    assertThat(((HtmlOpenTagNode) getNode(n, 1)).getTagName().getStaticTagNameAsLowerCase())
        .isEqualTo("g");
  }

  @Test
  public void testCssNodeInAttribute() {
    String templateBody = "<div class=\"{css('Foo')}\"></div>";

    performVisitor(templateBody, FAIL);
  }

  @Test
  public void testCssNodeInText() {
    String templateBody =
        "{let $class kind=\"text\"}{css('Foo')}{/let}" + "<div class=\"{$class}\"></div>";

    performVisitor(templateBody, FAIL);
  }

  @Test
  public void testCssNodeInHtml() {
    String templateBody = "<div>{css('Foo')}</div>";

    performVisitor(templateBody, FAIL);
    // this used to be an error but it no longer is
  }

  @Test
  public void testXidNodeInText() {
    String templateBody =
        "{let $foo kind=\"text\"}{xid('Foo')}{/let}" + "<div data-foo=\"{$foo}\"></div>";

    performVisitor(templateBody, FAIL);
  }

  @Test
  public void testXidNodeInAttribute() {
    String templateBody = "<div data-foo=\"{xid('Foo')}\"></div>";

    performVisitor(templateBody, FAIL);
  }

  @Test
  public void testXidNodeInHtml() {
    String templateBody = "<div>{xid('Foo')}</div>";
    performVisitor(templateBody, FAIL);
    // this used to be an error, but since xids are just function calls in normal print nodes it is
    // now ok
  }
}
