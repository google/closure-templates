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
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachIfemptyNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.jssrc.GoogMsgDefNode;

import junit.framework.TestCase;

/**
 * Unit tests for MoveGoogMsgDefNodesEarlierVisitor.
 *
 */
public final class MoveGoogMsgDefNodesEarlierVisitorTest extends TestCase {

  private static final ErrorReporter FAIL = ExplodingErrorReporter.get();

  public void testBasic() {

    String soyCode =
        "{@param goo: ?}\n"
            + "{msg desc=\"msg1\"}blah{/msg}\n"
            + "blah\n"
            + "{msg desc=\"msg2\"}{$goo}{/msg}\n"
            + "{$goo}\n"
            + "{msg desc=\"msg3\"}blah{/msg}\n";

    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateContents(soyCode).errorReporter(FAIL).parse().fileSet();
    new ReplaceMsgsWithGoogMsgsVisitor().exec(soyTree);
    new MoveGoogMsgDefNodesEarlierVisitor().exec(soyTree);
    TemplateNode template = soyTree.getChild(0).getChild(0);

    // Expected:
    // [TemplateNode]
    //   |--[GoogMsgDefNode]  msg1
    //   |--[GoogMsgDefNode]  msg2
    //   |--[GoogMsgDefNode]  msg3
    //   |--[GoogMsgRefNode]
    //   |--[RawTextNode]  "blah"
    //   |--[GoogMsgRefNode]
    //   |--[PrintNode]  $goo
    //   +--[GoogMsgRefNode]

    assertThat(template.numChildren()).isEqualTo(8);
    assertThat(((GoogMsgDefNode) template.getChild(0)).getChild(0).getDesc()).isEqualTo("msg1");
    assertThat(((GoogMsgDefNode) template.getChild(1)).getChild(0).getDesc()).isEqualTo("msg2");
    assertThat(((GoogMsgDefNode) template.getChild(2)).getChild(0).getDesc()).isEqualTo("msg3");
  }


  public void testIf() {

    String soyCode =
        "{@param goo1: ?}\n"
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
            + "{/if}\n";

    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateContents(soyCode).errorReporter(FAIL).parse().fileSet();
    new ReplaceMsgsWithGoogMsgsVisitor().exec(soyTree);
    new MoveGoogMsgDefNodesEarlierVisitor().exec(soyTree);
    TemplateNode template = soyTree.getChild(0).getChild(0);

    // Expected:
    // [TemplateNode]
    //   +--[IfNode]
    //        |--[IfCondNode]  $goo1
    //        |    |--[GoogMsgDefNode]  msg1
    //        |    |--[GoogMsgDefNode]  msg2
    //        |    |--[GoogMsgRefNode]
    //        |    +--[GoogMsgRefNode]
    //        |--[IfCondNode]  $goo2
    //        |    |--[GoogMsgDefNode]  msg3
    //        |    |--[GoogMsgDefNode]  msg4
    //        |    |--[GoogMsgRefNode]
    //        |    +--[GoogMsgRefNode]
    //        +--[IfElseNode]
    //             |--[GoogMsgDefNode]  msg5
    //             |--[GoogMsgDefNode]  msg6
    //             |--[GoogMsgRefNode]
    //             +--[GoogMsgRefNode]

    assertThat(template.numChildren()).isEqualTo(1);
    IfNode ifNode = (IfNode) template.getChild(0);
    assertThat(ifNode.numChildren()).isEqualTo(3);
    IfCondNode icn0 = (IfCondNode) ifNode.getChild(0);
    assertThat(icn0.numChildren()).isEqualTo(4);
    assertThat(((GoogMsgDefNode) icn0.getChild(0)).getChild(0).getDesc()).isEqualTo("msg1");
    assertThat(((GoogMsgDefNode) icn0.getChild(1)).getChild(0).getDesc()).isEqualTo("msg2");
    IfCondNode icn1 = (IfCondNode) ifNode.getChild(1);
    assertThat(icn1.numChildren()).isEqualTo(4);
    assertThat(((GoogMsgDefNode) icn1.getChild(0)).getChild(0).getDesc()).isEqualTo("msg3");
    assertThat(((GoogMsgDefNode) icn1.getChild(1)).getChild(0).getDesc()).isEqualTo("msg4");
    IfElseNode ien2 = (IfElseNode) ifNode.getChild(2);
    assertThat(ien2.numChildren()).isEqualTo(4);
    assertThat(((GoogMsgDefNode) ien2.getChild(0)).getChild(0).getDesc()).isEqualTo("msg5");
    assertThat(((GoogMsgDefNode) ien2.getChild(1)).getChild(0).getDesc()).isEqualTo("msg6");
  }


