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

package com.google.template.soy;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jbcsrc.BytecodeCompiler;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.shared.internal.NoOpScopedData;
import com.google.template.soy.tofu.SoyTofu;
import com.google.template.soy.tofu.internal.BaseTofu;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests the functinoality in tofu and soysauce for calculating transitive ij parameters. */
@RunWith(JUnit4.class)
public final class TransitiveIjParamsTest {

  @Test
  public void testSimple() {

    // aaa -> {bbb, ccc}, bbb -> ddd.
    String fileContent =
        ""
            + "{namespace ns}\n"
            + "\n"
            + "{template .aaa}\n"
            + "  {call .bbb /} {$ij.boo} {call .ccc /} {$ij.foo}\n"
            + "{/template}\n"
            + "\n"
            + "{template .bbb}\n"
            + "  {$ij.boo} {$ij.goo} {call .ddd /}\n"
            + "{/template}\n"
            + "\n"
            + "{template .ccc}\n"
            + "  {$ij.boo} {$ij.moo + $ij.woo}\n"
            + "{/template}\n"
            + "\n"
            + "{template .ddd}\n"
            + "  {$ij.boo} {$ij.moo} {round($ij.zoo)}\n"
            + "{/template}\n";
    IjsTester tester = new IjsTester(fileContent);

    assertThat(tester.calculateIjs("ns.ddd")).containsExactly("boo", "moo", "zoo");
    assertThat(tester.calculateIjs("ns.ccc")).containsExactly("boo", "moo", "woo");
    assertThat(tester.calculateIjs("ns.bbb")).containsExactly("boo", "goo", "moo", "zoo");
    assertThat(tester.calculateIjs("ns.aaa"))
        .containsExactly("boo", "goo", "moo", "zoo", "woo", "foo");
  }

  @Test
  public void testTwoPathsToSameTemplate() {

    // aaa -> {bbb, ccc}, ccc -> bbb.
    String fileContent =
        ""
            + "{namespace ns}\n"
            + "\n"
            + "{template .aaa}\n"
            + "  {call .bbb /} {$ij.boo} {call .ccc /} {$ij.foo}\n"
            + "{/template}\n"
            + "\n"
            + "{template .bbb}\n"
            + "  {$ij.boo} {$ij.goo}\n"
            + "{/template}\n"
            + "\n"
            + "{template .ccc}\n"
            + "  {$ij.boo} {$ij.moo + $ij.woo} {call .bbb /}\n"
            + "{/template}\n";
    IjsTester tester = new IjsTester(fileContent);

    // Exercises: processCalleeHelper case 4 with incorporateCalleeVisitInfo case 1 (ccc -> bbb).
    assertThat(tester.calculateIjs("ns.bbb")).containsExactly("boo", "goo");
    assertThat(tester.calculateIjs("ns.ccc")).containsExactly("boo", "moo", "woo", "goo");
    assertThat(tester.calculateIjs("ns.aaa")).containsExactly("boo", "moo", "woo", "goo", "foo");
  }

  @Test
  public void testSimpleRecursion() {

    // Tests direct recursion (cycle of 1) and indirect recursion with a cycle of 2.

    // aaa -> bbb, bbb -> {bbb, ccc}, ccc -> bbb.
    String fileContent =
        ""
            + "{namespace ns}\n"
            + "\n"
            + "{template .aaa}\n"
            + "  {call .bbb /} {$ij.boo} {$ij.foo}\n"
            + "{/template}\n"
            + "\n"
            + "{template .bbb}\n"
            + "  {$ij.boo} {$ij.goo} {call .bbb /} {call .ccc /}\n"
            + "{/template}\n"
            + "\n"
            + "{template .ccc}\n"
            + "  {$ij.boo} {call .bbb /} {$ij.moo}\n"
            + "{/template}\n";
    IjsTester tester = new IjsTester(fileContent);

    // Test with exec("ns.aaa").
    // Exercises: processCalleeHelper case 2 (bbb -> bbb).
    // Exercises: processCalleeHelper case 3 (ccc -> bbb).
    // Exercises: processCalleeHelper case 5 with incorporateCalleeVisitInfo case 2 (bbb -> ccc).
    assertThat(tester.calculateIjs("ns.ccc")).containsExactly("boo", "goo", "moo");
    assertThat(tester.calculateIjs("ns.bbb")).containsExactly("boo", "goo", "moo");
    assertThat(tester.calculateIjs("ns.aaa")).containsExactly("boo", "goo", "moo", "foo");
  }

