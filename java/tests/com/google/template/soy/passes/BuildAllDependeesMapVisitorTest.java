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

package com.google.template.soy.passes;

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateNode;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for BuildAllDependeesMapVisitor.
 *
 */
@RunWith(JUnit4.class)
public final class BuildAllDependeesMapVisitorTest {

  @Test
  public void testGetTopLevelRefsVisitor() {

    String testFileContent =
        "{namespace boo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** Test template */\n"
            + "{template .foo}\n"
            + "  {@param a : ?}\n"
            + "  {@param b : ?}\n"
            + "  {@param e : ?}\n"
            + "  {@param fs : ?}\n"
            + "  {@param g : ?}\n"
            + "  {@param i : ?}\n"
            + "  {@param k : ?}\n"
            + "  {@param n : ?}\n"
            + "  {$a}{$b.c}\n"
            + "  {if $b.d}\n"
            + "    {$e}\n"
            + "    {foreach $f in $fs}\n"
            + "      {$f}{$g.h|noAutoescape}\n"
            + "      {msg desc=\"\"}\n"
            + "        {$i}\n"
            + "        {call some.func}\n"
            + "          {param j: $k.l /}\n"
            + "          {param m}{$n}{$f.o}{/param}\n"
            + "        {/call}\n"
            + "      {/msg}\n"
            + "    {/foreach}\n"
            + "  {/if}\n"
            + "{/template}\n";

    ErrorReporter boom = ErrorReporter.exploding();

    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(testFileContent)
            .errorReporter(boom)
            .parse()
            .fileSet();

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
    MsgFallbackGroupNode msgFbGrpNode = (MsgFallbackGroupNode) foreachNonemptyNode.getChild(2);
    MsgNode msgNode = msgFbGrpNode.getChild(0);
    MsgPlaceholderNode iPh = (MsgPlaceholderNode) msgNode.getChild(0);
    PrintNode i = (PrintNode) iPh.getChild(0);
    MsgPlaceholderNode callPh = (MsgPlaceholderNode) msgNode.getChild(1);
    CallNode callNode = (CallNode) callPh.getChild(0);
    CallParamValueNode cpvn = (CallParamValueNode) callNode.getChild(0);
    CallParamContentNode cpcn = (CallParamContentNode) callNode.getChild(1);
    PrintNode n = (PrintNode) cpcn.getChild(0);
    PrintNode fo = (PrintNode) cpcn.getChild(1);

    // Build the nearest-dependee map.
    Map<SoyNode, List<SoyNode>> allDependeesMap = new BuildAllDependeesMapVisitor().exec(soyTree);

    assertThat(allDependeesMap.get(a)).containsExactly(template);
    assertThat(allDependeesMap.get(bc)).containsExactly(template);
    assertThat(allDependeesMap.get(ifNode)).containsExactly(template);
    assertThat(allDependeesMap.get(ifCondNode)).containsExactly(ifNode, template).inOrder();
    assertThat(allDependeesMap.get(e)).containsExactly(ifCondNode, template).inOrder();
    assertThat(allDependeesMap.get(foreachNode)).containsExactly(ifCondNode, template).inOrder();
    assertThat(allDependeesMap.get(foreachNonemptyNode))
        .containsExactly(foreachNode, ifCondNode, template)
        .inOrder();
    assertThat(allDependeesMap.get(f))
        .containsExactly(foreachNonemptyNode, ifCondNode, template)
        .inOrder();
    // Note special case: foreachNonemptyNode does not count as conditional block.
    assertThat(allDependeesMap.get(gh)).containsExactly(ifCondNode, template).inOrder();
    assertThat(allDependeesMap.get(ghPdn)).containsExactly(gh, ifCondNode, template).inOrder();
    assertThat(allDependeesMap.get(msgFbGrpNode))
        .containsExactly(foreachNonemptyNode, ifCondNode, template)
        .inOrder();
    assertThat(allDependeesMap.get(msgNode))
        .containsExactly(msgFbGrpNode, foreachNonemptyNode, ifCondNode, template)
        .inOrder();
    // Note special case: foreachNonemptyNode does not count as conditional block.
    assertThat(allDependeesMap.get(iPh)).containsExactly(msgNode, ifCondNode, template).inOrder();
    // Note special case: foreachNonemptyNode does not count as conditional block.
    assertThat(allDependeesMap.get(i)).containsExactly(ifCondNode, template).inOrder();
    assertThat(allDependeesMap.get(callPh))
        .containsExactly(msgNode, foreachNonemptyNode, ifCondNode, template)
        .inOrder();
    assertThat(allDependeesMap.get(callNode))
        .containsExactly(foreachNonemptyNode, ifCondNode, template)
        .inOrder();
    // Note special case: foreachNonemptyNode does not count as conditional block.
    assertThat(allDependeesMap.get(cpvn)).containsExactly(callNode, ifCondNode, template).inOrder();
    assertThat(allDependeesMap.get(cpcn))
        .containsExactly(callNode, foreachNonemptyNode, ifCondNode, template)
        .inOrder();
    // Note special case: foreachNonemptyNode does not count as conditional block.
    assertThat(allDependeesMap.get(n)).containsExactly(ifCondNode, template).inOrder();
    assertThat(allDependeesMap.get(fo))
        .containsExactly(foreachNonemptyNode, ifCondNode, template)
        .inOrder();
  }
}
