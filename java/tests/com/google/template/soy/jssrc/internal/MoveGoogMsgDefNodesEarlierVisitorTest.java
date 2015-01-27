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
public class MoveGoogMsgDefNodesEarlierVisitorTest extends TestCase {


  public void testBasic() {

    String soyCode =
        "{msg desc=\"msg1\"}blah{/msg}\n" +
        "blah\n" +
        "{msg desc=\"msg2\"}{$goo}{/msg}\n" +
        "{$goo}\n" +
        "{msg desc=\"msg3\"}blah{/msg}\n";

    SoyFileSetNode soyTree = JsSrcTestUtils.parseSoyCode(soyCode);
    (new MoveGoogMsgDefNodesEarlierVisitor()).exec(soyTree);
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

    assertEquals(8, template.numChildren());
    assertEquals("msg1", ((GoogMsgDefNode) template.getChild(0)).getChild(0).getDesc());
    assertEquals("msg2", ((GoogMsgDefNode) template.getChild(1)).getChild(0).getDesc());
    assertEquals("msg3", ((GoogMsgDefNode) template.getChild(2)).getChild(0).getDesc());
  }


  public void testIf() {

    String soyCode =
        "{if $goo1}\n" +
        "  {msg desc=\"msg1\"}blah{/msg}\n" +
        "  {msg desc=\"msg2\"}blah{/msg}\n" +
        "{elseif $goo2}\n" +
        "  {msg desc=\"msg3\"}blah{/msg}\n" +
        "  {msg desc=\"msg4\"}blah{/msg}\n" +
        "{else}\n" +
        "  {msg desc=\"msg5\"}blah{/msg}\n" +
        "  {msg desc=\"msg6\"}blah{/msg}\n" +
        "{/if}\n";

    SoyFileSetNode soyTree = JsSrcTestUtils.parseSoyCode(soyCode);
    (new MoveGoogMsgDefNodesEarlierVisitor()).exec(soyTree);
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

    assertEquals(1, template.numChildren());
    IfNode ifNode = (IfNode) template.getChild(0);
    assertEquals(3, ifNode.numChildren());
    IfCondNode icn0 = (IfCondNode) ifNode.getChild(0);
    assertEquals(4, icn0.numChildren());
    assertEquals("msg1", ((GoogMsgDefNode) icn0.getChild(0)).getChild(0).getDesc());
    assertEquals("msg2", ((GoogMsgDefNode) icn0.getChild(1)).getChild(0).getDesc());
    IfCondNode icn1 = (IfCondNode) ifNode.getChild(1);
    assertEquals(4, icn1.numChildren());
    assertEquals("msg3", ((GoogMsgDefNode) icn1.getChild(0)).getChild(0).getDesc());
    assertEquals("msg4", ((GoogMsgDefNode) icn1.getChild(1)).getChild(0).getDesc());
    IfElseNode ien2 = (IfElseNode) ifNode.getChild(2);
    assertEquals(4, ien2.numChildren());
    assertEquals("msg5", ((GoogMsgDefNode) ien2.getChild(0)).getChild(0).getDesc());
    assertEquals("msg6", ((GoogMsgDefNode) ien2.getChild(1)).getChild(0).getDesc());
  }


