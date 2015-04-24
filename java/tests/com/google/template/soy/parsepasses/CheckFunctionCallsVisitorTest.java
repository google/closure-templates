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

package com.google.template.soy.parsepasses;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.template.soy.FormattingErrorReporter;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.SoyFileSetNode;

import junit.framework.TestCase;

import java.util.Map;
import java.util.Set;

/**
 */
public final class CheckFunctionCallsVisitorTest extends TestCase {

  public void testPureFunctionOk() {
    applyCheckFunctionCallsVisitor(Joiner.on('\n').join(
        "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
        "/**",
        " * @param x",
        " * @param y",
        " */",
        "{template .foo}",
        "  {print min($x, $y)}",
        "{/template}"));
  }

  public void testIncorrectArity() {
    assertFunctionCallsInvalid(
        "Function 'min' called with 1 arguments (expected 2).",
        Joiner.on('\n').join(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
            "/**",
            " * @param x",
            " */",
            "{template .foo}",
            "  {print min($x)}",
            "{/template}"));
    assertFunctionCallsInvalid(
        "Function 'index' called with 0 arguments (expected 1).",
        Joiner.on('\n').join(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
            "/**",
            " */",
            "{template .foo}",
            "  {print index()}",
            "{/template}"));
  }

  public void testNestedFunctionCall() {
    assertFunctionCallsInvalid(
        "Function 'min' called with 1 arguments (expected 2).",
        Joiner.on('\n').join(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
            "/**",
            " * @param x",
            " * @param y",
            " */",
            "{template .foo}",
            "  {print min(min($x), min($x, $y))}",
            "{/template}"));
  }

  public void testNotALoopVariable1() {
    assertFunctionCallsInvalid(
        "Function 'index' must have a foreach loop variable as its argument",
        Joiner.on('\n').join(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
            "/**",
            " * @param x",
            " */",
            "{template .foo}",
            "  {print index($x)}",
            "{/template}"));
  }

  public void testNotALoopVariable2() {
    assertFunctionCallsInvalid(
        "Function 'index' must have a foreach loop variable as its argument",
        Joiner.on('\n').join(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
            "/**",
            " * @param x",
            " */",
            "{template .foo}",
            "  {print index($x.y)}",
            "{/template}"));
  }

  public void testNotALoopVariable3() {
    assertFunctionCallsInvalid(
        "Function 'index' must have a foreach loop variable as its argument",
        Joiner.on('\n').join(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
            "/**",
            " */",
            "{template .foo}",
            "  {print index($ij.data)}",
            "{/template}"));
  }

  public void testNotALoopVariable4() {
    assertFunctionCallsInvalid(
        "Function 'index' must have a foreach loop variable as its argument",
        Joiner.on('\n').join(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
            "/**",
            " * @param x",
            " */",
            "{template .foo}",
            "  {print index($x + 1)}",
            "{/template}"));
  }

  public void testLoopVariableOk() {
    applyCheckFunctionCallsVisitor(Joiner.on('\n').join(
        "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
        "/**",
        " * @param elements",
        " */",
        "{template .foo}",
        "  {foreach $z in $elements}",
        "    {if isLast($z)}Lorem Ipsum{/if}",
        "  {/foreach}",
        "{/template}"));
  }

  public void testLoopVariableNotInScopeWhenEmpty() {
    assertFunctionCallsInvalid(
        "Function 'index' must have a foreach loop variable as its argument",
        Joiner.on('\n').join(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
            "/**",
            " * @param elements",
            " */",
            "{template .foo}",
            "  {foreach $z in $elements}",
            "    Lorem Ipsum...",
            "  {ifempty}",
            "    {print index($z)}",  // Loop variable not in scope when empty.
            "  {/foreach}",
            "{/template}"));
  }

  public void testQuoteKeysIfJsFunction() {
    applyCheckFunctionCallsVisitor(
        Joiner.on('\n').join(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
            "/***/",
            "{template .foo}",
            "  {let $m: quoteKeysIfJs(['a': 1, 'b': 'blah']) /}",
            "{/template}"));

    assertFunctionCallsInvalid(
        "Function 'quoteKeysIfJs' called with argument of type string (expected map literal).",
        Joiner.on('\n').join(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
            "/***/",
            "{template .foo}",
            "  {let $m: quoteKeysIfJs('blah') /}",
            "{/template}"));
  }

  public void testUnrecognizedFunction() {
    assertFunctionCallsInvalid(
        "Unknown function 'bogus'.",
        Joiner.on('\n').join(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
            "/**",
            " */",
            "{template .foo}",
            "  {print bogus()}",
            "{/template}"));
  }

  public void testUnrecognizedFunctionOkInV1() {
    applyCheckFunctionCallsVisitor(
        Joiner.on('\n').join(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
            "{template .foo}",
            "  {print bogus()}",
            "{/template}"),
        SyntaxVersion.V1_0);
  }

  private FormattingErrorReporter applyCheckFunctionCallsVisitor(String soyContent) {
    return applyCheckFunctionCallsVisitor(soyContent, SyntaxVersion.V2_0);
  }

  private FormattingErrorReporter applyCheckFunctionCallsVisitor(
      String soyContent, SyntaxVersion declaredSyntaxVersion) {
    SoyFileSetNode fileSet = SoyFileSetParserBuilder.forFileContents(soyContent).parse();
    Map<String, SoyFunction> soyFunctions = ImmutableMap.<String, SoyFunction>of(
        "min",
        new SoyFunction() {
          public @Override String getName() {
            return "min";
          }

          public @Override Set<Integer> getValidArgsSizes() {
            return ImmutableSet.of(2);
          }
        });
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    CheckFunctionCallsVisitor visitor =
        new CheckFunctionCallsVisitor(soyFunctions, declaredSyntaxVersion, errorReporter);
    visitor.exec(fileSet);
    return errorReporter;
  }

  private void assertFunctionCallsInvalid(String errorMessage, String soyContent) {
    FormattingErrorReporter errorReporter = applyCheckFunctionCallsVisitor(soyContent);
    assertThat(errorReporter.getErrorMessages()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrorMessages())).contains(errorMessage);
  }
}
