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
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jbcsrc.BytecodeCompiler;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.shared.internal.NoOpScopedData;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
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
            + "  {@inject boo: ?}\n"
            + "  {@inject foo: ?}\n"
            + "  {call .bbb /} {$boo} {call .ccc /} {$foo}\n"
            + "{/template}\n"
            + "\n"
            + "{template .bbb}\n"
            + "  {@inject boo: ?}\n"
            + "  {@inject goo: ?}\n"
            + "  {$boo} {$goo} {call .ddd /}\n"
            + "{/template}\n"
            + "\n"
            + "{template .ccc}\n"
            + "  {@inject boo: ?}\n"
            + "  {@inject moo: ?}\n"
            + "  {@inject woo: ?}\n"
            + "  {$boo} {$moo + $woo}\n"
            + "{/template}\n"
            + "\n"
            + "{template .ddd}\n"
            + "  {@inject boo: ?}\n"
            + "  {@inject moo: ?}\n"
            + "  {@inject zoo: ?}\n"
            + "  {$boo} {$moo} {round($zoo)}\n"
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
            + "  {@inject boo: ?}\n"
            + "  {@inject foo: ?}\n"
            + "  {call .bbb /} {$boo} {call .ccc /} {$foo}\n"
            + "{/template}\n"
            + "\n"
            + "{template .bbb}\n"
            + "  {@inject boo: ?}\n"
            + "  {@inject goo: ?}\n"
            + "  {$boo} {$goo}\n"
            + "{/template}\n"
            + "\n"
            + "{template .ccc}\n"
            + "  {@inject boo: ?}\n"
            + "  {@inject moo: ?}\n"
            + "  {@inject woo: ?}\n"
            + "  {$boo} {$moo + $woo} {call .bbb /}\n"
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
            + "  {@inject boo: ?}\n"
            + "  {@inject foo: ?}\n"
            + "  {call .bbb /} {$boo} {$foo}\n"
            + "{/template}\n"
            + "\n"
            + "{template .bbb}\n"
            + "  {@inject boo: ?}\n"
            + "  {@inject goo: ?}\n"
            + "  {$boo} {$goo} {call .bbb /} {call .ccc /}\n"
            + "{/template}\n"
            + "\n"
            + "{template .ccc}\n"
            + "  {@inject boo: ?}\n"
            + "  {@inject moo: ?}\n"
            + "  {$boo} {call .bbb /} {$moo}\n"
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
            + "  {@inject foo: ?}\n"
            + "  {@inject boo: ?}\n"
            + "  {$foo} {$boo} {call .bbb /}\n"
            + "{/template}\n"
            + "\n"
            + "{template .bbb}\n"
            + "  {@inject goo: ?}\n"
            + "  {@inject boo: ?}\n"
            + "  {$goo} {call .ccc /} {$boo}\n"
            + "{/template}\n"
            + "\n"
            + "{template .ccc}\n"
            + "  {@inject moo: ?}\n"
            + "  {@inject boo: ?}\n"
            + "  {call .aaa /} {$moo} {$boo}\n"
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
            + "  {@inject boo: ?}\n"
            + "  {@inject foo: ?}\n"
            + "  {$boo} {$foo} {call .bbb /} {call .ccc /}\n"
            + "{/template}\n"
            + "\n"
            + "{template .bbb}\n"
            + "  {@inject boo: ?}\n"
            + "  {@inject goo: ?}\n"
            + "  {$boo} {$goo} {call .ddd /}\n"
            + "{/template}\n"
            + "\n"
            + "{template .ccc}\n"
            + "  {@inject boo: ?}\n"
            + "  {@inject moo: ?}\n"
            + "  {$boo} {$moo} {call .ddd /}\n"
            + "{/template}\n"
            + "\n"
            + "{template .ddd}\n"
            + "  {@inject boo: ?}\n"
            + "  {@inject too: ?}\n"
            + "  {$boo} {$too} {call .bbb /}\n"
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
            + "  {@inject foo: ?}\n"
            + "  {@inject boo: ?}\n"
            + "  {$foo} {$boo} {call .bbb /} {call .ccc /}\n"
            + "{/template}\n"
            + "\n"
            + "{template .bbb}\n"
            + "  {@inject goo: ?}\n"
            + "  {@inject boo: ?}\n"
            + "  {$goo} {$boo} {call .aaa /}\n"
            + "{/template}\n"
            + "\n"
            + "{template .ccc}\n"
            + "  {@inject moo: ?}\n"
            + "  {@inject boo: ?}\n"
            + "  {$moo} {$boo} {call .bbb /}\n"
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
              new NoOpScopedData(), result.fileSet(), /*pluginInstances=*/ ImmutableMap.of());
      compiledTemplates =
          BytecodeCompiler.compile(
                  result.registry(),
                  result.fileSet(),
                  ErrorReporter.exploding(),
                  parser.soyFileSuppliers(),
                  parser.typeRegistry())
              .get();
    }

    ImmutableSet<String> calculateIjs(String templateName) {
      ImmutableSortedSet<String> tofuIjs = tofu.getUsedIjParamsForTemplate(templateName);
      ImmutableSet<String> sauceIjs =
          compiledTemplates.getTransitiveIjParamsForTemplate(templateName);
      assertWithMessage("sauce ijs").that(sauceIjs).isEqualTo(tofuIjs);
      return sauceIjs;
    }
  }
}
