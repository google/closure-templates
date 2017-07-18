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

package com.google.template.soy.passes;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.passes.FindIjParamsVisitor.IjParamsInfo;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for FindIjParamsVisitor.
 *
 */
@RunWith(JUnit4.class)
public final class FindIjParamsVisitorTest {

  private static final ErrorReporter FAIL = ErrorReporter.exploding();

  @Test
  public void testSimple() {

    // aaa -> {bbb, ccc}, bbb -> ddd.
    String fileContent =
        ""
            + "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .aaa}\n"
            + "  {call .bbb /} {$ij.boo} {call .ccc /} {$ij.foo}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .bbb}\n"
            + "  {$ij.boo} {$ij.goo} {call .ddd /}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .ccc}\n"
            + "  {$ij.boo} {$ij.moo + $ij.woo}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .ddd}\n"
            + "  {$ij.boo} {$ij.moo} {round($ij.zoo)}\n"
            + "{/template}\n";

    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(fileContent).parse().fileSet();
    TemplateRegistry templateRegistry = new TemplateRegistry(soyTree, FAIL);

    TemplateNode aaa = soyTree.getChild(0).getChild(0);
    TemplateNode bbb = soyTree.getChild(0).getChild(1);
    TemplateNode ccc = soyTree.getChild(0).getChild(2);
    TemplateNode ddd = soyTree.getChild(0).getChild(3);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 5 with incorporateCalleeVisitInfo case 1 (aaa -> bbb).
    FindIjParamsVisitor visitor = new FindIjParamsVisitor(templateRegistry);
    visitor.exec(aaa);
    assertThat(visitor.exec(ddd).ijParamToCalleesMultimap).hasSize(3);
    assertThat(visitor.exec(ccc).ijParamToCalleesMultimap).hasSize(3);
    assertThat(visitor.exec(bbb).ijParamToCalleesMultimap).hasSize(5);
    assertThat(visitor.exec(bbb).ijParamToCalleesMultimap.keySet()).hasSize(4);
    assertThat(visitor.exec(aaa).ijParamToCalleesMultimap).hasSize(10);
    assertThat(visitor.exec(aaa).ijParamToCalleesMultimap.keySet()).hasSize(6);

    // Test with exec(bbb) then exec(aaa).
    // Exercises: processCalleeHelper case 1 (aaa -> bbb).
    visitor = new FindIjParamsVisitor(templateRegistry);
    visitor.exec(bbb);
    assertThat(visitor.exec(ddd).ijParamToCalleesMultimap).hasSize(3);
    assertThat(visitor.exec(bbb).ijParamToCalleesMultimap).hasSize(5);
    assertThat(visitor.exec(bbb).ijParamToCalleesMultimap.keySet()).hasSize(4);
    visitor.exec(aaa);
    assertThat(visitor.exec(ddd).ijParamToCalleesMultimap).hasSize(3);
    assertThat(visitor.exec(ccc).ijParamToCalleesMultimap).hasSize(3);
    assertThat(visitor.exec(bbb).ijParamToCalleesMultimap).hasSize(5);
    assertThat(visitor.exec(bbb).ijParamToCalleesMultimap.keySet()).hasSize(4);
    assertThat(visitor.exec(aaa).ijParamToCalleesMultimap).hasSize(10);
    assertThat(visitor.exec(aaa).ijParamToCalleesMultimap.keySet()).hasSize(6);
  }

  @Test
  public void testTwoPathsToSameTemplate() {

    // aaa -> {bbb, ccc}, ccc -> bbb.
    String fileContent =
        ""
            + "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .aaa}\n"
            + "  {call .bbb /} {$ij.boo} {call .ccc /} {$ij.foo}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .bbb}\n"
            + "  {$ij.boo} {$ij.goo}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .ccc}\n"
            + "  {$ij.boo} {$ij.moo + $ij.woo} {call .bbb /}\n"
            + "{/template}\n";

    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(fileContent).parse().fileSet();
    TemplateRegistry templateRegistry = new TemplateRegistry(soyTree, FAIL);

    TemplateNode aaa = soyTree.getChild(0).getChild(0);
    TemplateNode bbb = soyTree.getChild(0).getChild(1);
    TemplateNode ccc = soyTree.getChild(0).getChild(2);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 4 with incorporateCalleeVisitInfo case 1 (ccc -> bbb).
    FindIjParamsVisitor visitor = new FindIjParamsVisitor(templateRegistry);
    visitor.exec(aaa);
    assertThat(visitor.exec(bbb).ijParamToCalleesMultimap).hasSize(2);
    assertThat(visitor.exec(ccc).ijParamToCalleesMultimap).hasSize(5);
    assertThat(visitor.exec(ccc).ijParamToCalleesMultimap.keySet()).hasSize(4);
    assertThat(visitor.exec(aaa).ijParamToCalleesMultimap).hasSize(7);
    assertThat(visitor.exec(aaa).ijParamToCalleesMultimap.keySet()).hasSize(5);
  }

