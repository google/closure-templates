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

package com.google.template.soy.parsepasses;

import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.shared.internal.SharedTestUtils;
import com.google.template.soy.sharedpasses.CheckSoyDocVisitor;
import com.google.template.soy.soytree.SoyFileSetNode;

import junit.framework.TestCase;


/**
 * Unit tests for CheckCallsVisitor.
 *
 * @author Kai Huang
 */
public class CheckCallsVisitorTest extends TestCase {


  public void testMissingParam() {

    assertInvalidSoyFiles(
        "template ns1.boo: Call to 'ns1.foo' is missing required params [goo, moo].",
        "" +
            "{namespace ns1}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  {call .foo /}\n" +
            "{/template}\n" +
            "\n" +
            "/**\n" +
            " * @param goo\n" +
            " * @param moo" +
            " */\n" +
            "{template .foo}\n" +
            "  {$goo}{$moo}\n" +
            "{/template}\n");

    assertInvalidSoyFiles(
        "template ns1.boo: Call to 'ns2.foo_' is missing required param 'moo'.",
        "" +
            "{namespace ns1}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  {call ns2.foo_}\n" +
            "    {param goo: 26 /}\n" +
            "  {/call}\n" +
            "{/template}\n",
        "" +
            "{namespace ns2}\n" +
            "\n" +
            "/**\n" +
            " * @param goo\n" +
            " * @param moo" +
            " */\n" +
            "{template .foo_ private=\"true\"}\n" +
            "  {$goo}{$moo}\n" +
            "{/template}\n");
  }


  public void testMissingParamInDelcall() {

    assertInvalidSoyFiles(
        "template ns1.boo: Call to 'fooFoo' is missing required param 'moo'.",
        "" +
            "{namespace ns1}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  {delcall fooFoo}\n" +
            "    {param goo: 26 /}\n" +
            "  {/delcall}\n" +
            "{/template}\n" +
            "\n" +
            "/**\n" +
            " * @param goo\n" +
            " * @param moo" +
            " */\n" +
            "{deltemplate fooFoo}\n" +
            "  {$goo}{$moo}\n" +
            "{/deltemplate}\n");

    assertInvalidSoyFiles(
        "template ns1.boo: Call to 'fooFoo' is missing required params [goo, moo].",
        "" +
            "{namespace ns1}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  {delcall fooFoo /}\n" +
            "{/template}\n",
        "" +
            "{delpackage secretFeature}\n" +
            "{namespace ns2}\n" +
            "\n" +
            "/**\n" +
            " * @param goo\n" +
            " * @param moo" +
            " */\n" +
            "{deltemplate fooFoo}\n" +
            "  {$goo}{$moo}\n" +
            "{/deltemplate}\n");
  }


  public void testNoMissingParamErrorForOptionalParams() {

    assertValidSoyFiles(
        "" +
            "{namespace ns1}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  {call .foo /}\n" +
            "{/template}\n" +
            "\n" +
            "/**\n" +
            " * @param? goo\n" +
            " * @param? moo" +
            " */\n" +
            "{template .foo}\n" +
            "  {$goo ?: 26}{$moo ?: 'blah'}\n" +
            "{/template}\n");

    assertValidSoyFiles(
        "" +
            "{namespace ns1}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  {call ns2.foo}\n" +
            "    {param goo: 26 /}\n" +
            "  {/call}\n" +
            "{/template}\n",
        "" +
            "{namespace ns2}\n" +
            "\n" +
            "/**\n" +
            " * @param goo\n" +
            " * @param? moo" +
            " */\n" +
            "{template .foo}\n" +
            "  {$goo}{$moo ?: 'blah'}\n" +
            "{/template}\n");
  }


  private void assertValidSoyFiles(String... soyFileContents) {
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(soyFileContents);
    (new CheckSoyDocVisitor(false)).exec(soyTree);
    (new CheckCallsVisitor()).exec(soyTree);
  }


  private void assertInvalidSoyFiles(String expectedErrorMsgSubstr, String... soyFileContents) {

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(soyFileContents);
    (new CheckSoyDocVisitor(false)).exec(soyTree);
    try {
      (new CheckCallsVisitor()).exec(soyTree);
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage().contains(expectedErrorMsgSubstr));
      return;  // test passes
    }
    fail();
  }

}
