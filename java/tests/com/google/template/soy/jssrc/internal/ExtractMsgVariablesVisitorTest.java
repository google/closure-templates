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

package com.google.template.soy.jssrc.internal;

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.basetree.ParentNode;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachIfemptyNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for ReplaceMsgsWithGoogMsgsVisitor.
 *
 */
@RunWith(JUnit4.class)
public final class ExtractMsgVariablesVisitorTest {

  private static TemplateNode parseTemplate(String soyCode) {
    ErrorReporter boom = ExplodingErrorReporter.get();
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateContents(soyCode).errorReporter(boom).parse().fileSet();
    new ExtractMsgVariablesVisitor().exec(soyTree);
    return soyTree.getChild(0).getChild(0);
  }

  /** Gets the MsgFallbackGroupNode at the specified index, unwrapping its surrounding {let} node */
  private MsgFallbackGroupNode getMsgFallbackGroupNode(ParentNode<?> parent, int index) {
    return (MsgFallbackGroupNode) ((LetContentNode) parent.getChild(index)).getChild(0);
  }

  @Test
  public void testReplacement() {

    TemplateNode template =
        parseTemplate(
            ""
                + "{@param userName: ?}\n"
                + "{@param url: ?}\n"
                + "{msg desc=\"Tells the user to click a link.\"}\n"
                + "  Hello {$userName}, please click <a href=\"{$url}\">here</a>.\n"
                + "{/msg}\n"
                + "{msg meaning=\"blah\" desc=\"A span with generated id.\" hidden=\"true\"}\n"
                + "  <span id=\"{for $i in range(3)}{$i}{/for}\">\n"
                + "{/msg}\n");

    MsgFallbackGroupNode gmd0 = getMsgFallbackGroupNode(template, 1);
    MsgNode m0 = gmd0.getChild(0);
    assertThat(m0.getMeaning()).isNull();
    assertThat(m0.getDesc()).isEqualTo("Tells the user to click a link.");
    assertThat(m0.isHidden()).isFalse();

    assertThat(m0.numChildren()).isEqualTo(7);
    assertThat(((RawTextNode) m0.getChild(0)).getRawText()).isEqualTo("Hello ");
    MsgPlaceholderNode gm0p1 = (MsgPlaceholderNode) m0.getChild(1);
    assertThat(m0.getRepPlaceholderNode("USER_NAME")).isEqualTo(gm0p1);
    assertThat(m0.getPlaceholderName(gm0p1)).isEqualTo("USER_NAME");
    PrintNode gm0pc1 = (PrintNode) gm0p1.getChild(0);
    assertThat(gm0pc1.getExpr().toSourceString()).isEqualTo("$userName");
    assertThat(((RawTextNode) m0.getChild(2)).getRawText()).isEqualTo(", please click ");
    MsgPlaceholderNode gm0p3 = (MsgPlaceholderNode) m0.getChild(3);
    assertThat(m0.getRepPlaceholderNode("START_LINK")).isEqualTo(gm0p3);
    assertThat(m0.getPlaceholderName(gm0p3)).isEqualTo("START_LINK");
    MsgHtmlTagNode gm0pc3 = (MsgHtmlTagNode) gm0p3.getChild(0);
    assertThat(gm0pc3.numChildren()).isEqualTo(3);
    assertThat(((PrintNode) gm0pc3.getChild(1)).getExpr().toSourceString()).isEqualTo("$url");
    assertThat(((RawTextNode) m0.getChild(4)).getRawText()).isEqualTo("here");
    MsgPlaceholderNode gm0p5 = (MsgPlaceholderNode) m0.getChild(5);
    assertThat(m0.getRepPlaceholderNode("END_LINK")).isEqualTo(gm0p5);
    assertThat(m0.getPlaceholderName(gm0p5)).isEqualTo("END_LINK");
    MsgHtmlTagNode gm0pc5 = (MsgHtmlTagNode) gm0p5.getChild(0);
    assertThat(gm0pc5.numChildren()).isEqualTo(1);
    assertThat(((RawTextNode) gm0pc5.getChild(0)).getRawText()).isEqualTo("</a>");
    assertThat(((RawTextNode) m0.getChild(6)).getRawText()).isEqualTo(".");

    MsgFallbackGroupNode gmd2 = getMsgFallbackGroupNode(template, 0);
    MsgNode m2 = gmd2.getChild(0);
    assertThat(m2.getMeaning()).isEqualTo("blah");
    assertThat(m2.getDesc()).isEqualTo("A span with generated id.");
    assertThat(m2.isHidden()).isTrue();

    assertThat(m2.numChildren()).isEqualTo(1);
    MsgPlaceholderNode gm2p0 = (MsgPlaceholderNode) m2.getChild(0);
    assertThat(m2.getRepPlaceholderNode("START_SPAN")).isEqualTo(gm2p0);
    assertThat(m2.getPlaceholderName(gm2p0)).isEqualTo("START_SPAN");
    MsgHtmlTagNode gm2pc0 = (MsgHtmlTagNode) gm2p0.getChild(0);
    assertThat(gm2pc0.numChildren()).isEqualTo(3);
    assertThat(gm2pc0.getChild(1)).isInstanceOf(ForNode.class);
  }

