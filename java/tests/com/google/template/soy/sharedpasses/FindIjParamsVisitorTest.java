/*
 * Copyright 2012 Google Inc.
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

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.shared.internal.SharedTestUtils;
import com.google.template.soy.sharedpasses.FindIjParamsVisitor.IjParamsInfo;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;

import junit.framework.TestCase;


/**
 * Unit tests for FindIjParamsVisitor.
 *
 * @author Kai Huang
 */
public class FindIjParamsVisitorTest extends TestCase {


  public void testSimple() {

    // aaa -> {bbb, ccc}, bbb -> ddd.
    String fileContent = "" +
        "{namespace ns}\n" +
        "\n" +
        "/***/\n" +
        "{template .aaa}\n" +
        "  {call .bbb /} {$ij.boo} {call .ccc /} {$ij.foo}\n" +
        "{/template}\n" +
        "\n" +
        "/***/\n" +
        "{template .bbb}\n" +
        "  {$ij.boo} {$ij.goo} {call .ddd /}\n" +
        "{/template}\n" +
        "\n" +
        "/***/\n" +
        "{template .ccc}\n" +
        "  {$ij.boo} {$ij.moo + $ij.woo}\n" +
        "{/template}\n" +
        "\n" +
        "/***/\n" +
        "{template .ddd}\n" +
        "  {$ij.boo} {$ij.moo} {round($ij.zoo)}\n" +
        "{/template}\n";

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(fileContent);
    TemplateRegistry templateRegistry = new TemplateRegistry(soyTree);

    TemplateNode aaa = soyTree.getChild(0).getChild(0);
    TemplateNode bbb = soyTree.getChild(0).getChild(1);
    TemplateNode ccc = soyTree.getChild(0).getChild(2);
    TemplateNode ddd = soyTree.getChild(0).getChild(3);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 5 with incorporateCalleeVisitInfo case 1 (aaa -> bbb).
    FindIjParamsVisitor fuipv = new FindIjParamsVisitor(templateRegistry);
    fuipv.exec(aaa);
    assertEquals(4, fuipv.templateToFinishedInfoMap.size());
    assertEquals(3, fuipv.templateToFinishedInfoMap.get(ddd).ijParamToCalleesMultimap.size());
    assertEquals(3, fuipv.templateToFinishedInfoMap.get(ccc).ijParamToCalleesMultimap.size());
    assertEquals(5, fuipv.templateToFinishedInfoMap.get(bbb).ijParamToCalleesMultimap.size());
    assertEquals(
        4, fuipv.templateToFinishedInfoMap.get(bbb).ijParamToCalleesMultimap.keySet().size());
    assertEquals(10, fuipv.templateToFinishedInfoMap.get(aaa).ijParamToCalleesMultimap.size());
    assertEquals(
        6, fuipv.templateToFinishedInfoMap.get(aaa).ijParamToCalleesMultimap.keySet().size());

    // Test with exec(bbb) then exec(aaa).
    // Exercises: processCalleeHelper case 1 (aaa -> bbb).
    fuipv = new FindIjParamsVisitor(templateRegistry);
    fuipv.exec(bbb);
    assertEquals(2, fuipv.templateToFinishedInfoMap.size());
    assertEquals(3, fuipv.templateToFinishedInfoMap.get(ddd).ijParamToCalleesMultimap.size());
    assertEquals(5, fuipv.templateToFinishedInfoMap.get(bbb).ijParamToCalleesMultimap.size());
    assertEquals(
        4, fuipv.templateToFinishedInfoMap.get(bbb).ijParamToCalleesMultimap.keySet().size());
    fuipv.exec(aaa);
    assertEquals(4, fuipv.templateToFinishedInfoMap.size());
    assertEquals(3, fuipv.templateToFinishedInfoMap.get(ddd).ijParamToCalleesMultimap.size());
    assertEquals(3, fuipv.templateToFinishedInfoMap.get(ccc).ijParamToCalleesMultimap.size());
    assertEquals(5, fuipv.templateToFinishedInfoMap.get(bbb).ijParamToCalleesMultimap.size());
    assertEquals(
        4, fuipv.templateToFinishedInfoMap.get(bbb).ijParamToCalleesMultimap.keySet().size());
    assertEquals(10, fuipv.templateToFinishedInfoMap.get(aaa).ijParamToCalleesMultimap.size());
    assertEquals(
        6, fuipv.templateToFinishedInfoMap.get(aaa).ijParamToCalleesMultimap.keySet().size());
  }


