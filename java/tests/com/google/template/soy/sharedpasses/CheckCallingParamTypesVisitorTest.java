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

import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.shared.internal.SharedTestUtils;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;

import junit.framework.TestCase;


/**
 * Unit tests for CheckCallingParamTypesVisitor.
 *
 */
public class CheckCallingParamTypesVisitorTest extends TestCase {

  public void testArgumentTypeMismatch() {
    assertInvalidSoyFiles(
        "Argument type mismatch",
        "" +
            "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  {call .foo}\n" +
            "    {param p1 kind=\"html\"}value{/param}\n" +
            "  {/call}\n" +
            "{/template}\n" +
            "\n" +
            "/***/\n" +
            "{template .foo}\n" +
            "  {@param p1: int}\n" +
            "  {$p1}\n" +
            "{/template}\n");

    assertInvalidSoyFiles(
        "Argument type mismatch",
        "" +
            "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  {call ns2.foo}\n" +
            "    {param p1 kind=\"html\"}value{/param}\n" +
            "  {/call}\n" +
            "{/template}\n",
        "" +
            "{namespace ns2 autoescape=\"deprecated-noncontextual\"}\n" +
            "\n" +
            "/***/\n" +
            "{template .foo}\n" +
            "  {@param p1: int}\n" +
            "  {$p1}\n" +
            "{/template}\n");
  }

  public void testArgumentTypeMismatchInDelcall() {

    assertInvalidSoyFiles(
        "Argument type mismatch",
        "" +
            "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  {delcall fooFoo}\n" +
            "    {param p1 kind=\"html\"}value{/param}\n" +
            "  {/delcall}\n" +
            "{/template}\n" +
            "\n" +
            "/***/\n" +
            "{deltemplate fooFoo}\n" +
            "  {@param p1: int}\n" +
            "  {$p1}\n" +
            "{/deltemplate}\n");

    assertInvalidSoyFiles(
        "Argument type mismatch",
        "" +
            "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  {delcall fooFoo}\n" +
            "    {param p1 kind=\"html\"}value{/param}\n" +
            "  {/delcall}\n" +
            "{/template}\n",
        "" +
            "{delpackage secretFeature}\n" +
            "{namespace ns2 autoescape=\"deprecated-noncontextual\"}\n" +
            "\n" +
            "/***/\n" +
            "{deltemplate fooFoo}\n" +
            "  {@param p1: int}\n" +
            "  {$p1}\n" +
            "{/deltemplate}\n");
  }

  public void testNoArgumentTypeMismatch() {

    SoyFileSetNode tree = assertValidSoyFiles(
        "" +
            "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  {call .foo}\n" +
            "    {param p1 kind=\"html\"}value{/param}\n" +
            "  {/call}\n" +
            "{/template}\n" +
            "\n" +
            "/***/\n" +
            "{template .foo}\n" +
            "  {@param p1: html}\n" +
            "  {$p1}\n" +
            "{/template}\n");

    TemplateNode booTemplate = tree.getChild(0).getChild(0);
    TemplateNode fooTemplate = tree.getChild(0).getChild(1);
    CallBasicNode callFooTemplate = (CallBasicNode) booTemplate.getChild(0);
    // should be empty because all params were statically verified
    assertTrue(callFooTemplate.getParamsToRuntimeCheck(fooTemplate).isEmpty());
  }

  public void testNoArgumentTypeMismatch_indirectPass() {
    SoyFileSetNode tree = assertValidSoyFiles(
        "" +
            "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  {@param p2: html}\n" +
            "  {call .foo data=\"all\"}\n" +
            "    {param p1 kind=\"html\"}value{/param}\n" +
            "  {/call}\n" +
            "  {$p2}\n" +
            "{/template}\n" +
            "\n" +
            "/***/\n" +
            "{template .foo}\n" +
            "  {@param p1: html}\n" +
            "  {@param p2: html}\n" +
            "  {$p1}{$p2}\n" +
            "{/template}\n");

    TemplateNode booTemplate = tree.getChild(0).getChild(0);
    TemplateNode fooTemplate = tree.getChild(0).getChild(1);
    CallBasicNode callFooTemplate = (CallBasicNode) booTemplate.getChild(0);
    // should be empty because all params were statically verified
    assertTrue(callFooTemplate.getParamsToRuntimeCheck(fooTemplate).isEmpty());
  }

