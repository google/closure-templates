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

package com.google.template.soy.soyparse;

import static com.google.common.collect.Iterables.getLast;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.truth.StringSubject;
import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.passes.CombineConsecutiveRawTextNodesPass;
import com.google.template.soy.passes.DesugarHtmlNodesPass;
import com.google.template.soy.soytree.CommandChar;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlCommentNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.RawTextNode.SourceOffsets.Reason;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import java.io.StringReader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HtmlRewriterTest {

  @Test
  public void testTags() {
    TemplateNode node = runPass("<div></div>");
    assertThat(node.getChild(0)).isInstanceOf(HtmlOpenTagNode.class);
    assertThat(node.getChild(1)).isInstanceOf(HtmlCloseTagNode.class);
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
    node = runPass("<input class=foo/>");
    assertThatSourceString(node).isEqualTo("<input class=foo/>");
    HtmlOpenTagNode openTag = (HtmlOpenTagNode) node.getChild(0);
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
    assertThatSourceString(node).isEqualTo("x x<div>content</div> <div>{sp}</div>");
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
  public void testUnquotedAttributeValue() {
    TemplateNode node = runPass("<img class=foo />");
    assertThat(((HtmlOpenTagNode) node.getChild(0)).isSelfClosing()).isTrue();
    node = runPass("<img class=foo/>");
    assertThat(((HtmlOpenTagNode) node.getChild(0)).isSelfClosing()).isFalse();
    node = runPass("<img class/>");
    assertThat(((HtmlOpenTagNode) node.getChild(0)).isSelfClosing()).isTrue();
  }

  // The newlines between the print nodes will get eliminated by the line joining algorithm, but we
  // track where they were trimmed to make sure this doedsn't parse the same as 'foo={$p}{$p}{$p}'
  @Test
  public void testJoinedWhitespace() {
    TemplateNode node = runPass("{@param p:?}<img\nfoo={$p}\n{$p}\n{$p} />");
    assertThatASTString(node)
        .isEqualTo(
            ""
                + "HTML_OPEN_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "  HTML_ATTRIBUTE_NODE\n"
                + "    RAW_TEXT_NODE\n"
                + "    HTML_ATTRIBUTE_VALUE_NODE\n"
                + "      PRINT_NODE\n"
                + "  HTML_ATTRIBUTE_NODE\n"
                + "    PRINT_NODE\n"
                + "  HTML_ATTRIBUTE_NODE\n"
                + "    PRINT_NODE\n"
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

    node = runPass("{let $t : 'x' /}<div {$t}={$t}>content</div>");
    assertThatSourceString(node).isEqualTo("{let $t : 'x' /}<div {$t}={$t}>content</div>");
    assertThatASTString(node)
        .isEqualTo(
            ""
                + "LET_VALUE_NODE\n"
                + "HTML_OPEN_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "  HTML_ATTRIBUTE_NODE\n"
                + "    PRINT_NODE\n"
                + "    HTML_ATTRIBUTE_VALUE_NODE\n"
                + "      PRINT_NODE\n"
                + "RAW_TEXT_NODE\n"
                + "HTML_CLOSE_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "");

    node =
        runPass(
            "<div {call .name /}=x>content</div>{/template}" + "{template .name kind=\"text\"}foo");
    assertThatASTString(node)
        .isEqualTo(
            ""
                + "HTML_OPEN_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "  HTML_ATTRIBUTE_NODE\n"
                + "    CALL_BASIC_NODE\n"
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
      ErrorReporter errorReporter = ErrorReporter.createForTest();
      runPass(tag, errorReporter);
      assertWithMessage("error message for: %s", tag)
          .that(Iterables.getOnlyElement(errorReporter.getErrors()).message())
          .isEqualTo("Unexpected close tag for context-changing tag.");
    }
  }

  // regression test for a bug where we would drop rcdata content.
  @Test
  public void testRcDataTags() {
    assertThatSourceString(runPass("<script>xxx</script>")).isEqualTo("<script>xxx</script>");
  }

  @Test
  public void testBadTagName() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    runPass("<3 >", errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Illegal tag name character.");
  }

  @Test
  public void testBadAttributeName() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    runPass("<div foo-->", errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Illegal attribute name character.");
    errorReporter = ErrorReporter.createForTest();
    runPass("<div 0a>", errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Illegal attribute name character.");

    // these are fine, for weird reasons.  afaik, these characters aren't allowed by any defined
    // html attributes... but we'll allow them since some users are using them for weird reasons.
    // polymer uses _src and _style apparently
    runPass("<div _src='foo'>");
    runPass("<div $src='foo'>");
    runPass("<div $src_='foo'>");
  }

  @Test
  public void testHtmlCommentWithOnlyRawTextNode() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    TemplateNode node;

    // The most common test case.
    node = runPass("<!--foo-->", errorReporter);
    assertThatASTString(node)
        .isEqualTo(Joiner.on('\n').join("HTML_COMMENT_NODE", "  RAW_TEXT_NODE", ""));
    assertThatSourceString(node).isEqualTo("<!--foo-->");

    // Empty comment is allowed.
    node = runPass("<!---->", errorReporter);
    assertThatASTString(node).isEqualTo(Joiner.on('\n').join("HTML_COMMENT_NODE", ""));
    assertThatSourceString(node).isEqualTo("<!---->");

    // White spaces should be preserved.
    node = runPass("<!-- foo -->", errorReporter);
    assertThatASTString(node)
        .isEqualTo(Joiner.on('\n').join("HTML_COMMENT_NODE", "  RAW_TEXT_NODE", ""));
    assertThatSourceString(node).isEqualTo("<!-- foo -->");

    // script tag within HTML comment should be treated as raw text.
    node = runPass("<!-- <script>alert(\"Hi\");</script> -->", errorReporter);
    assertThatASTString(node)
        .isEqualTo(Joiner.on('\n').join("HTML_COMMENT_NODE", "  RAW_TEXT_NODE", ""));
    assertThatSourceString(node).isEqualTo("<!-- <script>alert(\"Hi\");</script> -->");

    // This is fine since we never start a HTML comment, so it is treated as raw text.
    node = runPass("-->", errorReporter);
    assertThatASTString(node).isEqualTo(Joiner.on('\n').join("RAW_TEXT_NODE", ""));
    assertThatSourceString(node).isEqualTo("-->");
  }

  @Test
  public void testHtmlCommentWithPrintNode() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    TemplateNode node;

    // Print node.
    node = runPass("<!--{$foo}-->", errorReporter);
    assertThatASTString(node)
        .isEqualTo(Joiner.on('\n').join("HTML_COMMENT_NODE", "  PRINT_NODE", ""));
    assertThatSourceString(node).isEqualTo("<!--{$foo}-->");

    // Mixed print node and raw text node.
    node = runPass("<!--{$foo}hello{$bar}-->", errorReporter);
    assertThatASTString(node)
        .isEqualTo(
            Joiner.on('\n')
                .join("HTML_COMMENT_NODE", "  PRINT_NODE", "  RAW_TEXT_NODE", "  PRINT_NODE", ""));
    assertThatSourceString(node).isEqualTo("<!--{$foo}hello{$bar}-->");
  }

  @Test
  public void testHtmlCommentWithControlFlow() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    TemplateNode node;
    // Control flow structure should be preserved.
    node = runPass("<!-- {if $foo} foo {else} bar {/if} -->", errorReporter);
    assertThatASTString(node)
        .isEqualTo(
            Joiner.on('\n')
                .join(
                    "HTML_COMMENT_NODE",
                    "  RAW_TEXT_NODE",
                    "  IF_NODE",
                    "    IF_COND_NODE",
                    "      RAW_TEXT_NODE",
                    "    IF_ELSE_NODE",
                    "      RAW_TEXT_NODE",
                    "  RAW_TEXT_NODE",
                    ""));
    assertThatSourceString(node).isEqualTo("<!-- {if $foo} foo {else} bar {/if} -->");
  }

  @Test
  public void testBadHtmlComment() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    // These are examples that we haven't closed the HTML comments.
    for (String text : new String[] {"<!--", "<!-- --", "<!--->"}) {
      errorReporter = ErrorReporter.createForTest();
      runPass(text, errorReporter);
      assertWithMessage("error message for: %s", text)
          .that(Iterables.getOnlyElement(errorReporter.getErrors()).message())
          .isEqualTo(
              "template changes context from 'pcdata' to 'html comment'. "
                  + "Did you forget to close the html comment?");
    }
  }

  @Test
  public void testLiteralPreserved() {
    TemplateNode node = runPass("{literal}\n<style>div { color: red; }</style>   \n{/literal}");
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
                + "");
    StandaloneNode last = getLast(node.getChildren());
    assertThat(last).isInstanceOf(RawTextNode.class);
    RawTextNode rtn = (RawTextNode) last;
    assertThat(rtn.getRawText()).isEqualTo("   \n"); // must not be collapsed.
    assertThat(rtn.getReasonAt(rtn.getRawText().length())).isEqualTo(Reason.LITERAL);
  }

  @Test
  public void testCommandCharInRegularText() {
    TemplateNode node = runPass("<div>hi,{sp}friend</div>");

    assertThatASTStringNoCombine(node)
        .isEqualTo(
            ""
                + "HTML_OPEN_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "RAW_TEXT_NODE\n" // "hi,"
                + "RAW_TEXT_NODE\n" // "{sp}"
                + "RAW_TEXT_NODE\n" // "friend"
                + "HTML_CLOSE_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "");

    // "hi,"
    assertThat(((RawTextNode) node.getChild(1)).getRawText()).isEqualTo("hi,");

    // {sp}
    RawTextNode spNode = (RawTextNode) node.getChild(2);
    assertThat(spNode.getRawText()).isEqualTo(" ");
    assertThat(spNode.getCommandChar()).isEqualTo(CommandChar.SPACE);

    // "friend"
    assertThat(((RawTextNode) node.getChild(3)).getRawText()).isEqualTo("friend");
  }

  @Test
  public void testSpCommandCharWithinAttributeValue() {
    TemplateNode node = runPass("<div class=\"GreenText{sp}BoldText\">hi, friend</div>");

    assertThatASTStringNoCombine(node)
        .isEqualTo(
            ""
                + "HTML_OPEN_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "  HTML_ATTRIBUTE_NODE\n"
                + "    RAW_TEXT_NODE\n"
                + "    HTML_ATTRIBUTE_VALUE_NODE\n"
                + "      RAW_TEXT_NODE\n"
                + "      RAW_TEXT_NODE\n"
                + "      RAW_TEXT_NODE\n"
                + "RAW_TEXT_NODE\n"
                + "HTML_CLOSE_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "");

    HtmlOpenTagNode openTag = (HtmlOpenTagNode) node.getChild(0);
    assertThat(openTag.isSelfClosing()).isFalse();
    assertThat(((RawTextNode) openTag.getChild(0)).getRawText()).isEqualTo("div");
    HtmlAttributeValueNode attributeValue =
        (HtmlAttributeValueNode) ((HtmlAttributeNode) openTag.getChild(1)).getChild(1);
    assertThat(((RawTextNode) attributeValue.getChild(0)).getRawText()).isEqualTo("GreenText");
    assertThat(((RawTextNode) attributeValue.getChild(1)).getCommandChar())
        .isEqualTo(CommandChar.SPACE);
    assertThat(((RawTextNode) attributeValue.getChild(2)).getRawText()).isEqualTo("BoldText");
  }

  @Test
  public void testNilCommandCharInHrefAttribute() {
    TemplateNode node = runPass("<a href=\"www.google.com\n{nil}/search\">hi, friend</div>");

    assertThatASTStringNoCombine(node)
        .isEqualTo(
            ""
                + "HTML_OPEN_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "  HTML_ATTRIBUTE_NODE\n"
                + "    RAW_TEXT_NODE\n"
                + "    HTML_ATTRIBUTE_VALUE_NODE\n"
                + "      RAW_TEXT_NODE\n"
                + "      RAW_TEXT_NODE\n"
                + "      RAW_TEXT_NODE\n"
                + "RAW_TEXT_NODE\n"
                + "HTML_CLOSE_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "");

    HtmlOpenTagNode openTag = (HtmlOpenTagNode) node.getChild(0);
    assertThat(openTag.isSelfClosing()).isFalse();
    assertThat(((RawTextNode) openTag.getChild(0)).getRawText()).isEqualTo("a");
    HtmlAttributeValueNode attributeValue =
        (HtmlAttributeValueNode) ((HtmlAttributeNode) openTag.getChild(1)).getChild(1);
    assertThat(attributeValue.toSourceString()).isEqualTo("\"www.google.com{nil}/search\"");
    assertThat(((RawTextNode) attributeValue.getChild(1)).isNilCommandChar()).isTrue();
    assertThat(((RawTextNode) node.getChild(1)).getRawText()).isEqualTo("hi, friend");
  }

  @Test
  public void testNilCommandCharInHtmlComment() {
    TemplateNode node =
        runPass("<div>\n" + "<!-- html comment with a {nil} character -->hi, friend\n" + "</div>");

    assertThatASTStringNoCombine(node)
        .isEqualTo(
            ""
                + "HTML_OPEN_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "HTML_COMMENT_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "RAW_TEXT_NODE\n"
                + "HTML_CLOSE_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "");

    HtmlCommentNode commentNode = (HtmlCommentNode) node.getChild(1);
    assertThat(((RawTextNode) commentNode.getChild(1)).isNilCommandChar()).isTrue();
  }

  @Test
  public void testOneCharLiteralPreserved() {
    TemplateNode node = runPass("{literal}\n<style>div { color: red; }</style>\n{/literal}");
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
                + "");
    StandaloneNode last = getLast(node.getChildren());
    assertThat(last).isInstanceOf(RawTextNode.class);
    RawTextNode rtn = (RawTextNode) last;
    assertThat(rtn.getRawText()).isEqualTo("\n"); // must not be collapsed.
    assertThat(rtn.getReasonAt(rtn.getRawText().length())).isEqualTo(Reason.LITERAL);
  }

  @Test
  public void testConcatPreservesLiteral() {
    TemplateNode node =
        runPass("{literal}<style>div { color: red; }</style>\n{/literal}\n  <div></div>");
    assertThatASTStringNoCombine(node)
        .isEqualTo(
            ""
                + "HTML_OPEN_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "RAW_TEXT_NODE\n"
                + "HTML_CLOSE_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "RAW_TEXT_NODE\n" // <-- "last" RawTextNode
                + "HTML_OPEN_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "HTML_CLOSE_TAG_NODE\n"
                + "  RAW_TEXT_NODE\n"
                + "");
    StandaloneNode last = node.getChildren().get(node.numChildren() - 3); // last RawTextNode
    assertThat(last).isInstanceOf(RawTextNode.class);
    RawTextNode rtn = (RawTextNode) last;
    assertThat(rtn.getRawText()).isEqualTo("\n"); // must not be collapsed.
    assertThat(rtn.getReasonAt(rtn.getRawText().length())).isEqualTo(Reason.LITERAL);
  }

  private static TemplateNode runPass(String input) {
    return runPass(input, ErrorReporter.exploding());
  }

  /** Parses the given input as a template content. */
  private static TemplateNode runPass(String input, ErrorReporter errorReporter) {
    String soyFile =
        Joiner.on('\n')
            .join("{namespace ns}", "", "{template .t stricthtml=\"false\"}", input, "{/template}");
    SoyFileNode node =
        new SoyFileParser(
                new IncrementingIdGenerator(), new StringReader(soyFile), "test.soy", errorReporter)
            .parseSoyFile();
    if (node != null) {
      return (TemplateNode) node.getChild(0);
    }
    return null;
  }

  private static StringSubject assertThatSourceString(TemplateNode node) {
    SoyFileNode parent = node.getParent().copy(new CopyState());
    new DesugarHtmlNodesPass().run(parent, new IncrementingIdGenerator());
    StringBuilder sb = new StringBuilder();
    ((TemplateNode) parent.getChild(0)).appendSourceStringForChildren(sb);
    return assertThat(sb.toString());
  }

  private static StringSubject assertThatASTString(TemplateNode node) {
    SoyFileNode parent = node.getParent().copy(new CopyState());
    new CombineConsecutiveRawTextNodesPass().run(parent);
    return assertThat(
        SoyTreeUtils.buildAstString((TemplateNode) parent.getChild(0), 0, new StringBuilder())
            .toString());
  }

  private static StringSubject assertThatASTStringNoCombine(TemplateNode node) {
    return assertThat(SoyTreeUtils.buildAstString(node, 0, new StringBuilder()).toString());
  }
}
