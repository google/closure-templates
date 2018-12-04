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

import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for TransitiveIjParamsCalculator.
 *
 */
@RunWith(JUnit4.class)
public final class TransitiveIjParamsCalculatorTest {

  @Test
  public void testSimple() {

    // aaa -> {bbb, ccc}, bbb -> ddd.
    String fileContent =
        ""
            + "{namespace ns}\n"
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

    TemplateRegistry templateRegistry =
        SoyFileSetParserBuilder.forFileContents(fileContent).parse().registry();

    TemplateMetadata aaa = templateRegistry.getAllTemplates().get(0);
    TemplateMetadata bbb = templateRegistry.getAllTemplates().get(1);
    TemplateMetadata ccc = templateRegistry.getAllTemplates().get(2);
    TemplateMetadata ddd = templateRegistry.getAllTemplates().get(3);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 5 with incorporateCalleeVisitInfo case 1 (aaa -> bbb).
    TransitiveIjParamsCalculator visitor = new TransitiveIjParamsCalculator(templateRegistry);
    visitor.calculateIjs(aaa);
    assertThat(visitor.calculateIjs(ddd).ijParamToCalleesMultimap).hasSize(3);
    assertThat(visitor.calculateIjs(ccc).ijParamToCalleesMultimap).hasSize(3);
    assertThat(visitor.calculateIjs(bbb).ijParamToCalleesMultimap).hasSize(5);
    assertThat(visitor.calculateIjs(bbb).ijParamToCalleesMultimap.keySet()).hasSize(4);
    assertThat(visitor.calculateIjs(aaa).ijParamToCalleesMultimap).hasSize(10);
    assertThat(visitor.calculateIjs(aaa).ijParamToCalleesMultimap.keySet()).hasSize(6);

    // Test with exec(bbb) then exec(aaa).
    // Exercises: processCalleeHelper case 1 (aaa -> bbb).
    visitor = new TransitiveIjParamsCalculator(templateRegistry);
    visitor.calculateIjs(bbb);
    assertThat(visitor.calculateIjs(ddd).ijParamToCalleesMultimap).hasSize(3);
    assertThat(visitor.calculateIjs(bbb).ijParamToCalleesMultimap).hasSize(5);
    assertThat(visitor.calculateIjs(bbb).ijParamToCalleesMultimap.keySet()).hasSize(4);
    visitor.calculateIjs(aaa);
    assertThat(visitor.calculateIjs(ddd).ijParamToCalleesMultimap).hasSize(3);
    assertThat(visitor.calculateIjs(ccc).ijParamToCalleesMultimap).hasSize(3);
    assertThat(visitor.calculateIjs(bbb).ijParamToCalleesMultimap).hasSize(5);
    assertThat(visitor.calculateIjs(bbb).ijParamToCalleesMultimap.keySet()).hasSize(4);
    assertThat(visitor.calculateIjs(aaa).ijParamToCalleesMultimap).hasSize(10);
    assertThat(visitor.calculateIjs(aaa).ijParamToCalleesMultimap.keySet()).hasSize(6);
  }

  @Test
  public void testTwoPathsToSameTemplate() {

    // aaa -> {bbb, ccc}, ccc -> bbb.
    String fileContent =
        ""
            + "{namespace ns}\n"
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

    TemplateRegistry templateRegistry =
        SoyFileSetParserBuilder.forFileContents(fileContent).parse().registry();

    TemplateMetadata aaa = templateRegistry.getAllTemplates().get(0);
    TemplateMetadata bbb = templateRegistry.getAllTemplates().get(1);
    TemplateMetadata ccc = templateRegistry.getAllTemplates().get(2);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 4 with incorporateCalleeVisitInfo case 1 (ccc -> bbb).
    TransitiveIjParamsCalculator visitor = new TransitiveIjParamsCalculator(templateRegistry);
    visitor.calculateIjs(aaa);
    assertThat(visitor.calculateIjs(bbb).ijParamToCalleesMultimap).hasSize(2);
    assertThat(visitor.calculateIjs(ccc).ijParamToCalleesMultimap).hasSize(5);
    assertThat(visitor.calculateIjs(ccc).ijParamToCalleesMultimap.keySet()).hasSize(4);
    assertThat(visitor.calculateIjs(aaa).ijParamToCalleesMultimap).hasSize(7);
    assertThat(visitor.calculateIjs(aaa).ijParamToCalleesMultimap.keySet()).hasSize(5);
  }

  @Test
  public void testSimpleRecursion() {

    // Tests direct recursion (cycle of 1) and indirect recursion with a cycle of 2.

    // aaa -> bbb, bbb -> {bbb, ccc}, ccc -> bbb.
    String fileContent =
        ""
            + "{namespace ns}\n"
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

    TemplateRegistry templateRegistry =
        SoyFileSetParserBuilder.forFileContents(fileContent).parse().registry();

    TemplateMetadata aaa = templateRegistry.getAllTemplates().get(0);
    TemplateMetadata bbb = templateRegistry.getAllTemplates().get(1);
    TemplateMetadata ccc = templateRegistry.getAllTemplates().get(2);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 2 (bbb -> bbb).
    // Exercises: processCalleeHelper case 3 (ccc -> bbb).
    // Exercises: processCalleeHelper case 5 with incorporateCalleeVisitInfo case 2 (bbb -> ccc).
    TransitiveIjParamsCalculator visitor = new TransitiveIjParamsCalculator(templateRegistry);
    visitor.calculateIjs(aaa);
    assertThat(visitor.calculateIjs(ccc).ijParamToCalleesMultimap).hasSize(4);
    assertThat(visitor.calculateIjs(ccc).ijParamToCalleesMultimap.keySet()).hasSize(3);
    assertThat(visitor.calculateIjs(bbb).ijParamToCalleesMultimap).hasSize(4);
    assertThat(visitor.calculateIjs(bbb).ijParamToCalleesMultimap.keySet()).hasSize(3);
    assertThat(visitor.calculateIjs(aaa).ijParamToCalleesMultimap).hasSize(6);
    assertThat(visitor.calculateIjs(aaa).ijParamToCalleesMultimap.keySet()).hasSize(4);
  }

