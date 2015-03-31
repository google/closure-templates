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
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.shared.SoyFileSetParserBuilder;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.SoyFileSetNode;

import junit.framework.TestCase;

import java.util.Map;
import java.util.Set;

/**
 */
public final class CheckFunctionCallsVisitorTest extends TestCase {


  public void testPureFunctionOk() throws Exception {
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


  public void testIncorrectArity() throws Exception {
    assertFunctionCallsInvalid(
        "In file no-path:7:3, template ns.foo:" +
            " Function 'min' called with the wrong number of arguments" +
            " (function call \"min($x)\").",
        Joiner.on('\n').join(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
            "/**",
            " * @param x",
            " */",
            "{template .foo}",
            "  {print min($x)}",
            "{/template}"));
    assertFunctionCallsInvalid(
        "In file no-path:6:3, template ns.foo:" +
            " Function 'index' called with the wrong number of arguments" +
            " (function call \"index()\").",
        Joiner.on('\n').join(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
            "/**",
            " */",
            "{template .foo}",
            "  {print index()}",
            "{/template}"));
  }


  public void testNestedFunctionCall() throws Exception {
    assertFunctionCallsInvalid(
        "In file no-path:8:3, template ns.foo:" +
            " Function 'min' called with the wrong number of arguments (function call" +
            " \"min($x)\").",
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


  public void testNotALoopVariable1() throws Exception {
    assertFunctionCallsInvalid(
        "In file no-path:7:3, template ns.foo:" +
            " Function 'index' must have a foreach loop variable as its argument" +
            " (encountered \"index($x)\").",
        Joiner.on('\n').join(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
            "/**",
            " * @param x",
            " */",
            "{template .foo}",
            "  {print index($x)}",
            "{/template}"));
  }


  public void testNotALoopVariable2() throws Exception {
    assertFunctionCallsInvalid(
        "In file no-path:7:3, template ns.foo:" +
            " Function 'index' must have a foreach loop variable as its argument" +
            " (encountered \"index($x.y)\").",
        Joiner.on('\n').join(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
            "/**",
            " * @param x",
            " */",
            "{template .foo}",
            "  {print index($x.y)}",
            "{/template}"));
  }


  public void testNotALoopVariable3() throws Exception {
    assertFunctionCallsInvalid(
        "In file no-path:6:3, template ns.foo:" +
            " Function 'index' must have a foreach loop variable as its argument" +
            " (encountered \"index($ij.data)\").",
        Joiner.on('\n').join(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
            "/**",
            " */",
            "{template .foo}",
            "  {print index($ij.data)}",
            "{/template}"));
  }



  public void testNotAVariable() throws Exception {
    assertFunctionCallsInvalid(
        "In file no-path:7:3, template ns.foo:" +
            " Function 'index' must have a foreach loop variable as its argument" +
            " (encountered \"index($x + 1)\").",
        Joiner.on('\n').join(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
            "/**",
            " * @param x",
            " */",
            "{template .foo}",
            "  {print index($x + 1)}",
            "{/template}"));
  }


  public void testLoopVariableOk() throws Exception {
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


  public void testLoopVariableNotInScopeWhenEmpty() throws Exception {
    assertFunctionCallsInvalid(
        "In file no-path:10:5, template ns.foo:" +
            " Function 'index' must have a foreach loop variable as its argument" +
            " (encountered \"index($z)\").",
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


  public void testQuoteKeysIfJsFunction() throws Exception {
    applyCheckFunctionCallsVisitor(
        Joiner.on('\n').join(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
            "/***/",
            "{template .foo}",
            "  {let $m: quoteKeysIfJs(['a': 1, 'b': 'blah']) /}",
            "{/template}"));

    assertFunctionCallsInvalid(
        "In file no-path:5:3, template ns.foo:" +
            " Function quoteKeysIfJs() must have a map literal as its arg" +
            " (encountered \"quoteKeysIfJs('blah')\").",
        Joiner.on('\n').join(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
            "/***/",
            "{template .foo}",
            "  {let $m: quoteKeysIfJs('blah') /}",
            "{/template}"));
  }


  public void testUnrecognizedFunction() throws Exception {
    assertFunctionCallsInvalid(
        "In file no-path:6:3, template ns.foo:" +
            " Unrecognized function 'bogus' (encountered function call \"bogus()\").",
        Joiner.on('\n').join(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
            "/**",
            " */",
            "{template .foo}",
            "  {print bogus()}",
            "{/template}"));
  }


  public void testUnrecognizedFunctionOkInV1() throws Exception {
    applyCheckFunctionCallsVisitor(
        Joiner.on('\n').join(
            "{namespace ns autoescape=\"deprecated-noncontextual\"}\n",
            "{template .foo}",
            "  {print bogus()}",
            "{/template}"),
        SyntaxVersion.V1_0);
  }


  private void applyCheckFunctionCallsVisitor(String soyContent) throws Exception {
    applyCheckFunctionCallsVisitor(soyContent, SyntaxVersion.V2_0);
  }


  private void applyCheckFunctionCallsVisitor(
      String soyContent, SyntaxVersion declaredSyntaxVersion)
      throws Exception {

    SoyFileSetNode fileSet = SoyFileSetParserBuilder.forFileContents(soyContent)
        .parse()
        .getParseTree();
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
    CheckFunctionCallsVisitor visitor =
        new CheckFunctionCallsVisitor(soyFunctions, declaredSyntaxVersion);
    visitor.exec(fileSet);
  }


  private void assertFunctionCallsInvalid(String errorMessage, String soyContent) throws Exception {
    try {
      applyCheckFunctionCallsVisitor(soyContent);
      fail("Spurious success.");
    } catch (SoySyntaxException ex) {
      assertThat(ex).hasMessage(errorMessage);
    }
  }

}
