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

package com.google.template.soy.sharedpasses.opti;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.SoyModule;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author Kai Huang */
@RunWith(JUnit4.class)
public class SimplifyVisitorTest {

  @Test
  public void testCombineConsecutiveRawTextNodes() throws Exception {

    String soyCode =
        "{@param boo : ?}\n"
            + "blah{$boo}blah"
            + "{for $i in range(5)}"
            + "  blah{$boo}blah"
            + "{/for}";
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forTemplateContents(soyCode).parse().fileSet();

    TemplateNode template = soyTree.getChild(0).getChild(0);
    ForNode forNode = (ForNode) template.getChild(3);
    forNode.addChild(new RawTextNode(0, "bleh", SourceLocation.UNKNOWN));
    forNode.addChild(new RawTextNode(0, "bluh", SourceLocation.UNKNOWN));
    template.addChild(0, new RawTextNode(0, "bleh", SourceLocation.UNKNOWN));
    template.addChild(0, new RawTextNode(0, "bluh", SourceLocation.UNKNOWN));

    assertEquals(6, template.numChildren());
    assertEquals(5, forNode.numChildren());

    SimplifyVisitor simplifyVisitor = createSimplifyVisitor();
    simplifyVisitor.simplify(soyTree, new TemplateRegistry(soyTree, ExplodingErrorReporter.get()));

    assertEquals(4, template.numChildren());
    assertEquals(3, forNode.numChildren());
    assertEquals("bluhblehblah", ((RawTextNode) template.getChild(0)).getRawText());
    assertEquals("blahblehbluh", ((RawTextNode) forNode.getChild(2)).getRawText());
  }

  @Test
  public void testMsgBlockNodeChildrenAreNotReplaced() throws Exception {

    String soyFileContent =
        "{namespace boo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "{template .foo}\n"
            + "\n"
            + "  {msg desc=\"\"}\n"
            + "    blah\n"
            + "    {'blah'}\n"
            + "    blah\n"
            + "    {call .aaa /}\n"
            + "    blah\n"
            + "    <div class=\"{call .aaa /}\">\n"
            + "    </div>\n"
            + "    blah\n"
            + "  {/msg}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .aaa}\n"
            + "  blah\n"
            + "{/template}";

    MsgNode msgNode =
        ((MsgFallbackGroupNode)
                simplifySoyFiles(soyFileContent).getChild(0).getChild(0).getChild(0))
            .getChild(0);
    assertEquals(8, msgNode.numChildren());
    // The MsgPlaceholderNode children are not replaced.
    assertTrue(msgNode.getChild(1) instanceof MsgPlaceholderNode);
    assertTrue(msgNode.getChild(3) instanceof MsgPlaceholderNode);
    assertTrue(msgNode.getChild(5) instanceof MsgPlaceholderNode);
    assertTrue(msgNode.getChild(6) instanceof MsgPlaceholderNode);
    // But the contents within the MsgPlaceholderNode children can be replaced.
    assertTrue(((MsgPlaceholderNode) msgNode.getChild(1)).getChild(0) instanceof RawTextNode);
  }

  @Test
  public void testSimplifyPrintNode() throws Exception {

    String soyCode;

    soyCode = "{'foo'}";
    assertEquals("foo", simplifySoyCode(soyCode).get(0).toSourceString());

    soyCode = "{'<b>&</b>' |escapeHtml}";
    assertEquals("&lt;b&gt;&amp;&lt;/b&gt;", simplifySoyCode(soyCode).get(0).toSourceString());

    soyCode = "{'<b>&</b>' |escapeHtml |insertWordBreaks:5}";
    assertEquals("&lt;b&gt;&amp;&lt;<wbr>/b&gt;", simplifySoyCode(soyCode).get(0).toSourceString());

    // Doesn't simplify PrintNode with non-constant expression (but expression is simplified).
    soyCode = "{@param boo : ?}\n" + "{1 + 3 + $boo}";
    assertEquals("{4 + $boo}", simplifySoyCode(soyCode).get(0).toSourceString());

    // formatNum is not annotated as a SoyPurePrintDirective, so it should not be simplified.
    soyCode = "{5 |formatNum}";
    assertEquals("{5 |formatNum}", simplifySoyCode(soyCode).get(0).toSourceString());

    // Doesn't simplify PrintNode with non-constant directive arg.
    soyCode = "{@param boo : ?}\n" + "{'0123456789' |insertWordBreaks:$boo}";
    assertEquals(
        "{'0123456789' |insertWordBreaks:$boo}", simplifySoyCode(soyCode).get(0).toSourceString());
  }