  public void testTwoPathsToSameTemplate() {

    // aaa -> {bbb, ccc}, ccc -> bbb.
    String fileContent = "" +
        "{namespace ns}\n" +
        "\n" +
        "/***/\n" +
        "{template .aaa}\n" +
        "  {call .bbb /} {$ij.boo} {call .ccc /} {$ij.foo}\n" +
        "{/template}\n" +
        "\n" +
        "/***/\n" +
        "{template .bbb}\n" +
        "  {$ij.boo} {$ij.goo}\n" +
        "{/template}\n" +
        "\n" +
        "/***/\n" +
        "{template .ccc}\n" +
        "  {$ij.boo} {$ij.moo + $ij.woo} {call .bbb /}\n" +
        "{/template}\n";

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(fileContent);
    TemplateRegistry templateRegistry = new TemplateRegistry(soyTree);

    TemplateNode aaa = soyTree.getChild(0).getChild(0);
    TemplateNode bbb = soyTree.getChild(0).getChild(1);
    TemplateNode ccc = soyTree.getChild(0).getChild(2);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 4 with incorporateCalleeVisitInfo case 1 (ccc -> bbb).
    FindIjParamsVisitor fuipv = new FindIjParamsVisitor(templateRegistry);
    fuipv.exec(aaa);
    assertEquals(3, fuipv.templateToFinishedInfoMap.size());
    assertEquals(2, fuipv.templateToFinishedInfoMap.get(bbb).ijParamToCalleesMultimap.size());
    assertEquals(5, fuipv.templateToFinishedInfoMap.get(ccc).ijParamToCalleesMultimap.size());
    assertEquals(
        4, fuipv.templateToFinishedInfoMap.get(ccc).ijParamToCalleesMultimap.keySet().size());
    assertEquals(7, fuipv.templateToFinishedInfoMap.get(aaa).ijParamToCalleesMultimap.size());
    assertEquals(
        5, fuipv.templateToFinishedInfoMap.get(aaa).ijParamToCalleesMultimap.keySet().size());
  }


  public void testSimpleRecursion() {

    // Tests direct recurion (cycle of 1) and indirect recursion with a cycle of 2.

    // aaa -> bbb, bbb -> {bbb, ccc}, ccc -> bbb.
    String fileContent = "" +
        "{namespace ns}\n" +
        "\n" +
        "/***/\n" +
        "{template .aaa}\n" +
        "  {call .bbb /} {$ij.boo} {$ij.foo}\n" +
        "{/template}\n" +
        "\n" +
        "/***/\n" +
        "{template .bbb}\n" +
        "  {$ij.boo} {$ij.goo} {call .bbb /} {call .ccc /}\n" +
        "{/template}\n" +
        "\n" +
        "/***/\n" +
        "{template .ccc}\n" +
        "  {$ij.boo} {call .bbb /} {$ij.moo}\n" +
        "{/template}\n";

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(fileContent);
    TemplateRegistry templateRegistry = new TemplateRegistry(soyTree);

    TemplateNode aaa = soyTree.getChild(0).getChild(0);
    TemplateNode bbb = soyTree.getChild(0).getChild(1);
    TemplateNode ccc = soyTree.getChild(0).getChild(2);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 2 (bbb -> bbb).
    // Exercises: processCalleeHelper case 3 (ccc -> bbb).
    // Exercises: processCalleeHelper case 5 with incorporateCalleeVisitInfo case 2 (bbb -> ccc).
    FindIjParamsVisitor fuipv = new FindIjParamsVisitor(templateRegistry);
    fuipv.exec(aaa);
    assertEquals(3, fuipv.templateToFinishedInfoMap.size());
    assertEquals(4, fuipv.templateToFinishedInfoMap.get(ccc).ijParamToCalleesMultimap.size());
    assertEquals(
        3, fuipv.templateToFinishedInfoMap.get(ccc).ijParamToCalleesMultimap.keySet().size());
    assertEquals(4, fuipv.templateToFinishedInfoMap.get(bbb).ijParamToCalleesMultimap.size());
    assertEquals(
        3, fuipv.templateToFinishedInfoMap.get(bbb).ijParamToCalleesMultimap.keySet().size());
    assertEquals(6, fuipv.templateToFinishedInfoMap.get(aaa).ijParamToCalleesMultimap.size());
    assertEquals(
        4, fuipv.templateToFinishedInfoMap.get(aaa).ijParamToCalleesMultimap.keySet().size());
  }


