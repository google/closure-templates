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
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CheckFunctionCallsVisitorTest {

  @Test
  public void testNotALoopVariable1() {
    assertFunctionCallsInvalid(
        "Function 'index' must have a loop variable as its argument",
        "{namespace ns}\n",
        "{template .foo}",
        "  {@param x: ?}",
        "  {print index($x)}",
        "{/template}");
  }

  @Test
  public void testNotALoopVariable2() {
    assertFunctionCallsInvalid(
        "Function 'index' must have a loop variable as its argument",
        "{namespace ns}\n",
        "{template .foo}",
        "  {@param x: ?}",
        "  {print index($x.y)}",
        "{/template}");
  }

  @Test
  public void testNotALoopVariable3() {
    assertFunctionCallsInvalid(
        "Function 'index' must have a loop variable as its argument",
        "{namespace ns}\n",
        "{template .foo}",
        "  {@param x: ?}",
        "  {print index($x + 1)}",
        "{/template}");
  }

  @Test
  public void testLoopVariableOk() {
    assertSuccess(
        "{namespace ns}\n",
        "{template .foo}",
        "  {@param elements: ?}",
        "  {for $z in $elements}",
        "    {if isLast($z)}Lorem Ipsum{/if}",
        "  {/for}",
        "{/template}");
  }

  @Test
  public void testLoopVariableNotInScopeWhenEmpty() {
    assertFunctionCallsInvalid(
        "Function 'index' must have a loop variable as its argument",
        "{namespace ns}\n",
        "{template .foo}",
        "  {@param elements: ?}",
        "  {for $z in $elements}",
        "    Lorem Ipsum...",
        "  {ifempty}",
        "    {print index($elements)}", // Loop variable not in scope when empty.
        "  {/for}",
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

  private void assertSuccess(String... lines) {
    SoyFileSetParserBuilder.forFileContents(Joiner.on('\n').join(lines)).parse().fileSet();
  }

  private void assertFunctionCallsInvalid(String errorMessage, String... lines) {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(Joiner.on('\n').join(lines))
        .errorReporter(errorReporter)
        .parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .contains(errorMessage);
  }
}