  public void testNoArgumentTypeMismatch_multipleFiles() {
    SoyFileSetNode tree = assertValidSoyFiles(
        "" +
            "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  {call ns2.foo}\n" +
            "    {param p1 kind=\"html\"}value{/param}\n" +
            "  {/call}\n" +
            "{/template}\n",
        "" +
            "{namespace ns2 autoescape=\"deprecated-noncontextual\"}\n" +
            "\n" +
            "/***/\n" +
            "{template .foo}\n" +
            "  {@param p1: html}\n" +
            "  {$p1}\n" +
            "{/template}\n");
    TemplateNode booTemplate = tree.getChild(0).getChild(0);
    TemplateNode fooTemplate = tree.getChild(1).getChild(0);
    CallBasicNode callFooTemplate = (CallBasicNode) booTemplate.getChild(0);
    // should be empty because all params were statically verified
    assertTrue(callFooTemplate.getParamsToRuntimeCheck(fooTemplate).isEmpty());
  }

  public void testIndirectParams() {
    assertInvalidSoyFiles(
        "Argument type mismatch",
        "" +
            "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n" +
            "\n" +
            "/***/\n" +
            "{template .t1}\n" +
            "  {call .t2}\n" +
            "    {param p1 kind=\"html\"}value{/param}\n" + // Error - html to int
            "  {/call}\n" +
            "{/template}\n" +
            "\n" +
            "/***/\n" +
            "{template .t2}\n" +
            "  {call .t3 data=\"all\" /}\n" +
            "{/template}\n" +
            "\n" +
            "/***/\n" +
            "{template .t3}\n" +
            "  {@param p1: int}\n" +
            "  {$p1}\n" +
            "{/template}\n");

    assertValidSoyFiles(
        "" +
            "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n" +
            "\n" +
            "/***/\n" +
            "{template .t1}\n" +
            "  {call .t2}\n" +
            "    {param p1 kind=\"html\"}value{/param}\n" + // OK - html to string
            "  {/call}\n" +
            "{/template}\n" +
            "\n" +
            "/***/\n" +
            "{template .t2}\n" +
            "  {call .t3 data=\"all\" /}\n" +
            "{/template}\n" +
            "\n" +
            "/***/\n" +
            "{template .t3}\n" +
            "  {@param p1: string}\n" +
            "  {$p1}\n" +
            "{/template}\n");
  }

  public void testNullableIndirectParams() {
    assertValidSoyFiles(
        "" +
            "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n" +
            "\n" +
            "/***/\n" +
            "{template .t1}\n" +
            "  {@param p1: string|null}\n" +
            "  {call .t2 data=\"all\" /}\n" +
            "{/template}\n" +
            "\n" +
            "/***/\n" +
            "{template .t2}\n" +
            "  {call .t3 data=\"all\" /}\n" +
            "{/template}\n" +
            "\n" +
            "/***/\n" +
            "{template .t3}\n" +
            "  {@param p1: string}\n" +
            "  {$p1}\n" +
            "{/template}\n");
  }

  public void testOverriddenTextTypeParam() {
    assertValidSoyFiles(
        "" +
            "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n" +
            "\n" +
            "/***/\n" +
            "{template .t1}\n" +
            "  {@param? p1: string}\n" +
            "  {call .t2 data=\"all\"}\n" +
            "    {param p1 kind=\"text\"}{if $p1}foo{/if}{/param}\n" +
            "  {/call}\n" +
            "{/template}\n" +
            "\n" +
            "/***/\n" +
            "{template .t2}\n" +
            "  {@param p1: string}\n" +
            "  {$p1}\n" +
            "{/template}\n");
  }

  private SoyFileSetNode assertValidSoyFiles(String... soyFileContents) {
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(soyFileContents);
    (new CheckSoyDocVisitor(SyntaxVersion.V2_0)).exec(soyTree);
    (new CheckCallingParamTypesVisitor()).exec(soyTree);
    return soyTree;
  }

  private void assertInvalidSoyFiles(String expectedErrorMsgSubstr, String... soyFileContents) {
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(soyFileContents);
    (new CheckSoyDocVisitor(SyntaxVersion.V2_0)).exec(soyTree);
    try {
      (new CheckCallingParamTypesVisitor()).exec(soyTree);
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage().contains(expectedErrorMsgSubstr));
      return;  // test passes
    }
    fail("Exception expected");
  }
}