  public void testLargerRecursiveCycle() {

    // Tests indirect recursion with a cycle of 3.

    // aaa -> bbb, bbb -> ccc, ccc -> aaa.
    String fileContent = "" +
        "{namespace ns}\n" +
        "\n" +
        "/***/\n" +
        "{template .aaa}\n" +
        "  {$ij.foo} {$ij.boo} {call .bbb /}\n" +
        "{/template}\n" +
        "\n" +
        "/***/\n" +
        "{template .bbb}\n" +
        "  {$ij.goo} {call .ccc /} {$ij.boo}\n" +
        "{/template}\n" +
        "\n" +
        "/***/\n" +
        "{template .ccc}\n" +
        "  {call .aaa /} {$ij.moo} {$ij.boo}\n" +
        "{/template}\n";

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(fileContent);
    TemplateRegistry templateRegistry = new TemplateRegistry(soyTree);

    TemplateNode aaa = soyTree.getChild(0).getChild(0);
    TemplateNode bbb = soyTree.getChild(0).getChild(1);
    TemplateNode ccc = soyTree.getChild(0).getChild(2);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 3 (ccc-> aaa).
    // Exercises: processCalleeHelper case 5 with incorporateCalleeVisitInfo case 3 (bbb -> ccc).
    // Exercises: processCalleeHelper case 5 with incorporateCalleeVisitInfo case 2 (aaa -> bbb).
    FindIjParamsVisitor fuipv = new FindIjParamsVisitor(templateRegistry);
    fuipv.exec(aaa);
    assertEquals(3, fuipv.templateToFinishedInfoMap.size());
    assertEquals(6, fuipv.templateToFinishedInfoMap.get(ccc).ijParamToCalleesMultimap.size());
    assertEquals(
        4, fuipv.templateToFinishedInfoMap.get(ccc).ijParamToCalleesMultimap.keySet().size());
    assertEquals(6, fuipv.templateToFinishedInfoMap.get(bbb).ijParamToCalleesMultimap.size());
    assertEquals(
        4, fuipv.templateToFinishedInfoMap.get(bbb).ijParamToCalleesMultimap.keySet().size());
    assertEquals(6, fuipv.templateToFinishedInfoMap.get(aaa).ijParamToCalleesMultimap.size());
    assertEquals(
        4, fuipv.templateToFinishedInfoMap.get(aaa).ijParamToCalleesMultimap.keySet().size());
  }


  public void testTwoPathsToSameRecursiveCycle() {

    // aaa -> {bbb, ccc}, bbb -> ddd, ccc -> ddd, ddd -> bbb.
    String fileContent = "" +
        "{namespace ns}\n" +
        "\n" +
        "/***/\n" +
        "{template .aaa}\n" +
        "  {$ij.boo} {$ij.foo} {call .bbb /} {call .ccc /}\n" +
        "{/template}\n" +
        "\n" +
        "/***/\n" +
        "{template .bbb}\n" +
        "  {$ij.boo} {$ij.goo} {call .ddd /}\n" +
        "{/template}\n" +
        "\n" +
        "/***/\n" +
        "{template .ccc}\n" +
        "  {$ij.boo} {$ij.moo} {call .ddd /}\n" +
        "{/template}\n" +
        "\n" +
        "/***/\n" +
        "{template .ddd}\n" +
        "  {$ij.boo} {$ij.too} {call .bbb /}\n" +
        "{/template}\n";

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(fileContent);
    TemplateRegistry templateRegistry = new TemplateRegistry(soyTree);

    TemplateNode aaa = soyTree.getChild(0).getChild(0);
    TemplateNode bbb = soyTree.getChild(0).getChild(1);
    TemplateNode ccc = soyTree.getChild(0).getChild(2);
    TemplateNode ddd = soyTree.getChild(0).getChild(3);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 4 with incorporateCalleeVisitInfo case 4 (ccc -> ddd).
    FindIjParamsVisitor fuipv = new FindIjParamsVisitor(templateRegistry);
    fuipv.exec(aaa);
    assertEquals(4, fuipv.templateToFinishedInfoMap.size());
    assertEquals(4, fuipv.templateToFinishedInfoMap.get(bbb).ijParamToCalleesMultimap.size());
    assertEquals(
        3, fuipv.templateToFinishedInfoMap.get(bbb).ijParamToCalleesMultimap.keySet().size());
    assertEquals(4, fuipv.templateToFinishedInfoMap.get(ddd).ijParamToCalleesMultimap.size());
    assertEquals(
        3, fuipv.templateToFinishedInfoMap.get(ddd).ijParamToCalleesMultimap.keySet().size());
    assertEquals(6, fuipv.templateToFinishedInfoMap.get(ccc).ijParamToCalleesMultimap.size());
    assertEquals(
        4, fuipv.templateToFinishedInfoMap.get(ccc).ijParamToCalleesMultimap.keySet().size());
    assertEquals(8, fuipv.templateToFinishedInfoMap.get(aaa).ijParamToCalleesMultimap.size());
    assertEquals(
        5, fuipv.templateToFinishedInfoMap.get(aaa).ijParamToCalleesMultimap.keySet().size());
  }