  public void testSwitch() {

    String soyCode =
        "{@param goo: ?}\n"
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
            + "{/switch}\n";

    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateContents(soyCode).errorReporter(FAIL).parse().fileSet();
    new ReplaceMsgsWithGoogMsgsVisitor().exec(soyTree);
    new MoveGoogMsgDefNodesEarlierVisitor().exec(soyTree);
    TemplateNode template = soyTree.getChild(0).getChild(0);

    // Expected:
    // [TemplateNode]
    //   +--[SwitchNode]
    //        |--[SwitchCaseNode]  0
    //        |    |--[GoogMsgDefNode]  msg1
    //        |    |--[GoogMsgDefNode]  msg2
    //        |    |--[GoogMsgRefNode]
    //        |    +--[GoogMsgRefNode]
    //        |--[SwitchCaseNode]  1, 2, 3
    //        |    |--[GoogMsgDefNode]  msg3
    //        |    |--[GoogMsgDefNode]  msg4
    //        |    |--[GoogMsgRefNode]
    //        |    +--[GoogMsgRefNode]
    //        +--[SwitchDefaultNode]
    //             |--[GoogMsgDefNode]  msg5
    //             |--[GoogMsgDefNode]  msg6
    //             |--[GoogMsgRefNode]
    //             +--[GoogMsgRefNode]

    assertThat(template.numChildren()).isEqualTo(1);
    SwitchNode switchNode = (SwitchNode) template.getChild(0);
    assertThat(switchNode.numChildren()).isEqualTo(3);
    SwitchCaseNode scn0 = (SwitchCaseNode) switchNode.getChild(0);
    assertThat(scn0.numChildren()).isEqualTo(4);
    assertThat(((GoogMsgDefNode) scn0.getChild(0)).getChild(0).getDesc()).isEqualTo("msg1");
    assertThat(((GoogMsgDefNode) scn0.getChild(1)).getChild(0).getDesc()).isEqualTo("msg2");
    SwitchCaseNode scn1 = (SwitchCaseNode) switchNode.getChild(1);
    assertThat(scn1.numChildren()).isEqualTo(4);
    assertThat(((GoogMsgDefNode) scn1.getChild(0)).getChild(0).getDesc()).isEqualTo("msg3");
    assertThat(((GoogMsgDefNode) scn1.getChild(1)).getChild(0).getDesc()).isEqualTo("msg4");
    SwitchDefaultNode sdn2 = (SwitchDefaultNode) switchNode.getChild(2);
    assertThat(sdn2.numChildren()).isEqualTo(4);
    assertThat(((GoogMsgDefNode) sdn2.getChild(0)).getChild(0).getDesc()).isEqualTo("msg5");
    assertThat(((GoogMsgDefNode) sdn2.getChild(1)).getChild(0).getDesc()).isEqualTo("msg6");
  }


