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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.FormattingErrorReporter;

import junit.framework.TestCase;

/**
 * Unit tests for CheckCallsVisitor.
 *
 */
public final class CheckCallsVisitorTest extends TestCase {

  public void testMissingParam() {

    assertInvalidSoyFiles(
        "Call missing required params [goo, moo].",
        "" +
            "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n" +
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
        "Call missing required param 'moo'.",
        "" +
            "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  {call ns2.foo_}\n" +
            "    {param goo: 26 /}\n" +
            "  {/call}\n" +
            "{/template}\n",
        "" +
            "{namespace ns2 autoescape=\"deprecated-noncontextual\"}\n" +
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
        "Call missing required param 'moo'.",
        "" +
            "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n" +
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
        "Call missing required params [goo, moo].",
        "" +
            "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  {delcall fooFoo /}\n" +
            "{/template}\n",
        "" +
            "{delpackage secretFeature}\n" +
            "{namespace ns2 autoescape=\"deprecated-noncontextual\"}\n" +
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
            "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n" +
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
            "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  {call ns2.foo}\n" +
            "    {param goo: 26 /}\n" +
            "  {/call}\n" +
            "{/template}\n",
        "" +
            "{namespace ns2 autoescape=\"deprecated-noncontextual\"}\n" +
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
    // Throws IllegalStateException on parse error.
    SoyFileSetParserBuilder.forFileContents(soyFileContents).parse();
  }


  private void assertInvalidSoyFiles(String expectedErrorMsgSubstr, String... soyFileContents) {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    SoyFileSetParserBuilder.forFileContents(soyFileContents).errorReporter(errorReporter).parse();
    ImmutableList<String> errorMessages = errorReporter.getErrorMessages();
    assertThat(errorMessages).hasSize(1);
    assertThat(Iterables.getFirst(errorMessages, null)).contains(expectedErrorMsgSubstr);
  }
}
