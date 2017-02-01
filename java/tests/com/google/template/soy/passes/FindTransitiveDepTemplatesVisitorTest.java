/*
 * Copyright 2013 Google Inc.
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
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.passes.FindTransitiveDepTemplatesVisitor.TransitiveDepTemplatesInfo;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for FindTransitiveDepTemplatesVisitor.
 *
 */
@RunWith(JUnit4.class)
public final class FindTransitiveDepTemplatesVisitorTest {

  private static final ErrorReporter FAIL = ExplodingErrorReporter.get();

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

    ParseResult result =
        SoyFileSetParserBuilder.forFileContents(fileContent).errorReporter(FAIL).parse();
    TemplateRegistry templateRegistry = result.registry();
    SoyFileSetNode soyTree = result.fileSet();

    TemplateNode aaa = soyTree.getChild(0).getChild(0);
    TemplateNode bbb = soyTree.getChild(0).getChild(1);
    TemplateNode ccc = soyTree.getChild(0).getChild(2);
    TemplateNode ddd = soyTree.getChild(0).getChild(3);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 5 with incorporateCalleeVisitInfo case 1 (aaa -> bbb).
    FindTransitiveDepTemplatesVisitor visitor =
        new FindTransitiveDepTemplatesVisitor(templateRegistry);
    Map<TemplateNode, TransitiveDepTemplatesInfo> memoizedInfoMap =
        visitor.templateToFinishedInfoMap;
    visitor.exec(aaa);
    assertThat(memoizedInfoMap).hasSize(4);
    assertThat(memoizedInfoMap.get(ddd).depTemplateSet).isEqualTo(ImmutableSet.of(ddd));
    assertThat(memoizedInfoMap.get(ccc).depTemplateSet).isEqualTo(ImmutableSet.of(ccc));
    assertThat(memoizedInfoMap.get(bbb).depTemplateSet).isEqualTo(ImmutableSet.of(bbb, ddd));
    assertThat(memoizedInfoMap.get(aaa).depTemplateSet)
        .isEqualTo(ImmutableSet.of(aaa, bbb, ccc, ddd));

    // Test with exec(bbb) then exec(aaa).
    // Exercises: processCalleeHelper case 1 (aaa -> bbb).
    visitor = new FindTransitiveDepTemplatesVisitor(templateRegistry);
    memoizedInfoMap = visitor.templateToFinishedInfoMap;
    visitor.exec(bbb);
    assertThat(memoizedInfoMap).hasSize(2);
    assertThat(memoizedInfoMap.get(ddd).depTemplateSet).isEqualTo(ImmutableSet.of(ddd));
    assertThat(memoizedInfoMap.get(bbb).depTemplateSet).isEqualTo(ImmutableSet.of(bbb, ddd));
    visitor.exec(aaa);
    assertThat(memoizedInfoMap).hasSize(4);
    assertThat(memoizedInfoMap.get(ddd).depTemplateSet).isEqualTo(ImmutableSet.of(ddd));
    assertThat(memoizedInfoMap.get(ccc).depTemplateSet).isEqualTo(ImmutableSet.of(ccc));
    assertThat(memoizedInfoMap.get(bbb).depTemplateSet).isEqualTo(ImmutableSet.of(bbb, ddd));
    assertThat(memoizedInfoMap.get(aaa).depTemplateSet)
        .isEqualTo(ImmutableSet.of(aaa, bbb, ccc, ddd));
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

    ParseResult result =
        SoyFileSetParserBuilder.forFileContents(fileContent).errorReporter(FAIL).parse();
    TemplateRegistry templateRegistry = result.registry();
    SoyFileSetNode soyTree = result.fileSet();

