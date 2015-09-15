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

package com.google.template.soy.html.passes;

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.shared.SharedTestUtils.getNode;

import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.error.FormattingErrorReporter;
import com.google.template.soy.html.HtmlAttributeNode;
import com.google.template.soy.html.HtmlOpenTagEndNode;
import com.google.template.soy.html.HtmlOpenTagStartNode;
import com.google.template.soy.html.HtmlTextNode;
import com.google.template.soy.shared.AutoEscapingType;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;


import junit.framework.TestCase;

/**
 * Unit tests for {@link HtmlTransformVisitor}.
 */
public final class HtmlTransformVisitorTest extends TestCase {
  private static final ErrorReporter FAIL = ExplodingErrorReporter.get();

  private static SoyFileSetNode performVisitor(String templateBody,
      ErrorReporter er) {
    SoyFileSetNode sfsn =
        SoyFileSetParserBuilder.forTemplateContents(AutoEscapingType.STRICT, templateBody)
            .parse()
            .fileSet();

    new HtmlTransformVisitor(er).exec(sfsn);

    return sfsn;
  }

  public void testMultipleAttributes() {
    String templateBody = "<div id=\"foo\" data-bam=\"baz\"></div>\n";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);
    assertThat(((HtmlOpenTagStartNode) getNode(n, 0)).getTagName()).isEqualTo("div");
    assertThat(((HtmlAttributeNode) getNode(n, 1)).getName()).isEqualTo("id");
    assertThat(((RawTextNode) getNode(n, 1, 0)).getRawText()).isEqualTo("foo");
    assertThat(((HtmlAttributeNode) getNode(n, 2)).getName()).isEqualTo("data-bam");
    assertThat(getNode(n, 3)).isInstanceOf(HtmlOpenTagEndNode.class);
  }

  public void testAttributeWithoutValue() {
    String templateBody = "<div id disabled></div>\n";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);
    assertThat(((HtmlOpenTagStartNode) getNode(n, 0)).getTagName()).isEqualTo("div");
    assertThat(((HtmlAttributeNode) getNode(n, 1)).getName()).isEqualTo("id");
    assertThat(((HtmlAttributeNode) getNode(n, 1)).getChildren()).isEmpty();
    assertThat(((HtmlAttributeNode) getNode(n, 2)).getName()).isEqualTo("disabled");
    assertThat(getNode(n, 3)).isInstanceOf(HtmlOpenTagEndNode.class);
  }

  public void testAttributeWithPrintNodes() {
    String templateBody =
        "{@param foo : ?}\n"
            + "{@param bar : ?}\n"
            + "<div class=\"Class1 {$foo} Class2 {$bar}\">\n";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);
    assertThat(((HtmlOpenTagStartNode) getNode(n, 0)).getTagName()).isEqualTo("div");
    assertThat(((HtmlAttributeNode) getNode(n, 1)).getName()).isEqualTo("class");
    assertThat(((RawTextNode) getNode(n, 1, 0)).getRawText()).isEqualTo("Class1 ");
    assertThat(((PrintNode) getNode(n, 1, 1)).getExprText()).isEqualTo("$foo");
    assertThat(((RawTextNode) getNode(n, 1, 2)).getRawText()).isEqualTo(" Class2 ");
    assertThat(((PrintNode) getNode(n, 1, 3)).getExprText()).isEqualTo("$bar");
    assertThat(getNode(n, 2)).isInstanceOf(HtmlOpenTagEndNode.class);
    assertThat(((TemplateNode) getNode(n)).getChildren()).hasSize(3);
  }
  
  public void testCssNodeInAttributeValue() {
    String templateBody = "<div class=\"{css Foobar} Baz\"></div>\n";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);
    assertThat(((HtmlOpenTagStartNode) getNode(n, 0)).getTagName()).isEqualTo("div");
    assertThat(((CssNode) getNode(n, 1, 0)).getCommandText()).isEqualTo("Foobar");
    assertThat(((RawTextNode) getNode(n, 1, 1)).getRawText()).isEqualTo(" Baz");
  }

  public void testPrintNodeInAttributeValue() {
    String templateBody = "{@param foo : ?}\n<div id=\"foo {$foo} bar\"></div>\n";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);
    assertThat(((RawTextNode) getNode(n, 1, 0)).getRawText()).isEqualTo("foo ");
    assertThat(((PrintNode) getNode(n, 1, 1)).getExprText()).isEqualTo("$foo");
    assertThat(((RawTextNode) getNode(n, 1, 2)).getRawText()).isEqualTo(" bar");
  }

  public void testConditionalInAttributes() {
    String templateBody = "{@param foo : ?}\n<div{if $foo} id=\"foo\"{/if}></div>\n";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);
    assertThat(((IfCondNode) getNode(n, 1, 0)).getCommandText()).isEqualTo("$foo");
    assertThat(((HtmlAttributeNode) getNode(n, 1, 0, 0)).getName()).isEqualTo("id");
  }

  public void testLetKindHtml() {
    String templateBody = ""
        + "{let $content kind=\"html\"}\n"
        + "  <div id=\"foo\"></div>\n"
        + "{/let}";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);
    assertThat(((LetContentNode) getNode(n, 0)).getContentKind()).isEqualTo(ContentKind.HTML);
    assertThat(((HtmlOpenTagStartNode) getNode(n, 0, 0)).getTagName()).isEqualTo("div");
    assertThat(getNode(n, 0, 2)).isInstanceOf(HtmlOpenTagEndNode.class);
  }

  public void testLetKindAttributes() {
    String templateBody =
        ""
            + "{@param disabled : ?}\n"
            + "{let $content kind=\"attributes\"}\n"
            + "  foo=\"bar\"\n"
            + "  {if $disabled}\n"
            + "    disabled=\"true\"\n"
            + "  {/if}\n"
            + "{/let}";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);
    assertThat(((HtmlAttributeNode) getNode(n, 0, 0)).getName()).isEqualTo("foo");
    assertThat(((IfCondNode) getNode(n, 0, 1, 0)).getCommandText()).isEqualTo("$disabled");
    assertThat(((HtmlAttributeNode) getNode(n, 0, 1, 0, 0)).getName()).isEqualTo("disabled");
    assertThat(((RawTextNode) getNode(n, 0, 1, 0, 0, 0)).getRawText()).isEqualTo("true");
  }

  public void testLetKindText() {
    String templateBody = ""
        + "{let $content kind=\"text\"}\n"
        + "  <div id=\"foo\"></div>\n"
        + "{/let}";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);
    assertThat(((LetContentNode) getNode(n, 0)).getContentKind()).isEqualTo(ContentKind.TEXT);
    assertThat(((RawTextNode) getNode(n, 0, 0)).getRawText()).isEqualTo("<div id=\"foo\"></div>");
  }
  
  public void testTemplateKindText() {
    String fileBody = ""
        + "{namespace test}"
        + "/** */"
        + "{template .foo kind=\"text\"}"
        + "  <div id=\"foo\"></div>\n"
        + "{/template}";

    SoyFileSetNode n = SoyFileSetParserBuilder.forFileContents(fileBody).parse().fileSet();
    new HtmlTransformVisitor(FAIL).exec(n);
    assertThat(((RawTextNode) getNode(n, 0)).getRawText()).isEqualTo("<div id=\"foo\"></div>");
  }

  public void testConsecutiveIf() {
    String templateBody =
        ""
            + "{@param foo : ?}\n"
            + "{let $content kind=\"html\"}\n"
            + "  <div>\n"
            + "    {if $foo}Hello world{/if}"
            + "    {if $foo}\n"
            + "      <div>Hello world</div>\n"
            + "    {/if}\n"
            + "  </div>\n"
            + "{/let}";

    SoyFileSetNode n = performVisitor(templateBody, FAIL);
    assertThat(((IfCondNode) getNode(n, 0, 2, 0)).getCommandText()).isEqualTo("$foo");
    assertThat(((HtmlTextNode) getNode(n, 0, 2, 0, 0)).getRawText()).isEqualTo("Hello world");
    assertThat(((IfCondNode) getNode(n, 0, 3, 0)).getCommandText()).isEqualTo("$foo");
    assertThat(((HtmlOpenTagStartNode) getNode(n, 0, 3, 0, 0)).getTagName()).isEqualTo("div");
  }

  public void testCallInAttributesDeclaration() {
    String templateBody = "<div id=\"foo\" {call .someTemplate /}></div>";

    FormattingErrorReporter fer = new FormattingErrorReporter();
    performVisitor(templateBody, fer);

    assertThat(fer.getErrorMessages()).containsExactly("The incremental HTML Soy backend does not "
        + "support template calls within HTML tag declarations.");
  }

  public void testIfInAttributeName() {
    String templateBody = "{@param foo : ?}\n<div go{if $foo}oooo{/if}ogle=\"foo\"></div>";

    FormattingErrorReporter fer = new FormattingErrorReporter();
    performVisitor(templateBody, fer);

    assertThat(fer.getErrorMessages()).containsExactly("Soy statements are not allowed in an "
        + "attribute name declaration.");
  }

  public void testIfBeforeQuotedValue() {
    String templateBody = "{@param foo : ?}\n<div id={if $foo}\"foo\"{/if}></div>";

    FormattingErrorReporter fer = new FormattingErrorReporter();
    performVisitor(templateBody, fer);

    assertThat(fer.getErrorMessages()).contains("Soy statements are not allowed before an "
        + "attribute value. They should be moved inside a quotation mark.");
  }

  public void testMissingTagName() {
    String templateBody = "<></div>\n";

    FormattingErrorReporter fer = new FormattingErrorReporter();
    performVisitor(templateBody, fer);

    assertThat(fer.getErrorMessages()).contains("Found a tag with an empty tag name.");
  }

  public void testUnclosedTag() {
    String templateBody = "<div></div\n";

    FormattingErrorReporter fer = new FormattingErrorReporter();
    performVisitor(templateBody, fer);

    assertThat(fer.getErrorMessages()).contains("Ending context of the content within a Soy tag "
        + "must match the starting context. Transition was from PCDATA to TAG");
  }

  public void testNonQuotedAttributeValue() {
    String templateBody = "<div></div><div foo=bar></div>";

    FormattingErrorReporter fer = new FormattingErrorReporter();
    performVisitor(templateBody, fer);

    assertThat(fer.getErrorMessages()).contains("Expected to find a quoted attribute value, but "
        + "found \"b\".");
  }
}