  @Test
  public void testLetPositionBasic() {

    TemplateNode template =
        parseTemplate(
            ""
                + "{@param goo: ?}\n"
                + "{msg desc=\"msg1\"}blah{/msg}\n"
                + "blah\n"
                + "{msg desc=\"msg2\"}{$goo}{/msg}\n"
                + "{$goo}\n"
                + "{msg desc=\"msg3\"}blah{/msg}\n");

    // Expected:
    // [TemplateNode]
    //   |--[MsgFallbackGroupNode]  msg3
    //   |--[MsgFallbackGroupNode]  msg2
    //   |--[MsgFallbackGroupNode]  msg1
    //   |--[PrintNode]
    //   |--[RawTextNode]  "blah"
    //   |--[PrintNode]
    //   |--[PrintNode]  $goo
    //   +--[PrintNode]

    assertThat(template.numChildren()).isEqualTo(8);
    assertThat(getMsgFallbackGroupNode(template, 0).getChild(0).getDesc()).isEqualTo("msg3");
    assertThat(getMsgFallbackGroupNode(template, 1).getChild(0).getDesc()).isEqualTo("msg2");
    assertThat(getMsgFallbackGroupNode(template, 2).getChild(0).getDesc()).isEqualTo("msg1");
  }

  @Test
  public void testLetPositionIf() {

    TemplateNode template =
        parseTemplate(
            ""
                + "{@param goo1: ?}\n"
                + "{@param goo2: ?}\n"
                + "{if $goo1}\n"
                + "  {msg desc=\"msg1\"}blah{/msg}\n"
                + "  {msg desc=\"msg2\"}blah{/msg}\n"
                + "{elseif $goo2}\n"
                + "  {msg desc=\"msg3\"}blah{/msg}\n"
                + "  {msg desc=\"msg4\"}blah{/msg}\n"
                + "{else}\n"
                + "  {msg desc=\"msg5\"}blah{/msg}\n"
                + "  {msg desc=\"msg6\"}blah{/msg}\n"
                + "{/if}\n");

    // Expected:
    // [TemplateNode]
    //   +--[IfNode]
    //        |--[IfCondNode]  $goo1
    //        |    |--[MsgFallbackGroupNode]  msg2
    //        |    |--[MsgFallbackGroupNode]  msg1
    //        |    |--[PrintNode]
    //        |    +--[PrintNode]
    //        |--[IfCondNode]  $goo2
    //        |    |--[MsgFallbackGroupNode]  msg4
    //        |    |--[MsgFallbackGroupNode]  msg3
    //        |    |--[PrintNode]
    //        |    +--[PrintNode]
    //        +--[IfElseNode]
    //             |--[MsgFallbackGroupNode]  msg6
    //             |--[MsgFallbackGroupNode]  msg5
    //             |--[PrintNode]
    //             +--[PrintNode]

    assertThat(template.numChildren()).isEqualTo(1);
    IfNode ifNode = (IfNode) template.getChild(0);
    assertThat(ifNode.numChildren()).isEqualTo(3);
    IfCondNode icn0 = (IfCondNode) ifNode.getChild(0);
    assertThat(icn0.numChildren()).isEqualTo(4);
    assertThat(getMsgFallbackGroupNode(icn0, 0).getChild(0).getDesc()).isEqualTo("msg2");
    assertThat(getMsgFallbackGroupNode(icn0, 1).getChild(0).getDesc()).isEqualTo("msg1");
    IfCondNode icn1 = (IfCondNode) ifNode.getChild(1);
    assertThat(icn1.numChildren()).isEqualTo(4);
    assertThat(getMsgFallbackGroupNode(icn1, 0).getChild(0).getDesc()).isEqualTo("msg4");
    assertThat(getMsgFallbackGroupNode(icn1, 1).getChild(0).getDesc()).isEqualTo("msg3");
    IfElseNode ien2 = (IfElseNode) ifNode.getChild(2);
    assertThat(ien2.numChildren()).isEqualTo(4);
    assertThat(getMsgFallbackGroupNode(ien2, 0).getChild(0).getDesc()).isEqualTo("msg6");
    assertThat(getMsgFallbackGroupNode(ien2, 1).getChild(0).getDesc()).isEqualTo("msg5");
  }