  @Test
  public void testSimpleRecursion() {

    // Tests direct recursion (cycle of 1) and indirect recursion with a cycle of 2.

    // aaa -> bbb, bbb -> {bbb, ccc}, ccc -> bbb.
    String fileContent =
        ""
            + "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .aaa}\n"
            + "  {call .bbb /} {$ij.boo} {$ij.foo}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .bbb}\n"
            + "  {$ij.boo} {$ij.goo} {call .bbb /} {call .ccc /}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .ccc}\n"
            + "  {$ij.boo} {call .bbb /} {$ij.moo}\n"
            + "{/template}\n";

    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(fileContent).parse().fileSet();
    TemplateRegistry templateRegistry = new TemplateRegistry(soyTree, FAIL);

    TemplateNode aaa = soyTree.getChild(0).getChild(0);
    TemplateNode bbb = soyTree.getChild(0).getChild(1);
    TemplateNode ccc = soyTree.getChild(0).getChild(2);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 2 (bbb -> bbb).
    // Exercises: processCalleeHelper case 3 (ccc -> bbb).
    // Exercises: processCalleeHelper case 5 with incorporateCalleeVisitInfo case 2 (bbb -> ccc).
    FindIjParamsVisitor visitor = new FindIjParamsVisitor(templateRegistry);
    visitor.exec(aaa);
    assertThat(visitor.exec(ccc).ijParamToCalleesMultimap).hasSize(4);
    assertThat(visitor.exec(ccc).ijParamToCalleesMultimap.keySet()).hasSize(3);
    assertThat(visitor.exec(bbb).ijParamToCalleesMultimap).hasSize(4);
    assertThat(visitor.exec(bbb).ijParamToCalleesMultimap.keySet()).hasSize(3);
    assertThat(visitor.exec(aaa).ijParamToCalleesMultimap).hasSize(6);
    assertThat(visitor.exec(aaa).ijParamToCalleesMultimap.keySet()).hasSize(4);
  }

  @Test
  public void testLargerRecursiveCycle() {

    // Tests indirect recursion with a cycle of 3.

    // aaa -> bbb, bbb -> ccc, ccc -> aaa.
    String fileContent =
        ""
            + "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .aaa}\n"
            + "  {$ij.foo} {$ij.boo} {call .bbb /}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .bbb}\n"
            + "  {$ij.goo} {call .ccc /} {$ij.boo}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .ccc}\n"
            + "  {call .aaa /} {$ij.moo} {$ij.boo}\n"
            + "{/template}\n";

    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(fileContent).parse().fileSet();
    TemplateRegistry templateRegistry = new TemplateRegistry(soyTree, FAIL);

    TemplateNode aaa = soyTree.getChild(0).getChild(0);
    TemplateNode bbb = soyTree.getChild(0).getChild(1);
    TemplateNode ccc = soyTree.getChild(0).getChild(2);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 3 (ccc-> aaa).
    // Exercises: processCalleeHelper case 5 with incorporateCalleeVisitInfo case 3 (bbb -> ccc).
    // Exercises: processCalleeHelper case 5 with incorporateCalleeVisitInfo case 2 (aaa -> bbb).
    FindIjParamsVisitor visitor = new FindIjParamsVisitor(templateRegistry);
    visitor.exec(aaa);
    assertThat(visitor.exec(ccc).ijParamToCalleesMultimap).hasSize(6);
    assertThat(visitor.exec(ccc).ijParamToCalleesMultimap.keySet()).hasSize(4);
    assertThat(visitor.exec(bbb).ijParamToCalleesMultimap).hasSize(6);
    assertThat(visitor.exec(bbb).ijParamToCalleesMultimap.keySet()).hasSize(4);
    assertThat(visitor.exec(aaa).ijParamToCalleesMultimap).hasSize(6);
    assertThat(visitor.exec(aaa).ijParamToCalleesMultimap.keySet()).hasSize(4);
  }

