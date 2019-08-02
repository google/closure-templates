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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.TemplateNode;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

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
    ParseResult parseResult = SoyFileSetParserBuilder.forTemplateContents(soyCode).parse();
    SoyFileSetNode soyTree = parseResult.fileSet();

    TemplateNode template = soyTree.getChild(0).getChild(0);
    ForNonemptyNode forNode = (ForNonemptyNode) ((ForNode) template.getChild(3)).getChild(0);
    forNode.addChild(new RawTextNode(0, "bleh", SourceLocation.UNKNOWN));
    forNode.addChild(new RawTextNode(0, "bluh", SourceLocation.UNKNOWN));
    template.addChild(0, new RawTextNode(0, "bleh", SourceLocation.UNKNOWN));
    template.addChild(0, new RawTextNode(0, "bluh", SourceLocation.UNKNOWN));

    assertThat(template.numChildren()).isEqualTo(6);
    assertThat(forNode.numChildren()).isEqualTo(5);

    SimplifyVisitor simplifyVisitor =
        SimplifyVisitor.create(
            soyTree.getNodeIdGenerator(), ImmutableList.copyOf(soyTree.getChildren()));
    simplifyVisitor.simplify(soyTree.getChild(0));

    assertThat(template.numChildren()).isEqualTo(4);
    assertThat(forNode.numChildren()).isEqualTo(3);
    assertThat(((RawTextNode) template.getChild(0)).getRawText()).isEqualTo("bluhblehblah");
    assertThat(((RawTextNode) forNode.getChild(2)).getRawText()).isEqualTo("blahblehbluh");
  }

  @Test
  public void testMsgBlockNodeChildrenAreNotReplaced() throws Exception {

    String soyFileContent =
        "{namespace boo}\n"
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
    assertThat(msgNode.numChildren()).isEqualTo(8);
    // The MsgPlaceholderNode children are not replaced.
    assertThat(msgNode.getChild(1)).isInstanceOf(MsgPlaceholderNode.class);
    assertThat(msgNode.getChild(3)).isInstanceOf(MsgPlaceholderNode.class);
    assertThat(msgNode.getChild(5)).isInstanceOf(MsgPlaceholderNode.class);
    assertThat(msgNode.getChild(6)).isInstanceOf(MsgPlaceholderNode.class);
    // But the contents within the MsgPlaceholderNode children can be replaced.
    assertThat(((MsgPlaceholderNode) msgNode.getChild(1)).getChild(0))
        .isInstanceOf(RawTextNode.class);
  }

  @Test
  public void testSimplifyPrintNode() throws Exception {

    String soyCode;

    soyCode = "{'foo'}";
    assertThat(simplifySoyCode(soyCode).get(0).toSourceString()).isEqualTo("foo");

    soyCode = "{'abcdefgh' |insertWordBreaks:5}";
    assertThat(simplifySoyCode(soyCode).get(0).toSourceString()).isEqualTo("abcde<wbr>fgh");

    // Doesn't simplify PrintNode with non-constant expression (but expression is simplified).
    soyCode = "{@param boo : ?}\n" + "{1 + 3 + $boo}";
    assertThat(simplifySoyCode(soyCode).get(0).toSourceString()).isEqualTo("{4 + $boo}");

    // formatNum is not annotated as a SoyPureFunction, so it should not be simplified.
    soyCode = "{formatNum(5)}";
    assertThat(simplifySoyCode(soyCode).get(0).toSourceString()).isEqualTo("{formatNum(5)}");

    // Doesn't simplify PrintNode with non-constant directive arg.
    soyCode = "{@param boo : ?}\n" + "{'0123456789' |insertWordBreaks:$boo}";
    assertThat(simplifySoyCode(soyCode).get(0).toSourceString())
        .isEqualTo("{'0123456789' |insertWordBreaks:$boo}");
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
    assertThat(simplifySoyCode(soyCode).get(0).toSourceString()).isEqualTo("111");

    soyCode =
        "{if ''}\n"
            + "  111\n"
            + "{elseif not 1}\n"
            + "  222\n"
            + "{else}\n"
            + "  333\n"
            + "{/if}\n";
    assertThat(simplifySoyCode(soyCode).get(0).toSourceString()).isEqualTo("333");

    soyCode =
        "{if false}\n"
            + "  111\n"
            + "{elseif true}\n"
            + "  222\n"
            + "{else}\n"
            + "  333\n"
            + "{/if}\n";
    assertThat(simplifySoyCode(soyCode).get(0).toSourceString()).isEqualTo("222");

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
    assertThat(simplifySoyCode(soyCode).get(0).toSourceString())
        .isEqualTo("{if $boo}222{else}333{/if}");

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
    assertThat(simplifySoyCode(soyCode).get(0).toSourceString())
        .isEqualTo("{if $boo}222{else}444{/if}");
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
    assertThat(simplifySoyCode(soyCode).get(0).toSourceString()).isEqualTo("222333");

    soyCode =
        "{switch 1 + 2}\n"
            + "  {case 1}111\n"
            + "  {case 2}222\n"
            + "  {default}333\n"
            + "{/switch}\n";
    assertThat(simplifySoyCode(soyCode).get(0).toSourceString()).isEqualTo("333");

    soyCode =
        "{@param boo : ?}\n"
            + "{switch 1 + 2}\n"
            + "  {case $boo}111\n"
            + "  {case 2}222\n"
            + "  {case 3}333\n"
            + "  {default}444\n"
            + "{/switch}\n";
    assertThat(simplifySoyCode(soyCode).get(0).toSourceString())
        .isEqualTo("{switch 3}{case $boo}111{default}333{/switch}");
  }

  private List<StandaloneNode> simplifySoyCode(String soyCode) throws Exception {
    SoyFileSetNode fileSet = SoyFileSetParserBuilder.forTemplateContents(soyCode).parse().fileSet();
    SimplifyVisitor simplifyVisitor =
        SimplifyVisitor.create(
            fileSet.getNodeIdGenerator(), ImmutableList.copyOf(fileSet.getChildren()));
    simplifyVisitor.simplify(fileSet.getChild(0));
    return fileSet.getChild(0).getChild(0).getChildren();
  }

  private SoyFileSetNode simplifySoyFiles(String... soyFileContents) throws Exception {
    SoyFileSetNode fileSet =
        SoyFileSetParserBuilder.forFileContents(soyFileContents).parse().fileSet();
    SimplifyVisitor simplifyVisitor =
        SimplifyVisitor.create(
            fileSet.getNodeIdGenerator(), ImmutableList.copyOf(fileSet.getChildren()));
    for (SoyFileNode file : fileSet.getChildren()) {
      simplifyVisitor.simplify(file);
    }
    return fileSet;
  }
}