  @Test
  public void testLetPositionSwitch() {

    TemplateNode template =
        parseTemplate(
            ""
                + "{@param goo: ?}\n"
                + "{switch $goo}\n"
                + "  {case 0}\n"
                + "    {msg desc=\"msg1\"}blah{/msg}\n"
                + "    {msg desc=\"msg2\"}blah{/msg}\n"
                + "  {case 1, 2, 3}\n"
                + "    {msg desc=\"msg3\"}blah{/msg}\n"
                + "    {msg desc=\"msg4\"}blah{/msg}\n"
                + "  {default}\n"
                + "    {msg desc=\"msg5\"}blah{/msg}\n"
                + "    {msg desc=\"msg6\"}blah{/msg}\n"
                + "{/switch}\n");

    // Expected:
    // [TemplateNode]
    //   +--[SwitchNode]
    //        |--[SwitchCaseNode]  0
    //        |    |--[MsgFallbackGroupNode]  msg2
    //        |    |--[MsgFallbackGroupNode]  msg1
    //        |    |--[PrintNode]
    //        |    +--[PrintNode]
    //        |--[SwitchCaseNode]  1, 2, 3
    //        |    |--[MsgFallbackGroupNode]  msg4
    //        |    |--[MsgFallbackGroupNode]  msg3
    //        |    |--[PrintNode]
    //        |    +--[PrintNode]
    //        +--[SwitchDefaultNode]
    //             |--[MsgFallbackGroupNode]  msg6
    //             |--[MsgFallbackGroupNode]  msg5
    //             |--[PrintNode]
    //             +--[PrintNode]

    assertThat(template.numChildren()).isEqualTo(1);
    SwitchNode switchNode = (SwitchNode) template.getChild(0);
    assertThat(switchNode.numChildren()).isEqualTo(3);
    SwitchCaseNode scn0 = (SwitchCaseNode) switchNode.getChild(0);
    assertThat(scn0.numChildren()).isEqualTo(4);
    assertThat(getMsgFallbackGroupNode(scn0, 0).getChild(0).getDesc()).isEqualTo("msg2");
    assertThat(getMsgFallbackGroupNode(scn0, 1).getChild(0).getDesc()).isEqualTo("msg1");
    SwitchCaseNode scn1 = (SwitchCaseNode) switchNode.getChild(1);
    assertThat(scn1.numChildren()).isEqualTo(4);
    assertThat(getMsgFallbackGroupNode(scn1, 0).getChild(0).getDesc()).isEqualTo("msg4");
    assertThat(getMsgFallbackGroupNode(scn1, 1).getChild(0).getDesc()).isEqualTo("msg3");
    SwitchDefaultNode sdn2 = (SwitchDefaultNode) switchNode.getChild(2);
    assertThat(sdn2.numChildren()).isEqualTo(4);
    assertThat(getMsgFallbackGroupNode(sdn2, 0).getChild(0).getDesc()).isEqualTo("msg6");
    assertThat(getMsgFallbackGroupNode(sdn2, 1).getChild(0).getDesc()).isEqualTo("msg5");
  }

