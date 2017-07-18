/*
 * Copyright 2011 Google Inc.
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

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.error.ErrorReporter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author Mike Samuel */
@RunWith(JUnit4.class)
public final class CheckFunctionCallsVisitorTest {

  @Test
  public void testPureFunctionOk() {
    assertSuccess(
        "{namespace ns}\n",
        "/**",
        " * @param x",
        " * @param y",
        " */",
        "{template .foo}",
        "  {print min($x, $y)}",
        "{/template}");
  }

  @Test
  public void testIncorrectArity() {
    assertFunctionCallsInvalid(
        "Function 'min' called with 1 arguments (expected 2).",
        "{namespace ns}\n",
        "/**",
        " * @param x",
        " */",
        "{template .foo}",
        "  {print min($x)}",
        "{/template}");
    assertFunctionCallsInvalid(
        "Function 'index' called with 0 arguments (expected 1).",
        "{namespace ns}\n",
        "{template .foo}",
        "  {print index()}",
        "{/template}");
  }

  @Test
  public void testNestedFunctionCall() {
    assertFunctionCallsInvalid(
        "Function 'min' called with 1 arguments (expected 2).",
        "{namespace ns}\n",
        "/**",
        " * @param x",
        " * @param y",
        " */",
        "{template .foo}",
        "  {print min(min($x), min($x, $y))}",
        "{/template}");
  }

  @Test
  public void testNotALoopVariable1() {
    assertFunctionCallsInvalid(
        "Function 'index' must have a foreach loop variable as its argument",
        "{namespace ns}\n",
        "/**",
        " * @param x",
        " */",
        "{template .foo}",
        "  {print index($x)}",
        "{/template}");
  }

  @Test
  public void testNotALoopVariable2() {
    assertFunctionCallsInvalid(
        "Function 'index' must have a foreach loop variable as its argument",
        "{namespace ns}\n",
        "/**",
        " * @param x",
        " */",
        "{template .foo}",
        "  {print index($x.y)}",
        "{/template}");
  }

  @Test
  public void testNotALoopVariable3() {
    assertFunctionCallsInvalid(
        "Function 'index' must have a foreach loop variable as its argument",
        "{namespace ns}\n",
        "{template .foo}",
        "  {print index($ij.data)}",
        "{/template}");
  }

  @Test
  public void testNotALoopVariable4() {
    assertFunctionCallsInvalid(
        "Function 'index' must have a foreach loop variable as its argument",
        "{namespace ns}\n",
        "/**",
        " * @param x",
        " */",
        "{template .foo}",
        "  {print index($x + 1)}",
        "{/template}");
  }

  @Test
  public void testLoopVariableOk() {
    assertSuccess(
        "{namespace ns}\n",
        "/**",
        " * @param elements",
        " */",
        "{template .foo}",
        "  {foreach $z in $elements}",
        "    {if isLast($z)}Lorem Ipsum{/if}",
        "  {/foreach}",
        "{/template}");
  }

  @Test
  public void testLoopVariableNotInScopeWhenEmpty() {
    assertFunctionCallsInvalid(
        "Function 'index' must have a foreach loop variable as its argument",
        "{namespace ns}\n",
        "/**",
        " * @param elements",
        " */",
        "{template .foo}",
        "  {foreach $z in $elements}",
        "    Lorem Ipsum...",
        "  {ifempty}",
        "    {print index($elements)}", // Loop variable not in scope when empty.
        "  {/foreach}",
        "{/template}");
  }

  @Test
  public void testQuoteKeysIfJsFunction() {
    assertSuccess(
        "{namespace ns}\n",
        "{template .foo}",
        "  {let $m: quoteKeysIfJs(['a': 1, 'b': 'blah']) /}",
        "{/template}");

    assertFunctionCallsInvalid(
        "Function 'quoteKeysIfJs' called with incorrect arg type string (expected map literal).",
        "{namespace ns}\n",
        "{template .foo}",
        "  {let $m: quoteKeysIfJs('blah') /}",
        "{/template}");
  }