  public void testSwitch() {

    String soyCode =
        "{switch $goo}\n" +
        "  {case 0}\n" +
        "    {msg desc=\"msg1\"}blah{/msg}\n" +
        "    {msg desc=\"msg2\"}blah{/msg}\n" +
        "  {case 1, 2, 3}\n" +
        "    {msg desc=\"msg3\"}blah{/msg}\n" +
        "    {msg desc=\"msg4\"}blah{/msg}\n" +
        "  {default}\n" +
        "    {msg desc=\"msg5\"}blah{/msg}\n" +
        "    {msg desc=\"msg6\"}blah{/msg}\n" +
        "{/switch}\n";

    SoyFileSetNode soyTree = JsSrcTestUtils.parseSoyCode(soyCode);
    (new MoveGoogMsgDefNodesEarlierVisitor()).exec(soyTree);
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

    assertEquals(1, template.numChildren());
    SwitchNode switchNode = (SwitchNode) template.getChild(0);
    assertEquals(3, switchNode.numChildren());
    SwitchCaseNode scn0 = (SwitchCaseNode) switchNode.getChild(0);
    assertEquals(4, scn0.numChildren());
    assertEquals("msg1", ((GoogMsgDefNode) scn0.getChild(0)).getChild(0).getDesc());
    assertEquals("msg2", ((GoogMsgDefNode) scn0.getChild(1)).getChild(0).getDesc());
    SwitchCaseNode scn1 = (SwitchCaseNode) switchNode.getChild(1);
    assertEquals(4, scn1.numChildren());
    assertEquals("msg3", ((GoogMsgDefNode) scn1.getChild(0)).getChild(0).getDesc());
    assertEquals("msg4", ((GoogMsgDefNode) scn1.getChild(1)).getChild(0).getDesc());
    SwitchDefaultNode sdn2 = (SwitchDefaultNode) switchNode.getChild(2);
    assertEquals(4, sdn2.numChildren());
    assertEquals("msg5", ((GoogMsgDefNode) sdn2.getChild(0)).getChild(0).getDesc());
    assertEquals("msg6", ((GoogMsgDefNode) sdn2.getChild(1)).getChild(0).getDesc());
  }


  public void testForeach() {

    String soyCode =
        "{foreach $goo in $goose}\n" +
        "  {msg desc=\"msg1\"}blah{/msg}\n" +
        "  {msg desc=\"msg2\"}{$goo}{/msg}\n" +  // dep on $goo
        "  {msg desc=\"msg3\"}{$moo}{/msg}\n" +
        "  {msg desc=\"msg4\"}{$goo}{/msg}\n" +  // dep on $goo
        "{ifempty}\n" +
        "  {msg desc=\"msg5\"}{$moo}{/msg}\n" +
        "  {msg desc=\"msg6\"}blah{/msg}\n" +
        "{/foreach}\n";

    SoyFileSetNode soyTree = JsSrcTestUtils.parseSoyCode(soyCode);
    (new MoveGoogMsgDefNodesEarlierVisitor()).exec(soyTree);
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

    assertEquals(3, template.numChildren());
    assertEquals("msg1", ((GoogMsgDefNode) template.getChild(0)).getChild(0).getDesc());
    assertEquals("msg3", ((GoogMsgDefNode) template.getChild(1)).getChild(0).getDesc());

    ForeachNode foreachNode = (ForeachNode) template.getChild(2);
    ForeachNonemptyNode fnn0 = (ForeachNonemptyNode) foreachNode.getChild(0);
    assertEquals(6, fnn0.numChildren());
    assertEquals("msg2", ((GoogMsgDefNode) fnn0.getChild(0)).getChild(0).getDesc());
    assertEquals("msg4", ((GoogMsgDefNode) fnn0.getChild(1)).getChild(0).getDesc());
    ForeachIfemptyNode fin1 = (ForeachIfemptyNode) foreachNode.getChild(1);
    assertEquals(4, fin1.numChildren());
    assertEquals("msg5", ((GoogMsgDefNode) fin1.getChild(0)).getChild(0).getDesc());
    assertEquals("msg6", ((GoogMsgDefNode) fin1.getChild(1)).getChild(0).getDesc());
  }