  @Test
  public void testLetPositionForeach() {

    TemplateNode template =
        parseTemplate(
            ""
                + "{@param goose: ?}\n"
                + "{@param moo: ?}\n"
                + "{foreach $goo in $goose}\n"
                + "  {msg desc=\"msg1\"}blah{/msg}\n"
                + "  {msg desc=\"msg2\"}{$goo}{/msg}\n"
                + // dep on $goo
                "  {msg desc=\"msg3\"}{$moo}{/msg}\n"
                + "  {msg desc=\"msg4\"}{$goo}{/msg}\n"
                + // dep on $goo
                "{ifempty}\n"
                + "  {msg desc=\"msg5\"}{$moo}{/msg}\n"
                + "  {msg desc=\"msg6\"}blah{/msg}\n"
                + "{/foreach}\n");

    // Expected:
    // [TemplateNode]
    //   |--[MsgFallbackGroupNode]  msg3
    //   |--[MsgFallbackGroupNode]  msg1
    //   +--[ForeachNode]  $goo in $goose
    //        |--[ForeachNonemptyNode]
    //        |    |--[MsgFallbackGroupNode]  msg4
    //        |    |--[MsgFallbackGroupNode]  msg2
    //        |    |--[PrintNode]
    //        |    |--[PrintNode]
    //        |    |--[PrintNode]
    //        |    +--[PrintNode]
    //        +--[ForeachIfemptyNode]
    //             |--[MsgFallbackGroupNode]  msg6
    //             |--[MsgFallbackGroupNode]  msg5
    //             |--[PrintNode]
    //             +--[PrintNode]

    assertThat(template.numChildren()).isEqualTo(3);
    assertThat(getMsgFallbackGroupNode(template, 0).getChild(0).getDesc()).isEqualTo("msg3");
    assertThat(getMsgFallbackGroupNode(template, 1).getChild(0).getDesc()).isEqualTo("msg1");

    ForeachNode foreachNode = (ForeachNode) template.getChild(2);
    ForeachNonemptyNode fnn0 = (ForeachNonemptyNode) foreachNode.getChild(0);
    assertThat(fnn0.numChildren()).isEqualTo(6);
    assertThat(getMsgFallbackGroupNode(fnn0, 0).getChild(0).getDesc()).isEqualTo("msg4");
    assertThat(getMsgFallbackGroupNode(fnn0, 1).getChild(0).getDesc()).isEqualTo("msg2");
    ForeachIfemptyNode fin1 = (ForeachIfemptyNode) foreachNode.getChild(1);
    assertThat(fin1.numChildren()).isEqualTo(4);
    assertThat(getMsgFallbackGroupNode(fin1, 0).getChild(0).getDesc()).isEqualTo("msg6");
    assertThat(getMsgFallbackGroupNode(fin1, 1).getChild(0).getDesc()).isEqualTo("msg5");
  }

  @Test
  public void testLetPositionFor() {

    TemplateNode template =
        parseTemplate(
            ""
                + "{@param moo: ?}\n"
                + "{for $i in range(3)}\n"
                + "  {msg desc=\"msg1\"}blah{/msg}\n"
                + "  {msg desc=\"msg2\"}{$i}{/msg}\n"
                + // dep on $i
                "  {msg desc=\"msg3\"}{$moo}{/msg}\n"
                + "  {msg desc=\"msg4\"}{$i}{/msg}\n"
                + // dep on $i
                "{/for}\n");

    // Expected:
    // [TemplateNode]
    //   |--[MsgFallbackGroupNode]  msg3
    //   |--[MsgFallbackGroupNode]  msg1
    //   +--[ForNode]  $i in range(3)
    //        |--[MsgFallbackGroupNode]  msg4
    //        |--[MsgFallbackGroupNode]  msg2
    //        |--[PrintNode]
    //        |--[PrintNode]
    //        |--[PrintNode]
    //        +--[PrintNode]

    assertThat(template.numChildren()).isEqualTo(3);
    assertThat(getMsgFallbackGroupNode(template, 0).getChild(0).getDesc()).isEqualTo("msg3");
    assertThat(getMsgFallbackGroupNode(template, 1).getChild(0).getDesc()).isEqualTo("msg1");

    ForNode forNode = (ForNode) template.getChild(2);
    assertThat(forNode.numChildren()).isEqualTo(6);
    assertThat(getMsgFallbackGroupNode(forNode, 0).getChild(0).getDesc()).isEqualTo("msg4");
    assertThat(getMsgFallbackGroupNode(forNode, 1).getChild(0).getDesc()).isEqualTo("msg2");
  }