  @Test
  public void testCssFunction() {
    assertSuccess(
        "{namespace ns}\n",
        "{template .foo}",
        "  {@param x : ?}",
        "  {css('foo')}",
        "  {css($x, 'foo')}",
        "  {css('foo', 'bar')}",
        "{/template}");

    assertFunctionCallsInvalid(
        "Argument to function 'css' must be a string literal.",
        "{namespace ns}\n",
        "{template .foo}",
        "  {@param x : ?}",
        "  {css($x)}",
        "{/template}");

    assertFunctionCallsInvalid(
        "Argument to function 'css' must be a string literal.",
        "{namespace ns}\n",
        "{template .foo}",
        "  {@param x : ?}",
        "  {css($x, $x)}",
        "{/template}");

    assertFunctionCallsInvalid(
        "Argument to function 'css' must be a string literal.",
        "{namespace ns}\n",
        "{template .foo}",
        "  {@param x : ?}",
        "  {css($x, 10)}",
        "{/template}");
  }

  @Test
  public void testXidFunction() {
    assertSuccess("{namespace ns}\n", "{template .foo}", "  {xid('foo')}", "{/template}");

    assertFunctionCallsInvalid(
        "Argument to function 'xid' must be a string literal.",
        "{namespace ns}\n",
        "{template .foo}",
        "  {xid(10)}",
        "{/template}");

    assertFunctionCallsInvalid(
        "Argument to function 'xid' must be a string literal.",
        "{namespace ns}\n",
        "{template .foo}",
        "  {@param x : string}",
        "  {xid($x)}",
        "{/template}");
  }

  @Test
  public void testV1ExpressionFunction() {
    assertPasses(
        SyntaxVersion.V1_0,
        "{namespace ns}\n",
        "{template .foo deprecatedV1=\"true\"}",
        "  {let $m: v1Expression('blah.length') /}",
        "{/template}");

    assertFunctionCallsInvalid(
        SyntaxVersion.V1_0,
        "Incorrect syntax for version 1.0: The v1Expression function can only be used in templates "
            + "marked with the deprecatedV1=\"true\" attribute.",
        "{namespace ns}\n",
        "{template .foo}",
        "  {let $blah: 'foo' /}",
        "  {let $m: v1Expression('$blah') /}",
        "{/template}");

    assertFunctionCallsInvalid(
        SyntaxVersion.V1_0,
        "Function 'v1Expression' called with incorrect arg type string (expected string literal).",
        "{namespace ns}\n",
        "{template .foo deprecatedV1=\"true\"}",
        "  {let $blah: 'foo' /}",
        "  {let $m: v1Expression($blah) /}",
        "{/template}");
  }

  @Test
  public void testUnrecognizedFunction() {
    assertFunctionCallsInvalid(
        "Unknown function 'bogus'.",
        "{namespace ns}\n",
        "{template .foo}",
        "  {print bogus()}",
        "{/template}");
  }

  @Test
  public void testUnrecognizedFunctionOkInV1() {
    assertPasses(
        SyntaxVersion.V1_0,
        "{namespace ns}\n",
        "{template .foo}",
        "  {print bogus()}",
        "{/template}");
  }

  private void assertSuccess(String... lines) {
    assertPasses(SyntaxVersion.V2_0, lines);
  }

  private void assertPasses(SyntaxVersion declaredSyntaxVersion, String... lines) {
    SoyFileSetParserBuilder.forFileContents(Joiner.on('\n').join(lines))
        .declaredSyntaxVersion(declaredSyntaxVersion)
        .parse()
        .fileSet();
  }

  private void assertFunctionCallsInvalid(String errorMessage, String... lines) {
    assertFunctionCallsInvalid(SyntaxVersion.V2_0, errorMessage, lines);
  }

  private void assertFunctionCallsInvalid(
      SyntaxVersion declaredSyntaxVersion, String errorMessage, String... lines) {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(Joiner.on('\n').join(lines))
        .declaredSyntaxVersion(declaredSyntaxVersion)
        .errorReporter(errorReporter)
        .parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .contains(errorMessage);
  }
}
