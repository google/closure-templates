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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.template.soy.shared.internal.SharedTestUtils;
import com.google.template.soy.sharedpasses.FindIndirectParamsVisitor.IndirectParamsInfo;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateNode.SoyDocParam;

import junit.framework.TestCase;

import java.util.Map;


/**
 * Unit tests for FindIndirectParamsVisitor.
 *
 * @author Kai Huang
 */
public class FindIndirectParamsVisitorTest extends TestCase {


  public void testFindIndirectParams() {

    String fileContent1 =
        "{namespace alpha}\n" +
        "\n" +
        "/** @param a0 @param b3 */\n" +  // 'b3' listed by alpha.zero
        "{template .zero}\n" +
        "  {call .zero data=\"all\" /}\n" +  // recursive call should not cause 'a0' to be added
        "  {call .one data=\"all\" /}\n" +
        "  {call .two /}\n" +
        "  {call beta.zero /}\n" +
        "  {call .five data=\"all\"}\n" +
        "    {param a5: $a0 /}\n" +
        "    {param b2: 88 /}\n" +
        "  {/call}\n" +
        "{/template}\n" +
        "\n" +
        "/** @param a1 */\n" +
        "{template .one}\n" +
        "  {call .three data=\"all\" /}\n" +
        "  {call .four /}\n" +
        "{/template}\n" +
        "\n" +
        "/** @param a2 */\n" +
        "{template .two}\n" +
        "{/template}\n" +
        "\n" +
        "/** @param a3 */\n" +
        "{template .three}\n" +
        "  {call beta.one data=\"all\" /}\n" +
        "{/template}\n" +
        "\n" +
        "/** @param a4 */\n" +
        "{template .four}\n" +
        "  {call external.one /}\n" +
        "{/template}\n" +
        "\n" +
        "/** @param a5 @param b4 */\n" +  // 'b4' listed by alpha.five
        "{template .five}\n" +
        "  {call beta.two data=\"all\" /}\n" +
        "  {call beta.three data=\"all\" /}\n" +
        "  {call beta.four data=\"all\" /}\n" +
        "{/template}\n" +
        "\n" +
        "/** @param a6 */\n" +
        "{template .six}\n" +
        "{/template}\n";

    String fileContent2 =
        "{namespace beta}\n" +
        "\n" +
        "/** @param b0 */\n" +
        "{template .zero}\n" +
        "{/template}\n" +
        "\n" +
        "/** @param b1 */\n" +
        "{template .one}\n" +
        "  {call alpha.six data=\"all\" /}\n" +
        "{/template}\n" +
        "\n" +
        "/** @param b2 */\n" +
        "{template .two}\n" +
        "{/template}\n" +
        "\n" +
        "/** @param b3 */\n" +
        "{template .three}\n" +
        "{/template}\n" +
        "\n" +
        "/** @param b4 */\n" +
        "{template .four}\n" +
        "{/template}\n";

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(fileContent1, fileContent2);

    SoyFileNode a = soyTree.getChild(0);
    TemplateNode a0 = a.getChild(0);
    TemplateNode a1 = a.getChild(1);
    //TemplateNode a2 = a.getChild(2);
    TemplateNode a3 = a.getChild(3);
    //TemplateNode a4 = a.getChild(4);
    TemplateNode a5 = a.getChild(5);
    TemplateNode a6 = a.getChild(6);
    SoyFileNode b = soyTree.getChild(1);
    //TemplateNode b0 = b.getChild(0);
    TemplateNode b1 = b.getChild(1);
    //TemplateNode b2 = b.getChild(2);
    TemplateNode b3 = b.getChild(3);
    TemplateNode b4 = b.getChild(4);

    IndirectParamsInfo ipi = (new FindIndirectParamsVisitor(null)).exec(a0);
    assertEquals(false, ipi.mayHaveIndirectParamsInExternalCalls);
    assertEquals(false, ipi.mayHaveIndirectParamsInExternalDelCalls);

    Map<String, SoyDocParam> ipMap = ipi.indirectParams;
    assertEquals(6, ipMap.size());
    assertFalse(ipMap.containsKey("a0"));
    assertTrue(ipMap.containsKey("a1"));
    assertFalse(ipMap.containsKey("a2"));
    assertTrue(ipMap.containsKey("a3"));
    assertFalse(ipMap.containsKey("a4"));
    assertFalse(ipMap.containsKey("a5"));
    assertTrue(ipMap.containsKey("a6"));
    assertFalse(ipMap.containsKey("b0"));
    assertTrue(ipMap.containsKey("b1"));
    assertFalse(ipMap.containsKey("b2"));
    assertTrue(ipMap.containsKey("b3"));
    assertTrue(ipMap.containsKey("b4"));

    Multimap<String, TemplateNode> pktcm = ipi.paramKeyToCalleesMultimap;
    assertEquals(ImmutableSet.of(a1), pktcm.get("a1"));
    assertEquals(ImmutableSet.of(a3), pktcm.get("a3"));
    assertEquals(ImmutableSet.of(a6), pktcm.get("a6"));
    assertEquals(ImmutableSet.of(b1), pktcm.get("b1"));
    assertEquals(ImmutableSet.of(b3), pktcm.get("b3"));
    assertEquals(ImmutableSet.of(a5, b4), pktcm.get("b4"));  // 'b4' listed by alpha.five
  }

}