  @Test
  public void testTwoPathsToSameRecursiveCycle() {

    // aaa -> {bbb, ccc}, bbb -> ddd, ccc -> ddd, ddd -> bbb.
    String fileContent =
        ""
            + "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .aaa}\n"
            + "  {$ij.boo} {$ij.foo} {call .bbb /} {call .ccc /}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .bbb}\n"
            + "  {$ij.boo} {$ij.goo} {call .ddd /}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .ccc}\n"
            + "  {$ij.boo} {$ij.moo} {call .ddd /}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .ddd}\n"
            + "  {$ij.boo} {$ij.too} {call .bbb /}\n"
            + "{/template}\n";

    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(fileContent).parse().fileSet();
    TemplateRegistry templateRegistry = new TemplateRegistry(soyTree, FAIL);

    TemplateNode aaa = soyTree.getChild(0).getChild(0);
    TemplateNode bbb = soyTree.getChild(0).getChild(1);
    TemplateNode ccc = soyTree.getChild(0).getChild(2);
    TemplateNode ddd = soyTree.getChild(0).getChild(3);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 4 with incorporateCalleeVisitInfo case 4 (ccc -> ddd).
    FindIjParamsVisitor visitor = new FindIjParamsVisitor(templateRegistry);
    visitor.exec(aaa);
    assertThat(visitor.exec(bbb).ijParamToCalleesMultimap).hasSize(4);
    assertThat(visitor.exec(bbb).ijParamToCalleesMultimap.keySet()).hasSize(3);
    assertThat(visitor.exec(ddd).ijParamToCalleesMultimap).hasSize(4);
    assertThat(visitor.exec(ddd).ijParamToCalleesMultimap.keySet()).hasSize(3);
    assertThat(visitor.exec(ccc).ijParamToCalleesMultimap).hasSize(6);
    assertThat(visitor.exec(ccc).ijParamToCalleesMultimap.keySet()).hasSize(4);
    assertThat(visitor.exec(aaa).ijParamToCalleesMultimap).hasSize(8);
    assertThat(visitor.exec(aaa).ijParamToCalleesMultimap.keySet()).hasSize(5);
  }

  @Test
  public void testSmallerRecursiveCycleInLargerRecursiveCycle() {

    // aaa -> {bbb, ccc}, bbb -> aaa, ccc -> bbb.
    String fileContent =
        ""
            + "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .aaa}\n"
            + "  {$ij.foo} {$ij.boo} {call .bbb /} {call .ccc /}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .bbb}\n"
            + "  {$ij.goo} {$ij.boo} {call .aaa /}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .ccc}\n"
            + "  {$ij.moo} {$ij.boo} {call .bbb /}\n"
            + "{/template}\n";

    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(fileContent).parse().fileSet();
    TemplateRegistry templateRegistry = new TemplateRegistry(soyTree, FAIL);

    TemplateNode aaa = soyTree.getChild(0).getChild(0);
    TemplateNode bbb = soyTree.getChild(0).getChild(1);
    TemplateNode ccc = soyTree.getChild(0).getChild(2);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 4 with incorporateCalleeVisitInfo case 3 (ccc -> bbb).
    FindIjParamsVisitor visitor = new FindIjParamsVisitor(templateRegistry);
    visitor.exec(aaa);
    assertThat(visitor.exec(ccc).ijParamToCalleesMultimap).hasSize(6);
    assertThat(visitor.exec(ccc).ijParamToCalleesMultimap.keySet()).hasSize(4);
    assertThat(visitor.exec(bbb).ijParamToCalleesMultimap).hasSize(6);
    assertThat(visitor.exec(bbb).ijParamToCalleesMultimap.keySet()).hasSize(4);
    assertThat(visitor.exec(aaa).ijParamToCalleesMultimap).hasSize(6);
    assertThat(visitor.exec(aaa).ijParamToCalleesMultimap.keySet()).hasSize(4);
  }

  @Test
  public void testExecForAllTemplates() {

    // aaa -> {bbb, ccc}, bbb -> ddd.
    String fileContent =
        ""
            + "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .bbb}\n"
            + "  {$ij.boo} {$ij.goo} {call .ddd /}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .aaa}\n"
            + "  {call .bbb /} {$ij.boo} {call .ccc /} {$ij.foo}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .ccc}\n"
            + "  {$ij.boo} {$ij.moo + $ij.woo}\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{template .ddd}\n"
            + "  {$ij.boo} {$ij.moo} {round($ij.zoo)}\n"
            + "{/template}\n";

    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(fileContent).parse().fileSet();
    TemplateRegistry templateRegistry = new TemplateRegistry(soyTree, FAIL);
    TemplateNode bbb = soyTree.getChild(0).getChild(0);
    TemplateNode aaa = soyTree.getChild(0).getChild(1);
    TemplateNode ccc = soyTree.getChild(0).getChild(2);
    TemplateNode ddd = soyTree.getChild(0).getChild(3);

    ImmutableMap<TemplateNode, IjParamsInfo> templateToIjParamsInfoMap =
        new FindIjParamsVisitor(templateRegistry).execOnAllTemplates(soyTree);
    assertThat(templateToIjParamsInfoMap).hasSize(4);
    assertThat(templateToIjParamsInfoMap.get(ddd).ijParamToCalleesMultimap).hasSize(3);
    assertThat(templateToIjParamsInfoMap.get(ccc).ijParamToCalleesMultimap).hasSize(3);
    assertThat(templateToIjParamsInfoMap.get(bbb).ijParamToCalleesMultimap).hasSize(5);
    assertThat(templateToIjParamsInfoMap.get(bbb).ijParamToCalleesMultimap.keySet()).hasSize(4);
    assertThat(templateToIjParamsInfoMap.get(aaa).ijParamToCalleesMultimap).hasSize(10);
    assertThat(templateToIjParamsInfoMap.get(aaa).ijParamToCalleesMultimap.keySet()).hasSize(6);
  }
}
