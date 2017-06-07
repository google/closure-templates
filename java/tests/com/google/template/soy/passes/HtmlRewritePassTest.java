/*
 * Copyright 2016 Google Inc.
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

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.truth.StringSubject;
import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.error.FormattingErrorReporter;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.types.SoyTypeRegistry;
import java.io.StringReader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HtmlRewritePassTest {

  @Test
  public void testTags() {
    TemplateNode node = runPass("<div></div>");
    assertThat(node.getChild(0)).isInstanceOf(RawTextNode.class);
    assertThat(node.getChild(1)).isInstanceOf(HtmlOpenTagNode.class);
    assertThat(node.getChild(2)).isInstanceOf(HtmlCloseTagNode.class);
    assertThatSourceString(node).isEqualTo("<div></div>");
    assertThatASTString(node)
        .isEqualTo(
            "HTML_OPEN_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "HTML_CLOSE_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "");
  }

  @Test
  public void testAttributes() {
    TemplateNode node = runPass("<div class=\"foo\"></div>");
    assertThatSourceString(node).isEqualTo("<div class=\"foo\"></div>");
    String structure =
        ""
            + "HTML_OPEN_TAG_NODE\n"
            + "  RAW_TEXT_NODE\n"
            + "  HTML_ATTRIBUTE_NODE\n"
            + "    RAW_TEXT_NODE\n"
            + "    HTML_ATTRIBUTE_VALUE_NODE\n"
            + "      RAW_TEXT_NODE\n"
            + "HTML_CLOSE_TAG_NODE\n"
            + "  RAW_TEXT_NODE\n"
            + "";
    assertThatASTString(node).isEqualTo(structure);

    // test alternate quotation marks

    node = runPass("<div class='foo'></div>");
    assertThatSourceString(node).isEqualTo("<div class='foo'></div>");
    assertThatASTString(node).isEqualTo(structure);

    node = runPass("<div class=foo></div>");
    assertThatSourceString(node).isEqualTo("<div class=foo></div>");
    assertThatASTString(node).isEqualTo(structure);

    // This is a tricky case, according to the spec the '/' belongs to the attribute, not the tag
    node = runPass("<div class=foo/>");
    assertThatSourceString(node).isEqualTo("<div class=foo/>");
    HtmlOpenTagNode openTag = (HtmlOpenTagNode) node.getChild(1);
    assertThat(openTag.isSelfClosing()).isFalse();
    HtmlAttributeValueNode attributeValue =
        (HtmlAttributeValueNode) ((HtmlAttributeNode) openTag.getChild(1)).getChild(1);
    assertThat(attributeValue.getQuotes()).isEqualTo(HtmlAttributeValueNode.Quotes.NONE);
    assertThat(((RawTextNode) attributeValue.getChild(0)).getRawText()).isEqualTo("foo/");
  }

  @Test
  public void testLetAttributes() {
    TemplateNode node = runPass("{let $foo kind=\"attributes\"}class='foo'{/let}");
    assertThatSourceString(node).isEqualTo("{let $foo kind=\"attributes\"}class='foo'{/let}");
    String structure =
        ""
            + "LET_CONTENT_NODE\n"
            + "  HTML_ATTRIBUTE_NODE\n"
            + "    RAW_TEXT_NODE\n"
            + "    HTML_ATTRIBUTE_VALUE_NODE\n"
            + "      RAW_TEXT_NODE\n";
    assertThatASTString(node).isEqualTo(structure);
  }

  @Test
  public void testSelfClosingTag() {
    TemplateNode node = runPass("<input/>");
    assertThatSourceString(node).isEqualTo("<input/>");

    // NOTE: the whitespace difference
    node = runPass("<input />");
    assertThatSourceString(node).isEqualTo("<input/>");
  }

  @Test
  public void testTextNodes() {
    TemplateNode node = runPass("x x<div>content</div> <div>{sp}</div>");
    assertThatSourceString(node).isEqualTo("x x<div>content</div> <div> </div>");
    assertThatASTString(node)
        .isEqualTo(
            ""
                + "RAW_TEXT_NODE\n"
                + "HTML_OPEN_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "RAW_TEXT_NODE\n"
                + "HTML_CLOSE_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "RAW_TEXT_NODE\n"
                + "HTML_OPEN_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "RAW_TEXT_NODE\n"
                + "HTML_CLOSE_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "");
  }

  @Test
  public void testDynamicTagName() {
    TemplateNode node = runPass("{let $t : 'div' /}<{$t}>content</{$t}>");
    assertThatSourceString(node).isEqualTo("{let $t : 'div' /}<{$t}>content</{$t}>");
    // NOTE: the print nodes don't end up in the AST due to how TagName works, this is probably a
    // bad idea in the long run.  We should probably make TagName be a node.
    assertThatASTString(node)
        .isEqualTo(
            ""
                + "LET_VALUE_NODE\n"
                + "HTML_OPEN_TAG_NODE\n"
                + "  PRINT_NODE\n"
                + "RAW_TEXT_NODE\n"
                + "HTML_CLOSE_TAG_NODE\n"
                + "  PRINT_NODE\n"
                + "");
  }

  @Test
  public void testDynamicAttributeValue() {
    TemplateNode node = runPass("{let $t : 'x' /}<div a={$t}>content</div>");
    assertThatSourceString(node).isEqualTo("{let $t : 'x' /}<div a={$t}>content</div>");
    assertThatASTString(node)
        .isEqualTo(
            ""
                + "LET_VALUE_NODE\n"
                + "HTML_OPEN_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "  HTML_ATTRIBUTE_NODE\n"
                + "    RAW_TEXT_NODE\n"
                + "    HTML_ATTRIBUTE_VALUE_NODE\n"
                + "      PRINT_NODE\n"
                + "RAW_TEXT_NODE\n"
                + "HTML_CLOSE_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "");
    // try alternate quotes
    node = runPass("{let $t : 'x' /}<div a=\"{$t}\">content</div>");
    assertThatSourceString(node).isEqualTo("{let $t : 'x' /}<div a=\"{$t}\">content</div>");

    node = runPass("{let $t : 'x' /}<div a='{$t}'>content</div>");
    assertThatSourceString(node).isEqualTo("{let $t : 'x' /}<div a='{$t}'>content</div>");
  }

  @Test
  public void testDynamicAttribute() {
    TemplateNode node = runPass("{let $t : 'x' /}<div {$t}>content</div>");
    assertThatSourceString(node).isEqualTo("{let $t : 'x' /}<div {$t}>content</div>");
    assertThatASTString(node)
        .isEqualTo(
            ""
                + "LET_VALUE_NODE\n"
                + "HTML_OPEN_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "  HTML_ATTRIBUTE_NODE\n"
                + "    PRINT_NODE\n"
                + "RAW_TEXT_NODE\n"
                + "HTML_CLOSE_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "");

    // and with a value
    node = runPass("{let $t : 'x' /}<div {$t}=x>content</div>");
    assertThatSourceString(node).isEqualTo("{let $t : 'x' /}<div {$t}=x>content</div>");
    assertThatASTString(node)
        .isEqualTo(
            ""
                + "LET_VALUE_NODE\n"
                + "HTML_OPEN_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "  HTML_ATTRIBUTE_NODE\n"
                + "    PRINT_NODE\n"
                + "    HTML_ATTRIBUTE_VALUE_NODE\n"
                + "      RAW_TEXT_NODE\n"
                + "RAW_TEXT_NODE\n"
                + "HTML_CLOSE_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "");
  }

  @Test
  public void testConditionalAttribute() {
    TemplateNode node = runPass("{let $t : 'x' /}<div {if $t}foo{else}bar{/if}>content</div>");
    assertThatSourceString(node)
        .isEqualTo("{let $t : 'x' /}<div{if $t} foo{else} bar{/if}>content</div>");
    assertThatASTString(node)
        .isEqualTo(
            ""
                + "LET_VALUE_NODE\n"
                + "HTML_OPEN_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "  IF_NODE\n"
                + "    IF_COND_NODE\n"
                + "      HTML_ATTRIBUTE_NODE\n"
                + "        RAW_TEXT_NODE\n"
                + "    IF_ELSE_NODE\n"
                + "      HTML_ATTRIBUTE_NODE\n"
                + "        RAW_TEXT_NODE\n"
                + "RAW_TEXT_NODE\n"
                + "HTML_CLOSE_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "");
  }

  @Test
  public void testConditionalAttributeValue() {
    TemplateNode node =
        runPass("{let $t : 'x' /}<div class=\"{if $t}foo{else}bar{/if}\">content</div>");
    assertThatSourceString(node)
        .isEqualTo("{let $t : 'x' /}<div class=\"{if $t}foo{else}bar{/if}\">content</div>");
    assertThatASTString(node)
        .isEqualTo(
            ""
                + "LET_VALUE_NODE\n"
                + "HTML_OPEN_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "  HTML_ATTRIBUTE_NODE\n"
                + "    RAW_TEXT_NODE\n"
                + "    HTML_ATTRIBUTE_VALUE_NODE\n"
                + "      IF_NODE\n"
                + "        IF_COND_NODE\n"
                + "          RAW_TEXT_NODE\n"
                + "        IF_ELSE_NODE\n"
                + "          RAW_TEXT_NODE\n"
                + "RAW_TEXT_NODE\n"
                + "HTML_CLOSE_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "");
  }

  // TODO(lukes): ideally these would all be implemented in the CompilerIntegrationTests but the
  // ContextualAutoescaper rejects these forms.  once we stop 'desuraging' prior to the autoescaper
  // we can move these tests over.

  @Test
  public void testConditionalContextMerging() {
    TemplateNode node = runPass("{@param p : ?}<div {if $p}foo=bar{else}baz{/if}>");
    assertThatSourceString(node).isEqualTo("<div{if $p} foo=bar{else} baz{/if}>");
    assertThatASTString(node)
        .isEqualTo(
            ""
                + "HTML_OPEN_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "  IF_NODE\n"
                + "    IF_COND_NODE\n"
                + "      HTML_ATTRIBUTE_NODE\n"
                + "        RAW_TEXT_NODE\n"
                + "        HTML_ATTRIBUTE_VALUE_NODE\n"
                + "          RAW_TEXT_NODE\n"
                + "    IF_ELSE_NODE\n"
                + "      HTML_ATTRIBUTE_NODE\n"
                + "        RAW_TEXT_NODE\n"
                + "");
    node = runPass("{@param p : ?}<div {if $p}class=x{else}style=\"baz\"{/if}>");
    assertThatSourceString(node).isEqualTo("<div{if $p} class=x{else} style=\"baz\"{/if}>");

    node = runPass("{@param p : ?}<div {if $p}class='x'{else}style=\"baz\"{/if}>");
    assertThatSourceString(node).isEqualTo("<div{if $p} class='x'{else} style=\"baz\"{/if}>");
  }

  // Ideally, we wouldn't support this pattern since it adds a fair bit of complexity
  @Test
  public void testConditionalQuotedAttributeValues() {
    TemplateNode node = runPass("{@param p : ?}<div x={if $p}'foo'{else}'bar'{/if} {$p}>");
    assertThatSourceString(node).isEqualTo("<div x={if $p}'foo'{else}'bar'{/if} {$p}>");
    assertThatASTString(node)
        .isEqualTo(
            ""
                + "HTML_OPEN_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "  HTML_ATTRIBUTE_NODE\n"
                + "    RAW_TEXT_NODE\n"
                + "    IF_NODE\n"
                + "      IF_COND_NODE\n"
                + "        HTML_ATTRIBUTE_VALUE_NODE\n"
                + "          RAW_TEXT_NODE\n"
                + "      IF_ELSE_NODE\n"
                + "        HTML_ATTRIBUTE_VALUE_NODE\n"
                + "          RAW_TEXT_NODE\n"
                + "  HTML_ATTRIBUTE_NODE\n"
                + "    PRINT_NODE\n"
                + "");

    node =
        runPass(
            "{@param p : ?}{@param p2 : ?}<div x={if $p}{if $p2}'foo'{else}'bar'{/if}"
                + "{else}{if $p2}'foo'{else}'bar'{/if}{/if} {$p}>");
    assertThatSourceString(node)
        .isEqualTo(
            "<div x={if $p}{if $p2}'foo'{else}'bar'{/if}{else}{if $p2}'foo'{else}'bar'{/if}{/if}"
                + " {$p}>");
    assertThatASTString(node)
        .isEqualTo(
            ""
                + "HTML_OPEN_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "  HTML_ATTRIBUTE_NODE\n"
                + "    RAW_TEXT_NODE\n"
                + "    IF_NODE\n"
                + "      IF_COND_NODE\n"
                + "        IF_NODE\n"
                + "          IF_COND_NODE\n"
                + "            HTML_ATTRIBUTE_VALUE_NODE\n"
                + "              RAW_TEXT_NODE\n"
                + "          IF_ELSE_NODE\n"
                + "            HTML_ATTRIBUTE_VALUE_NODE\n"
                + "              RAW_TEXT_NODE\n"
                + "      IF_ELSE_NODE\n"
                + "        IF_NODE\n"
                + "          IF_COND_NODE\n"
                + "            HTML_ATTRIBUTE_VALUE_NODE\n"
                + "              RAW_TEXT_NODE\n"
                + "          IF_ELSE_NODE\n"
                + "            HTML_ATTRIBUTE_VALUE_NODE\n"
                + "              RAW_TEXT_NODE\n"
                + "  HTML_ATTRIBUTE_NODE\n"
                + "    PRINT_NODE\n"
                + "");
  }

  @Test
  public void testConditionalUnquotedAttributeValue() {
    TemplateNode node = runPass("{@param p : ?}<div class={if $p}x{else}y{/if}>");
    assertThatSourceString(node).isEqualTo("<div class={if $p}x{else}y{/if}>");
  }

  @Test
  public void testUnmatchedContextChangingCloseTagUnquotedAttributeValue() {
    // matched script is fine
    runPass("<script>xxx</script>");
    // unmatched closing div is fine.
    runPass("</div>");
    for (String tag : new String[] {"</script>", "</style>", "</title>", "</textarea>", "</xmp>"}) {
      FormattingErrorReporter errorReporter = new FormattingErrorReporter();
      runPass(tag, errorReporter);
      assertThat(errorReporter.getErrorMessages())
          .named("error message for: %s", tag)
          .containsExactly("Unexpected close tag for context-changing tag.");
    }
  }

  // regression test for a bug where we would drop rcdata content.
  @Test
  public void testRcDataTags() {
    assertThatSourceString(runPass("<script>xxx</script>")).isEqualTo("<script>xxx</script>");
  }

  private static TemplateNode runPass(String input) {
    return runPass(input, ExplodingErrorReporter.get());
  }

  /** Parses the given input as a template content. */
  private static TemplateNode runPass(String input, ErrorReporter errorReporter) {
    String soyFile =
        Joiner.on('\n')
            .join("{namespace ns}", "", "{template .t stricthtml=\"true\"}", input, "{/template}");
    IncrementingIdGenerator nodeIdGen = new IncrementingIdGenerator();
    SoyFileNode node =
        new SoyFileParser(
                new SoyTypeRegistry(),
                nodeIdGen,
                new StringReader(soyFile),
                SoyFileKind.SRC,
                "test.soy",
                errorReporter)
            .parseSoyFile();
    if (node != null) {
      new HtmlRewritePass(true, errorReporter).run(node, nodeIdGen);
      return node.getChild(0);
    }
    return null;
  }

  private static StringSubject assertThatSourceString(TemplateNode node) {
    SoyFileNode parent = SoyTreeUtils.cloneNode(node.getParent());
    new DesugarHtmlNodesPass().run(parent, new IncrementingIdGenerator());
    StringBuilder sb = new StringBuilder();
    parent.getChild(0).appendSourceStringForChildren(sb);
    return assertThat(sb.toString());
  }

  private static StringSubject assertThatASTString(TemplateNode node) {
    SoyFileNode parent = SoyTreeUtils.cloneNode(node.getParent());
    new CombineConsecutiveRawTextNodesVisitor().exec(parent);
    return assertThat(buildAstString(parent.getChild(0), 0, new StringBuilder()).toString());
  }

  private static StringBuilder buildAstString(ParentSoyNode<?> node, int indent, StringBuilder sb) {
    for (SoyNode child : node.getChildren()) {
      sb.append(Strings.repeat("  ", indent)).append(child.getKind()).append('\n');
      if (child instanceof ParentSoyNode) {
        buildAstString((ParentSoyNode<?>) child, indent + 1, sb);
      }
    }
    return sb;
  }
}