  public void testSmallerRecursiveCycleInLargerRecursiveCycle() {

    // aaa -> {bbb, ccc}, bbb -> aaa, ccc -> bbb.
    String fileContent = "" +
        "{namespace ns}\n" +
        "\n" +
        "/***/\n" +
        "{template .aaa}\n" +
        "  {$ij.foo} {$ij.boo} {call .bbb /} {call .ccc /}\n" +
        "{/template}\n" +
        "\n" +
        "/***/\n" +
        "{template .bbb}\n" +
        "  {$ij.goo} {$ij.boo} {call .aaa /}\n" +
        "{/template}\n" +
        "\n" +
        "/***/\n" +
        "{template .ccc}\n" +
        "  {$ij.moo} {$ij.boo} {call .bbb /}\n" +
        "{/template}\n";

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(fileContent);
    TemplateRegistry templateRegistry = new TemplateRegistry(soyTree);

    TemplateNode aaa = soyTree.getChild(0).getChild(0);
    TemplateNode bbb = soyTree.getChild(0).getChild(1);
    TemplateNode ccc = soyTree.getChild(0).getChild(2);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 4 with incorporateCalleeVisitInfo case 3 (ccc -> bbb).
    FindIjParamsVisitor fuipv = new FindIjParamsVisitor(templateRegistry);
    fuipv.exec(aaa);
    assertEquals(3, fuipv.templateToFinishedInfoMap.size());
    assertEquals(6, fuipv.templateToFinishedInfoMap.get(ccc).ijParamToCalleesMultimap.size());
    assertEquals(
        4, fuipv.templateToFinishedInfoMap.get(ccc).ijParamToCalleesMultimap.keySet().size());
    assertEquals(6, fuipv.templateToFinishedInfoMap.get(bbb).ijParamToCalleesMultimap.size());
    assertEquals(
        4, fuipv.templateToFinishedInfoMap.get(bbb).ijParamToCalleesMultimap.keySet().size());
    assertEquals(6, fuipv.templateToFinishedInfoMap.get(aaa).ijParamToCalleesMultimap.size());
    assertEquals(
        4, fuipv.templateToFinishedInfoMap.get(aaa).ijParamToCalleesMultimap.keySet().size());
  }


  public void testExecForAllTemplates() {

    // aaa -> {bbb, ccc}, bbb -> ddd.
    String fileContent = "" +
        "{namespace ns}\n" +
        "\n" +
        "/***/\n" +
        "{template .bbb}\n" +
        "  {$ij.boo} {$ij.goo} {call .ddd /}\n" +
        "{/template}\n" +
        "\n" +
        "/***/\n" +
        "{template .aaa}\n" +
        "  {call .bbb /} {$ij.boo} {call .ccc /} {$ij.foo}\n" +
        "{/template}\n" +
        "\n" +
        "/***/\n" +
        "{template .ccc}\n" +
        "  {$ij.boo} {$ij.moo + $ij.woo}\n" +
        "{/template}\n" +
        "\n" +
        "/***/\n" +
        "{template .ddd}\n" +
        "  {$ij.boo} {$ij.moo} {round($ij.zoo)}\n" +
        "{/template}\n";

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(fileContent);
    TemplateRegistry templateRegistry = new TemplateRegistry(soyTree);

    TemplateNode bbb = soyTree.getChild(0).getChild(0);
    TemplateNode aaa = soyTree.getChild(0).getChild(1);
    TemplateNode ccc = soyTree.getChild(0).getChild(2);
    TemplateNode ddd = soyTree.getChild(0).getChild(3);

    ImmutableMap<TemplateNode, IjParamsInfo> templateToIjParamsInfoMap =
        (new FindIjParamsVisitor(null)).execForAllTemplates(soyTree);
    assertEquals(4, templateToIjParamsInfoMap.size());
    assertEquals(3, templateToIjParamsInfoMap.get(ddd).ijParamToCalleesMultimap.size());
    assertEquals(3, templateToIjParamsInfoMap.get(ccc).ijParamToCalleesMultimap.size());
    assertEquals(5, templateToIjParamsInfoMap.get(bbb).ijParamToCalleesMultimap.size());
    assertEquals(4, templateToIjParamsInfoMap.get(bbb).ijParamToCalleesMultimap.keySet().size());
    assertEquals(10, templateToIjParamsInfoMap.get(aaa).ijParamToCalleesMultimap.size());
    assertEquals(6, templateToIjParamsInfoMap.get(aaa).ijParamToCalleesMultimap.keySet().size());
  }

}