  @Test
  public void testSimplifyIfNode() throws Exception {

    String soyCode;

    soyCode =
        "{if not false}\n"
            + "  111\n"
            + "{/if}\n"
            + "{if true and false}\n"
            + "  222\n"
            + "{/if}\n";
    assertEquals("111", simplifySoyCode(soyCode).get(0).toSourceString());

    soyCode =
        "{if ''}\n"
            + "  111\n"
            + "{elseif not 1}\n"
            + "  222\n"
            + "{else}\n"
            + "  333\n"
            + "{/if}\n";
    assertEquals("333", simplifySoyCode(soyCode).get(0).toSourceString());

    soyCode =
        "{if false}\n"
            + "  111\n"
            + "{elseif true}\n"
            + "  222\n"
            + "{else}\n"
            + "  333\n"
            + "{/if}\n";
    assertEquals("222", simplifySoyCode(soyCode).get(0).toSourceString());

    soyCode =
        "{@param boo : ?}\n"
            + "{if false}\n"
            + "  111\n"
            + "{elseif $boo}\n"
            + "  222\n"
            + "{elseif true}\n"
            + "  333\n"
            + "{else}\n"
            + "  444\n"
            + "{/if}\n";
    assertEquals("{if $boo}222{else}333{/if}", simplifySoyCode(soyCode).get(0).toSourceString());

    soyCode =
        "{@param boo : ?}\n"
            + "{if 0}\n"
            + "  111\n"
            + "{elseif 1}\n"
            + "  {if true}\n"
            + "    {if $boo}\n"
            + "      222\n"
            + "    {elseif ''}\n"
            + "      333\n"
            + "    {elseif 'blah'}\n"
            + "      444\n"
            + "    {else}\n"
            + "      555\n"
            + "    {/if}\n"
            + "  {else}\n"
            + "    666\n"
            + "  {/if}\n"
            + "{else}\n"
            + "  777\n"
            + "{/if}\n";
    assertEquals("{if $boo}222{else}444{/if}", simplifySoyCode(soyCode).get(0).toSourceString());
  }

  @Test
  public void testSimplifySwitchNode() throws Exception {

    String soyCode;

    soyCode =
        "{@param boo : ?}\n"
            + "{switch 1 + 2}\n"
            + "  {case 1}111\n"
            + "  {case 2, 3}222333\n"
            + "  {case $boo}444\n"
            + "  {default}goo\n"
            + "{/switch}\n";
    assertEquals("222333", simplifySoyCode(soyCode).get(0).toSourceString());

    soyCode =
        "{switch 1 + 2}\n"
            + "  {case 1}111\n"
            + "  {case 2}222\n"
            + "  {default}333\n"
            + "{/switch}\n";
    assertEquals("333", simplifySoyCode(soyCode).get(0).toSourceString());

    soyCode =
        "{@param boo : ?}\n"
            + "{switch 1 + 2}\n"
            + "  {case $boo}111\n"
            + "  {case 2}222\n"
            + "  {case 3}333\n"
            + "  {default}444\n"
            + "{/switch}\n";
    assertEquals(
        "{switch 3}{case $boo}111{default}333{/switch}",
        simplifySoyCode(soyCode).get(0).toSourceString());
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers.

  {
    Guice.createInjector(new SoyModule()).injectMembers(this);
  }

  @Inject ImmutableMap<String, ? extends SoyPrintDirective> printDirectives;
  @Inject SoyValueConverter valueConverter;

  private SimplifyVisitor createSimplifyVisitor() {
    return SimplifyVisitor.create(printDirectives, valueConverter);
  }

  private List<StandaloneNode> simplifySoyCode(String soyCode) throws Exception {

    ParseResult parse = SoyFileSetParserBuilder.forTemplateContents(soyCode).parse();
    SimplifyVisitor simplifyVisitor = createSimplifyVisitor();
    simplifyVisitor.simplify(parse.fileSet(), parse.registry());
    return parse.fileSet().getChild(0).getChild(0).getChildren();
  }

  private SoyFileSetNode simplifySoyFiles(String... soyFileContents) throws Exception {
    ParseResult parse = SoyFileSetParserBuilder.forFileContents(soyFileContents).parse();
    SimplifyVisitor simplifyVisitor = createSimplifyVisitor();
    simplifyVisitor.simplify(parse.fileSet(), parse.registry());
    return parse.fileSet();
  }
}