  @Test
  public void testLargerRecursiveCycle() {

    // Tests indirect recursion with a cycle of 3.

    // aaa -> bbb, bbb -> ccc, ccc -> aaa.
    String fileContent =
        ""
            + "{namespace ns}\n"
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

    TemplateRegistry templateRegistry =
        SoyFileSetParserBuilder.forFileContents(fileContent).parse().registry();

    TemplateMetadata aaa = templateRegistry.getAllTemplates().get(0);
    TemplateMetadata bbb = templateRegistry.getAllTemplates().get(1);
    TemplateMetadata ccc = templateRegistry.getAllTemplates().get(2);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 3 (ccc-> aaa).
    // Exercises: processCalleeHelper case 5 with incorporateCalleeVisitInfo case 3 (bbb -> ccc).
    // Exercises: processCalleeHelper case 5 with incorporateCalleeVisitInfo case 2 (aaa -> bbb).
    TransitiveIjParamsCalculator visitor = new TransitiveIjParamsCalculator(templateRegistry);
    visitor.calculateIjs(aaa);
    assertThat(visitor.calculateIjs(ccc).ijParamToCalleesMultimap).hasSize(6);
    assertThat(visitor.calculateIjs(ccc).ijParamToCalleesMultimap.keySet()).hasSize(4);
    assertThat(visitor.calculateIjs(bbb).ijParamToCalleesMultimap).hasSize(6);
    assertThat(visitor.calculateIjs(bbb).ijParamToCalleesMultimap.keySet()).hasSize(4);
    assertThat(visitor.calculateIjs(aaa).ijParamToCalleesMultimap).hasSize(6);
    assertThat(visitor.calculateIjs(aaa).ijParamToCalleesMultimap.keySet()).hasSize(4);
  }

  @Test
  public void testTwoPathsToSameRecursiveCycle() {

    // aaa -> {bbb, ccc}, bbb -> ddd, ccc -> ddd, ddd -> bbb.
    String fileContent =
        ""
            + "{namespace ns}\n"
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

    TemplateRegistry templateRegistry =
        SoyFileSetParserBuilder.forFileContents(fileContent).parse().registry();

    TemplateMetadata aaa = templateRegistry.getAllTemplates().get(0);
    TemplateMetadata bbb = templateRegistry.getAllTemplates().get(1);
    TemplateMetadata ccc = templateRegistry.getAllTemplates().get(2);
    TemplateMetadata ddd = templateRegistry.getAllTemplates().get(3);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 4 with incorporateCalleeVisitInfo case 4 (ccc -> ddd).
    TransitiveIjParamsCalculator visitor = new TransitiveIjParamsCalculator(templateRegistry);
    visitor.calculateIjs(aaa);
    assertThat(visitor.calculateIjs(bbb).ijParamToCalleesMultimap).hasSize(4);
    assertThat(visitor.calculateIjs(bbb).ijParamToCalleesMultimap.keySet()).hasSize(3);
    assertThat(visitor.calculateIjs(ddd).ijParamToCalleesMultimap).hasSize(4);
    assertThat(visitor.calculateIjs(ddd).ijParamToCalleesMultimap.keySet()).hasSize(3);
    assertThat(visitor.calculateIjs(ccc).ijParamToCalleesMultimap).hasSize(6);
    assertThat(visitor.calculateIjs(ccc).ijParamToCalleesMultimap.keySet()).hasSize(4);
    assertThat(visitor.calculateIjs(aaa).ijParamToCalleesMultimap).hasSize(8);
    assertThat(visitor.calculateIjs(aaa).ijParamToCalleesMultimap.keySet()).hasSize(5);
  }

  @Test
  public void testSmallerRecursiveCycleInLargerRecursiveCycle() {

    // aaa -> {bbb, ccc}, bbb -> aaa, ccc -> bbb.
    String fileContent =
        ""
            + "{namespace ns}\n"
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

    TemplateRegistry templateRegistry =
        SoyFileSetParserBuilder.forFileContents(fileContent).parse().registry();

    TemplateMetadata aaa = templateRegistry.getAllTemplates().get(0);
    TemplateMetadata bbb = templateRegistry.getAllTemplates().get(1);
    TemplateMetadata ccc = templateRegistry.getAllTemplates().get(2);

    // Test with exec(aaa).
    // Exercises: processCalleeHelper case 4 with incorporateCalleeVisitInfo case 3 (ccc -> bbb).
    TransitiveIjParamsCalculator visitor = new TransitiveIjParamsCalculator(templateRegistry);
    visitor.calculateIjs(aaa);
    assertThat(visitor.calculateIjs(ccc).ijParamToCalleesMultimap).hasSize(6);
    assertThat(visitor.calculateIjs(ccc).ijParamToCalleesMultimap.keySet()).hasSize(4);
    assertThat(visitor.calculateIjs(bbb).ijParamToCalleesMultimap).hasSize(6);
    assertThat(visitor.calculateIjs(bbb).ijParamToCalleesMultimap.keySet()).hasSize(4);
    assertThat(visitor.calculateIjs(aaa).ijParamToCalleesMultimap).hasSize(6);
    assertThat(visitor.calculateIjs(aaa).ijParamToCalleesMultimap.keySet()).hasSize(4);
  }
}