  public void testForeach() {

    String soyCode =
        "{@param goose: ?}\n"
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
            + "{/foreach}\n";

    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateContents(soyCode).errorReporter(FAIL).parse().fileSet();
    new ReplaceMsgsWithGoogMsgsVisitor().exec(soyTree);
    new MoveGoogMsgDefNodesEarlierVisitor().exec(soyTree);
    TemplateNode template = soyTree.getChild(0).getChild(0);

    // Expected:
    // [TemplateNode]
    //   |--[GoogMsgDefNode]  msg1
    //   |--[GoogMsgDefNode]  msg3
    //   +--[ForeachNode]  $goo in $goose
    //        |--[ForeachNonemptyNode]
    //        |    |--[GoogMsgDefNode]  msg2
    //        |    |--[GoogMsgDefNode]  msg4
    //        |    |--[GoogMsgRefNode]
    //        |    |--[GoogMsgRefNode]
    //        |    |--[GoogMsgRefNode]
    //        |    +--[GoogMsgRefNode]
    //        +--[ForeachIfemptyNode]
    //             |--[GoogMsgDefNode]  msg5
    //             |--[GoogMsgDefNode]  msg6
    //             |--[GoogMsgRefNode]
    //             +--[GoogMsgRefNode]

    assertThat(template.numChildren()).isEqualTo(3);
    assertThat(((GoogMsgDefNode) template.getChild(0)).getChild(0).getDesc()).isEqualTo("msg1");
    assertThat(((GoogMsgDefNode) template.getChild(1)).getChild(0).getDesc()).isEqualTo("msg3");

    ForeachNode foreachNode = (ForeachNode) template.getChild(2);
    ForeachNonemptyNode fnn0 = (ForeachNonemptyNode) foreachNode.getChild(0);
    assertThat(fnn0.numChildren()).isEqualTo(6);
    assertThat(((GoogMsgDefNode) fnn0.getChild(0)).getChild(0).getDesc()).isEqualTo("msg2");
    assertThat(((GoogMsgDefNode) fnn0.getChild(1)).getChild(0).getDesc()).isEqualTo("msg4");
    ForeachIfemptyNode fin1 = (ForeachIfemptyNode) foreachNode.getChild(1);
    assertThat(fin1.numChildren()).isEqualTo(4);
    assertThat(((GoogMsgDefNode) fin1.getChild(0)).getChild(0).getDesc()).isEqualTo("msg5");
    assertThat(((GoogMsgDefNode) fin1.getChild(1)).getChild(0).getDesc()).isEqualTo("msg6");
  }


  public void testFor() {

    String soyCode =
        "{@param moo: ?}\n"
            + "{for $i in range(3)}\n"
            + "  {msg desc=\"msg1\"}blah{/msg}\n"
            + "  {msg desc=\"msg2\"}{$i}{/msg}\n"
            + // dep on $i
            "  {msg desc=\"msg3\"}{$moo}{/msg}\n"
            + "  {msg desc=\"msg4\"}{$i}{/msg}\n"
            + // dep on $i
            "{/for}\n";

    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateContents(soyCode).errorReporter(FAIL).parse().fileSet();
    new ReplaceMsgsWithGoogMsgsVisitor().exec(soyTree);
    new MoveGoogMsgDefNodesEarlierVisitor().exec(soyTree);
    TemplateNode template = soyTree.getChild(0).getChild(0);

    // Expected:
    // [TemplateNode]
    //   |--[GoogMsgDefNode]  msg1
    //   |--[GoogMsgDefNode]  msg3
    //   +--[ForNode]  $i in range(3)
    //        |--[GoogMsgDefNode]  msg2
    //        |--[GoogMsgDefNode]  msg4
    //        |--[GoogMsgRefNode]
    //        |--[GoogMsgRefNode]
    //        |--[GoogMsgRefNode]
    //        +--[GoogMsgRefNode]

    assertThat(template.numChildren()).isEqualTo(3);
    assertThat(((GoogMsgDefNode) template.getChild(0)).getChild(0).getDesc()).isEqualTo("msg1");
    assertThat(((GoogMsgDefNode) template.getChild(1)).getChild(0).getDesc()).isEqualTo("msg3");

    ForNode forNode = (ForNode) template.getChild(2);
    assertThat(forNode.numChildren()).isEqualTo(6);
    assertThat(((GoogMsgDefNode) forNode.getChild(0)).getChild(0).getDesc()).isEqualTo("msg2");
    assertThat(((GoogMsgDefNode) forNode.getChild(1)).getChild(0).getDesc()).isEqualTo("msg4");
  }