  @Test
  public void testLetPositionNested() {

    TemplateNode template =
        parseTemplate(
            ""
                + "{@param goo: ?}\n"
                + "{@param moose: ?}\n"
                + "{@param zoo: ?}\n"
                + "{if $goo}\n"
                + "  {foreach $moo in $moose}\n"
                + "    {for $i in range(3)}\n"
                + "      {msg desc=\"msg1\"}blah{/msg}\n"
                + "      {msg desc=\"msg2\"}{$goo}{/msg}\n"
                + // dep on $goo (irrelevant)
                "      {msg desc=\"msg3\"}{$moo}{/msg}\n"
                + // dep on $moo
                "      {msg desc=\"msg4\"}{$i}{/msg}\n"
                + // dep on $i
                "      {msg desc=\"msg5\"}{$zoo}{/msg}\n"
                + "      {msg desc=\"msg6\"}{$goo}{$moo}{/msg}\n"
                + // dep on $goo (irrelevant) and $moo
                "      {msg desc=\"msg7\"}{$goo}{$i}{/msg}\n"
                + // dep on $goo (irrelevant) and $i
                "      {msg desc=\"msg8\"}{$moo}{$i}{/msg}\n"
                + // dep on $moo and $i
                "    {/for}\n"
                + "  {/foreach}\n"
                + "{/if}\n"
                + "{msg desc=\"msg9\"}{$goo}{/msg}\n"); // dep on $goo (irrelevant)

    // Expected:
    // [TemplateNode]
    //   |--[MsgFallbackGroupNode]  msg9
    //   |--[IfNode]
    //   |    +--[IfCondNode]  $goo
    //   |         |--[MsgFallbackGroupNode]  msg5
    //   |         |--[MsgFallbackGroupNode]  msg2
    //   |         |--[MsgFallbackGroupNode]  msg1
    //   |         +--[ForeachNode]  $moo in $moose
    //   |              +--[ForeachNonemptyNode]
    //   |                   |--[MsgFallbackGroupNode]  msg6
    //   |                   |--[MsgFallbackGroupNode]  msg3
    //   |                   +--[ForNode]
    //   |                        |--[MsgFallbackGroupNode]  msg8
    //   |                        |--[MsgFallbackGroupNode]  msg7
    //   |                        |--[MsgFallbackGroupNode]  msg4
    //   |                        |--[PrintNode]
    //   |                        |--[PrintNode]
    //   |                        |--[PrintNode]
    //   |                        |--[PrintNode]
    //   |                        |--[PrintNode]
    //   |                        |--[PrintNode]
    //   |                        |--[PrintNode]
    //   |                        +--[PrintNode]
    //   +--[PrintNode]

    assertThat(template.numChildren()).isEqualTo(3);
    assertThat(getMsgFallbackGroupNode(template, 0).getChild(0).getDesc()).isEqualTo("msg9");

    IfCondNode ifCondNode = (IfCondNode) ((IfNode) template.getChild(1)).getChild(0);
    assertThat(ifCondNode.numChildren()).isEqualTo(4);
    assertThat(getMsgFallbackGroupNode(ifCondNode, 0).getChild(0).getDesc()).isEqualTo("msg5");
    assertThat(getMsgFallbackGroupNode(ifCondNode, 1).getChild(0).getDesc()).isEqualTo("msg2");
    assertThat(getMsgFallbackGroupNode(ifCondNode, 2).getChild(0).getDesc()).isEqualTo("msg1");

    ForeachNonemptyNode foreachNonemptyNode =
        (ForeachNonemptyNode) ((ForeachNode) ifCondNode.getChild(3)).getChild(0);
    assertThat(foreachNonemptyNode.numChildren()).isEqualTo(3);
    assertThat(getMsgFallbackGroupNode(foreachNonemptyNode, 0).getChild(0).getDesc())
        .isEqualTo("msg6");
    assertThat(getMsgFallbackGroupNode(foreachNonemptyNode, 1).getChild(0).getDesc())
        .isEqualTo("msg3");

    ForNode forNode = (ForNode) foreachNonemptyNode.getChild(2);
    assertThat(forNode.numChildren()).isEqualTo(11);
    assertThat(getMsgFallbackGroupNode(forNode, 0).getChild(0).getDesc()).isEqualTo("msg8");
    assertThat(getMsgFallbackGroupNode(forNode, 1).getChild(0).getDesc()).isEqualTo("msg7");
    assertThat(getMsgFallbackGroupNode(forNode, 2).getChild(0).getDesc()).isEqualTo("msg4");
  }
}