  @Test
  public void testLargerRecursiveCycle() {

    // Tests indirect recursion with a cycle of 3.

    // aaa -> bbb, bbb -> ccc, ccc -> aaa.
    String fileContent =
        ""
            + "{namespace ns}\n"
            + "\n"
            + "{template .aaa}\n"
            + "  {$ij.foo} {$ij.boo} {call .bbb /}\n"
            + "{/template}\n"
            + "\n"
            + "{template .bbb}\n"
            + "  {$ij.goo} {call .ccc /} {$ij.boo}\n"
            + "{/template}\n"
            + "\n"
            + "{template .ccc}\n"
            + "  {call .aaa /} {$ij.moo} {$ij.boo}\n"
            + "{/template}\n";
    IjsTester tester = new IjsTester(fileContent);

    assertThat(tester.calculateIjs("ns.ccc")).containsExactly("boo", "foo", "goo", "moo");
    assertThat(tester.calculateIjs("ns.bbb")).containsExactly("boo", "foo", "goo", "moo");
    assertThat(tester.calculateIjs("ns.aaa")).containsExactly("boo", "foo", "goo", "moo");
  }

  @Test
  public void testTwoPathsToSameRecursiveCycle() {

    // aaa -> {bbb, ccc}, bbb -> ddd, ccc -> ddd, ddd -> bbb.
    String fileContent =
        ""
            + "{namespace ns}\n"
            + "\n"
            + "{template .aaa}\n"
            + "  {$ij.boo} {$ij.foo} {call .bbb /} {call .ccc /}\n"
            + "{/template}\n"
            + "\n"
            + "{template .bbb}\n"
            + "  {$ij.boo} {$ij.goo} {call .ddd /}\n"
            + "{/template}\n"
            + "\n"
            + "{template .ccc}\n"
            + "  {$ij.boo} {$ij.moo} {call .ddd /}\n"
            + "{/template}\n"
            + "\n"
            + "{template .ddd}\n"
            + "  {$ij.boo} {$ij.too} {call .bbb /}\n"
            + "{/template}\n";
    IjsTester tester = new IjsTester(fileContent);

    // Test with exec("ns.aaa").
    // Exercises: processCalleeHelper case 4 with incorporateCalleeVisitInfo case 4 (ccc -> ddd).
    assertThat(tester.calculateIjs("ns.bbb")).containsExactly("boo", "goo", "too");
    assertThat(tester.calculateIjs("ns.ddd")).containsExactly("boo", "goo", "too");
    assertThat(tester.calculateIjs("ns.ccc")).containsExactly("boo", "moo", "too", "goo");
    assertThat(tester.calculateIjs("ns.aaa")).containsExactly("boo", "foo", "goo", "moo", "too");
  }

  @Test
  public void testSmallerRecursiveCycleInLargerRecursiveCycle() {

    // aaa -> {bbb, ccc}, bbb -> aaa, ccc -> bbb.
    String fileContent =
        ""
            + "{namespace ns}\n"
            + "\n"
            + "{template .aaa}\n"
            + "  {$ij.foo} {$ij.boo} {call .bbb /} {call .ccc /}\n"
            + "{/template}\n"
            + "\n"
            + "{template .bbb}\n"
            + "  {$ij.goo} {$ij.boo} {call .aaa /}\n"
            + "{/template}\n"
            + "\n"
            + "{template .ccc}\n"
            + "  {$ij.moo} {$ij.boo} {call .bbb /}\n"
            + "{/template}\n";

    IjsTester tester = new IjsTester(fileContent);

    // Test with exec("ns.aaa").
    // Exercises: processCalleeHelper case 4 with incorporateCalleeVisitInfo case 3 (ccc -> bbb).
    ImmutableSet<String> allIjs = ImmutableSet.of("moo", "boo", "goo", "foo");
    assertThat(tester.calculateIjs("ns.ccc")).isEqualTo(allIjs);
    assertThat(tester.calculateIjs("ns.bbb")).isEqualTo(allIjs);
    assertThat(tester.calculateIjs("ns.aaa")).isEqualTo(allIjs);
  }

  static final class IjsTester {
    final SoyTofu tofu;
    final CompiledTemplates compiledTemplates;

    IjsTester(String fileContent) {
      SoyFileSetParser parser = SoyFileSetParserBuilder.forFileContents(fileContent).build();
      ParseResult result = parser.parse();
      // parserBuilder.
      tofu =
          new BaseTofu(
              new NoOpScopedData(),
              result.fileSet(),
              /*pluginInstances=*/ ImmutableMap.<String, Supplier<Object>>of());
      compiledTemplates =
          BytecodeCompiler.compile(
                  result.registry(),
                  result.fileSet(),
                  /*developmentMode=*/ false,
                  ErrorReporter.exploding(),
                  parser.soyFileSuppliers(),
                  parser.typeRegistry())
              .get();
    }

    ImmutableSet<String> calculateIjs(String templateName) {
      ImmutableSortedSet<String> tofuIjs = tofu.getUsedIjParamsForTemplate(templateName);
      ImmutableSet<String> sauceIjs =
          compiledTemplates.getTransitiveIjParamsForTemplate(templateName);
      assertThat(sauceIjs).named("sauce ijs").isEqualTo(tofuIjs);
      return sauceIjs;
    }
  }
}