    TemplateNode aaa = soyTree.getChild(0).getChild(0);
    TemplateNode bbb = soyTree.getChild(0).getChild(1);
    TemplateNode ccc = soyTree.getChild(0).getChild(2);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 4 with incorporateCalleeVisitInfo case 1 (ccc -> bbb).
    FindTransitiveDepTemplatesVisitor visitor =
        new FindTransitiveDepTemplatesVisitor(templateRegistry);
    Map<TemplateNode, TransitiveDepTemplatesInfo> memoizedInfoMap =
        visitor.templateToFinishedInfoMap;
    visitor.exec(aaa);
    assertThat(memoizedInfoMap).hasSize(3);
    assertThat(memoizedInfoMap.get(bbb).depTemplateSet).isEqualTo(ImmutableSet.of(bbb));
    assertThat(memoizedInfoMap.get(ccc).depTemplateSet).isEqualTo(ImmutableSet.of(ccc, bbb));
    assertThat(memoizedInfoMap.get(aaa).depTemplateSet).isEqualTo(ImmutableSet.of(aaa, bbb, ccc));
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

    ParseResult result =
        SoyFileSetParserBuilder.forFileContents(fileContent).errorReporter(FAIL).parse();
    TemplateRegistry templateRegistry = result.registry();
    SoyFileSetNode soyTree = result.fileSet();

    TemplateNode aaa = soyTree.getChild(0).getChild(0);
    TemplateNode bbb = soyTree.getChild(0).getChild(1);
    TemplateNode ccc = soyTree.getChild(0).getChild(2);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 2 (bbb -> bbb).
    // Exercises: processCalleeHelper case 3 (ccc -> bbb).
    // Exercises: processCalleeHelper case 5 with incorporateCalleeVisitInfo case 2 (bbb -> ccc).
    FindTransitiveDepTemplatesVisitor visitor =
        new FindTransitiveDepTemplatesVisitor(templateRegistry);
    Map<TemplateNode, TransitiveDepTemplatesInfo> memoizedInfoMap =
        visitor.templateToFinishedInfoMap;
    visitor.exec(aaa);
    assertThat(memoizedInfoMap).hasSize(3);
    assertThat(memoizedInfoMap.get(ccc).depTemplateSet).isEqualTo(ImmutableSet.of(ccc, bbb));
    assertThat(memoizedInfoMap.get(bbb).depTemplateSet).isEqualTo(ImmutableSet.of(bbb, ccc));
    assertThat(memoizedInfoMap.get(aaa).depTemplateSet).isEqualTo(ImmutableSet.of(aaa, bbb, ccc));
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

    ParseResult result =
        SoyFileSetParserBuilder.forFileContents(fileContent).errorReporter(FAIL).parse();
    TemplateRegistry templateRegistry = result.registry();
    SoyFileSetNode soyTree = result.fileSet();

    TemplateNode aaa = soyTree.getChild(0).getChild(0);
    TemplateNode bbb = soyTree.getChild(0).getChild(1);
    TemplateNode ccc = soyTree.getChild(0).getChild(2);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 3 (ccc-> aaa).
    // Exercises: processCalleeHelper case 5 with incorporateCalleeVisitInfo case 3 (bbb -> ccc).
    // Exercises: processCalleeHelper case 5 with incorporateCalleeVisitInfo case 2 (aaa -> bbb).
    FindTransitiveDepTemplatesVisitor visitor =
        new FindTransitiveDepTemplatesVisitor(templateRegistry);
    Map<TemplateNode, TransitiveDepTemplatesInfo> memoizedInfoMap =
        visitor.templateToFinishedInfoMap;
    visitor.exec(aaa);
    assertThat(memoizedInfoMap).hasSize(3);
    assertThat(memoizedInfoMap.get(ccc).depTemplateSet).isEqualTo(ImmutableSet.of(ccc, aaa, bbb));
    assertThat(memoizedInfoMap.get(bbb).depTemplateSet).isEqualTo(ImmutableSet.of(bbb, ccc, aaa));
    assertThat(memoizedInfoMap.get(aaa).depTemplateSet).isEqualTo(ImmutableSet.of(aaa, bbb, ccc));
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

    ParseResult result =
        SoyFileSetParserBuilder.forFileContents(fileContent).errorReporter(FAIL).parse();
    TemplateRegistry templateRegistry = result.registry();
    SoyFileSetNode soyTree = result.fileSet();