  public void testFor() {

    String soyCode =
        "{for $i in range(3)}\n" +
        "  {msg desc=\"msg1\"}blah{/msg}\n" +
        "  {msg desc=\"msg2\"}{$i}{/msg}\n" +  // dep on $i
        "  {msg desc=\"msg3\"}{$moo}{/msg}\n" +
        "  {msg desc=\"msg4\"}{$i}{/msg}\n" +  // dep on $i
        "{/for}\n";

    SoyFileSetNode soyTree = JsSrcTestUtils.parseSoyCode(soyCode);
    (new MoveGoogMsgDefNodesEarlierVisitor()).exec(soyTree);
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

    assertEquals(3, template.numChildren());
    assertEquals("msg1", ((GoogMsgDefNode) template.getChild(0)).getChild(0).getDesc());
    assertEquals("msg3", ((GoogMsgDefNode) template.getChild(1)).getChild(0).getDesc());

    ForNode forNode = (ForNode) template.getChild(2);
    assertEquals(6, forNode.numChildren());
    assertEquals("msg2", ((GoogMsgDefNode) forNode.getChild(0)).getChild(0).getDesc());
    assertEquals("msg4", ((GoogMsgDefNode) forNode.getChild(1)).getChild(0).getDesc());
  }


  public void testNested() {

    String soyCode =
        "{if $goo}\n" +
        "  {foreach $moo in $moose}\n" +
        "    {for $i in range(3)}\n" +
        "      {msg desc=\"msg1\"}blah{/msg}\n" +
        "      {msg desc=\"msg2\"}{$goo}{/msg}\n" +  // dep on $goo (irrelevant)
        "      {msg desc=\"msg3\"}{$moo}{/msg}\n" +  // dep on $moo
        "      {msg desc=\"msg4\"}{$i}{/msg}\n" +  // dep on $i
        "      {msg desc=\"msg5\"}{$zoo}{/msg}\n" +
        "      {msg desc=\"msg6\"}{$goo}{$moo}{/msg}\n" +  // dep on $goo (irrelevant) and $moo
        "      {msg desc=\"msg7\"}{$goo}{$i}{/msg}\n" +  // dep on $goo (irrelevant) and $i
        "      {msg desc=\"msg8\"}{$moo}{$i}{/msg}\n" +  // dep on $moo and $i
        "    {/for}\n" +
        "  {/foreach}\n" +
        "{/if}\n" +
        "{msg desc=\"msg9\"}{$goo}{/msg}\n";  // dep on $goo (irrelevant)

    SoyFileSetNode soyTree = JsSrcTestUtils.parseSoyCode(soyCode);
    (new MoveGoogMsgDefNodesEarlierVisitor()).exec(soyTree);
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

    assertEquals(3, template.numChildren());
    assertEquals("msg9", ((GoogMsgDefNode) template.getChild(0)).getChild(0).getDesc());

    IfCondNode ifCondNode = (IfCondNode) ((IfNode) template.getChild(1)).getChild(0);
    assertEquals(4, ifCondNode.numChildren());
    assertEquals("msg1", ((GoogMsgDefNode) ifCondNode.getChild(0)).getChild(0).getDesc());
    assertEquals("msg2", ((GoogMsgDefNode) ifCondNode.getChild(1)).getChild(0).getDesc());
    assertEquals("msg5", ((GoogMsgDefNode) ifCondNode.getChild(2)).getChild(0).getDesc());

    ForeachNonemptyNode foreachNonemptyNode =
        (ForeachNonemptyNode) ((ForeachNode) ifCondNode.getChild(3)).getChild(0);
    assertEquals(3, foreachNonemptyNode.numChildren());
    assertEquals(
        "msg3", ((GoogMsgDefNode) foreachNonemptyNode.getChild(0)).getChild(0).getDesc());
    assertEquals(
        "msg6", ((GoogMsgDefNode) foreachNonemptyNode.getChild(1)).getChild(0).getDesc());

    ForNode forNode = (ForNode) foreachNonemptyNode.getChild(2);
    assertEquals(11, forNode.numChildren());
    assertEquals("msg4", ((GoogMsgDefNode) forNode.getChild(0)).getChild(0).getDesc());
    assertEquals("msg7", ((GoogMsgDefNode) forNode.getChild(1)).getChild(0).getDesc());
    assertEquals("msg8", ((GoogMsgDefNode) forNode.getChild(2)).getChild(0).getDesc());
  }

}