  public void testNested() {

    String soyCode =
        "{@param goo: ?}\n"
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
            + "{msg desc=\"msg9\"}{$goo}{/msg}\n"; // dep on $goo (irrelevant)

    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateContents(soyCode).errorReporter(FAIL).parse().fileSet();
    new ReplaceMsgsWithGoogMsgsVisitor().exec(soyTree);
    new MoveGoogMsgDefNodesEarlierVisitor().exec(soyTree);
    TemplateNode template = soyTree.getChild(0).getChild(0);

    // Expected:
    // [TemplateNode]
    //   |--[GoogMsgDefNode]  msg9
    //   |--[IfNode]
    //   |    +--[IfCondNode]  $goo
    //   |         |--[GoogMsgDefNode]  msg1
    //   |         |--[GoogMsgDefNode]  msg2
    //   |         |--[GoogMsgDefNode]  msg5
    //   |         +--[ForeachNode]  $moo in $moose
    //   |              +--[ForeachNonemptyNode]
    //   |                   |--[GoogMsgDefNode]  msg3
    //   |                   |--[GoogMsgDefNode]  msg6
    //   |                   +--[ForNode]
    //   |                        |--[GoogMsgDefNode]  msg4
    //   |                        |--[GoogMsgDefNode]  msg7
    //   |                        |--[GoogMsgDefNode]  msg8
    //   |                        |--[GoogMsgRefNode]
    //   |                        |--[GoogMsgRefNode]
    //   |                        |--[GoogMsgRefNode]
    //   |                        |--[GoogMsgRefNode]
    //   |                        |--[GoogMsgRefNode]
    //   |                        |--[GoogMsgRefNode]
    //   |                        |--[GoogMsgRefNode]
    //   |                        +--[GoogMsgRefNode]
    //   +--[GoogMsgRefNode]

    assertThat(template.numChildren()).isEqualTo(3);
    assertThat(((GoogMsgDefNode) template.getChild(0)).getChild(0).getDesc()).isEqualTo("msg9");

    IfCondNode ifCondNode = (IfCondNode) ((IfNode) template.getChild(1)).getChild(0);
    assertThat(ifCondNode.numChildren()).isEqualTo(4);
    assertThat(((GoogMsgDefNode) ifCondNode.getChild(0)).getChild(0).getDesc()).isEqualTo("msg1");
    assertThat(((GoogMsgDefNode) ifCondNode.getChild(1)).getChild(0).getDesc()).isEqualTo("msg2");
    assertThat(((GoogMsgDefNode) ifCondNode.getChild(2)).getChild(0).getDesc()).isEqualTo("msg5");

    ForeachNonemptyNode foreachNonemptyNode =
        (ForeachNonemptyNode) ((ForeachNode) ifCondNode.getChild(3)).getChild(0);
    assertThat(foreachNonemptyNode.numChildren()).isEqualTo(3);
    assertThat(((GoogMsgDefNode) foreachNonemptyNode.getChild(0)).getChild(0).getDesc())
        .isEqualTo("msg3");
    assertThat(((GoogMsgDefNode) foreachNonemptyNode.getChild(1)).getChild(0).getDesc())
        .isEqualTo("msg6");

    ForNode forNode = (ForNode) foreachNonemptyNode.getChild(2);
    assertThat(forNode.numChildren()).isEqualTo(11);
    assertThat(((GoogMsgDefNode) forNode.getChild(0)).getChild(0).getDesc()).isEqualTo("msg4");
    assertThat(((GoogMsgDefNode) forNode.getChild(1)).getChild(0).getDesc()).isEqualTo("msg7");
    assertThat(((GoogMsgDefNode) forNode.getChild(2)).getChild(0).getDesc()).isEqualTo("msg8");
  }

}