    TemplateNode aaa = soyTree.getChild(0).getChild(0);
    TemplateNode bbb = soyTree.getChild(0).getChild(1);
    TemplateNode ccc = soyTree.getChild(0).getChild(2);
    TemplateNode ddd = soyTree.getChild(0).getChild(3);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 4 with incorporateCalleeVisitInfo case 4 (ccc -> ddd).
    FindTransitiveDepTemplatesVisitor visitor =
        new FindTransitiveDepTemplatesVisitor(templateRegistry);
    Map<TemplateNode, TransitiveDepTemplatesInfo> memoizedInfoMap =
        visitor.templateToFinishedInfoMap;
    visitor.exec(aaa);
    assertThat(memoizedInfoMap).hasSize(4);
    assertThat(memoizedInfoMap.get(bbb).depTemplateSet).isEqualTo(ImmutableSet.of(bbb, ddd));
    assertThat(memoizedInfoMap.get(ddd).depTemplateSet).isEqualTo(ImmutableSet.of(ddd, bbb));
    assertThat(memoizedInfoMap.get(ccc).depTemplateSet).isEqualTo(ImmutableSet.of(ccc, ddd, bbb));
    assertThat(memoizedInfoMap.get(aaa).depTemplateSet)
        .isEqualTo(ImmutableSet.of(aaa, bbb, ccc, ddd));
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

    ParseResult result =
        SoyFileSetParserBuilder.forFileContents(fileContent).errorReporter(FAIL).parse();
    TemplateRegistry templateRegistry = result.registry();
    SoyFileSetNode soyTree = result.fileSet();

    TemplateNode aaa = soyTree.getChild(0).getChild(0);
    TemplateNode bbb = soyTree.getChild(0).getChild(1);
    TemplateNode ccc = soyTree.getChild(0).getChild(2);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 4 with incorporateCalleeVisitInfo case 3 (ccc -> bbb).
    FindTransitiveDepTemplatesVisitor visitor =
        new FindTransitiveDepTemplatesVisitor(templateRegistry);
    Map<TemplateNode, TransitiveDepTemplatesInfo> memoizedInfoMap =
        visitor.templateToFinishedInfoMap;
    visitor.exec(aaa);
    assertThat(memoizedInfoMap).hasSize(3);
    assertThat(memoizedInfoMap.get(ccc).depTemplateSet).isEqualTo(ImmutableSet.of(ccc, bbb, aaa));
    assertThat(memoizedInfoMap.get(bbb).depTemplateSet).isEqualTo(ImmutableSet.of(bbb, aaa, ccc));
    assertThat(memoizedInfoMap.get(aaa).depTemplateSet).isEqualTo(ImmutableSet.of(aaa, bbb, ccc));
  }

  @Test
  public void testExecOnAllTemplates() {

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

    ParseResult result =
        SoyFileSetParserBuilder.forFileContents(fileContent).errorReporter(FAIL).parse();
    TemplateRegistry templateRegistry = result.registry();
    SoyFileSetNode soyTree = result.fileSet();

    TemplateNode bbb = soyTree.getChild(0).getChild(0);
    TemplateNode aaa = soyTree.getChild(0).getChild(1);
    TemplateNode ccc = soyTree.getChild(0).getChild(2);
    TemplateNode ddd = soyTree.getChild(0).getChild(3);

    ImmutableMap<TemplateNode, TransitiveDepTemplatesInfo> resultMap =
        new FindTransitiveDepTemplatesVisitor(templateRegistry).execOnAllTemplates(soyTree);
    assertThat(resultMap).hasSize(4);
    assertThat(resultMap.get(ddd).depTemplateSet).isEqualTo(ImmutableSet.of(ddd));
    assertThat(resultMap.get(ccc).depTemplateSet).isEqualTo(ImmutableSet.of(ccc));
    assertThat(resultMap.get(bbb).depTemplateSet).isEqualTo(ImmutableSet.of(bbb, ddd));
    assertThat(resultMap.get(aaa).depTemplateSet).isEqualTo(ImmutableSet.of(aaa, bbb, ccc, ddd));
  }
}
