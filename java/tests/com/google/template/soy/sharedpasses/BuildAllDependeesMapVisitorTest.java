/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.sharedpasses;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.shared.internal.SharedTestUtils;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateNode;

import junit.framework.TestCase;

import java.util.List;
import java.util.Map;


/**
 * Unit tests for BuildAllDependeesMapVisitor.
 *
 * @author Kai Huang
 */
public class BuildAllDependeesMapVisitorTest extends TestCase {


  public void testGetTopLevelRefsVisitor() {

    String testFileContent =
        "{namespace boo}\n" +
        "\n" +
        "/** Test template */\n" +
        "{template .foo}\n" +
        "  {$a}{$b.c}\n" +
        "  {if $b.d}\n" +
        "    {$e}\n" +
        "    {foreach $f in $fs}\n" +
        "      {$f}{$g.h|noAutoescape}\n" +
        "      {msg desc=\"\"}\n" +
        "        {$i}\n" +
        "        {call some.func}\n" +
        "          {param j: $k.l /}\n" +
        "          {param m}{$n}{$f.o}{/param}\n" +
        "        {/call}\n" +
        "      {/msg}\n" +
        "    {/foreach}\n" +
        "  {/if}\n" +
        "{/template}\n";

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(testFileContent);

    TemplateNode template = soyTree.getChild(0).getChild(0);
    PrintNode a = (PrintNode) template.getChild(0);
    PrintNode bc = (PrintNode) template.getChild(1);
    IfNode ifNode = (IfNode) template.getChild(2);
    IfCondNode ifCondNode = (IfCondNode) ifNode.getChild(0);
    PrintNode e = (PrintNode) ifCondNode.getChild(0);
    ForeachNode foreachNode = (ForeachNode) ifCondNode.getChild(1);
    ForeachNonemptyNode foreachNonemptyNode = (ForeachNonemptyNode) foreachNode.getChild(0);
    PrintNode f = (PrintNode) foreachNonemptyNode.getChild(0);
    PrintNode gh = (PrintNode) foreachNonemptyNode.getChild(1);
    PrintDirectiveNode ghPdn = gh.getChild(0);
    MsgNode msgNode = (MsgNode) foreachNonemptyNode.getChild(2);
    MsgPlaceholderNode iPh = (MsgPlaceholderNode) msgNode.getChild(0);
    PrintNode i = (PrintNode) iPh.getChild(0);
    MsgPlaceholderNode callPh = (MsgPlaceholderNode) msgNode.getChild(1);
    CallNode callNode = (CallNode) callPh.getChild(0);
    CallParamValueNode cpvn = (CallParamValueNode) callNode.getChild(0);
    CallParamContentNode cpcn = (CallParamContentNode) callNode.getChild(1);
    PrintNode n = (PrintNode) cpcn.getChild(0);
    PrintNode fo = (PrintNode) cpcn.getChild(1);

    // Build the nearest-dependee map.
    Map<SoyNode, List<SoyNode>> allDependeesMap = (new BuildAllDependeesMapVisitor()).exec(soyTree);

    assertEquals(ImmutableList.of(template), allDependeesMap.get(a));
    assertEquals(ImmutableList.of(template), allDependeesMap.get(bc));
    assertEquals(ImmutableList.of(template), allDependeesMap.get(ifNode));
    assertEquals(ImmutableList.of(ifNode, template), allDependeesMap.get(ifCondNode));
    assertEquals(ImmutableList.of(ifCondNode, template), allDependeesMap.get(e));
    assertEquals(ImmutableList.of(ifCondNode, template), allDependeesMap.get(foreachNode));
    assertEquals(
        ImmutableList.of(foreachNode, ifCondNode, template),
        allDependeesMap.get(foreachNonemptyNode));
    assertEquals(
        ImmutableList.of(foreachNonemptyNode, ifCondNode, template),
        allDependeesMap.get(f));
    // Note special case: foreachNonemptyNode does not count as conditional block.
    assertEquals(ImmutableList.of(ifCondNode, template), allDependeesMap.get(gh));
    assertEquals(ImmutableList.of(gh, ifCondNode, template), allDependeesMap.get(ghPdn));
    assertEquals(
        ImmutableList.of(foreachNonemptyNode, ifCondNode, template),
        allDependeesMap.get(msgNode));
    // Note special case: foreachNonemptyNode does not count as conditional block.
    assertEquals(ImmutableList.of(msgNode, ifCondNode, template), allDependeesMap.get(iPh));
    // Note special case: foreachNonemptyNode does not count as conditional block.
    assertEquals(ImmutableList.of(ifCondNode, template), allDependeesMap.get(i));
    assertEquals(
        ImmutableList.of(msgNode, foreachNonemptyNode, ifCondNode, template),
        allDependeesMap.get(callPh));
    assertEquals(
        ImmutableList.of(foreachNonemptyNode, ifCondNode, template),
        allDependeesMap.get(callNode));
    // Note special case: foreachNonemptyNode does not count as conditional block.
    assertEquals(ImmutableList.of(callNode, ifCondNode, template), allDependeesMap.get(cpvn));
    assertEquals(
        ImmutableList.of(callNode, foreachNonemptyNode, ifCondNode, template),
        allDependeesMap.get(cpcn));
    // Note special case: foreachNonemptyNode does not count as conditional block.
    assertEquals(ImmutableList.of(ifCondNode, template), allDependeesMap.get(n));
    assertEquals(
        ImmutableList.of(foreachNonemptyNode, ifCondNode, template),
        allDependeesMap.get(fo));
  }

}
