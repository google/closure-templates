/*
 * Copyright 2008 Google Inc.
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
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link CheckTemplateHeaderVarsPass}.
 *
 */
@RunWith(JUnit4.class)
public final class CheckTemplateHeaderVarsPassTest {

  @Test
  public void testMatchingSimple() {
    // ------ No params ------
    String params = "";
    String templateBody = "Hello world!";
    assertThat(paramsErrorsForTemplate(params, templateBody)).isEmpty();

    // ------ Only 'print' statements ------
    params = "{@param boo: ?}{@param foo: ?}{@param? goo: ?}{@param moo: ?}";
    templateBody = "{$boo}{$foo.goo}{2 * $goo[round($moo)]}";
    assertThat(paramsErrorsForTemplate(params, templateBody)).isEmpty();

    // ------ Simple 'if' statement with nested 'print' statement ------
    params =
        "{@param boo: ?}  /** Something scary. */\n"
            + "{@param? goo: ?}  /** Something slimy. */\n";
    templateBody = "{if $boo.foo}\n" + "  Slimy {$goo}.\n" + "{/if}\n";
    assertThat(paramsErrorsForTemplate(params, templateBody)).isEmpty();
  }

  @Test
  public void testMatchingWithAdvancedStmts() {
    // ------ 'if', 'elseif', 'else', '/if' ------
    String params = "{@param boo: ?}{@param foo: ?}";
    String templateBody =
        "{if $boo}\n"
            + "  Scary.\n"
            + "{elseif $foo.goo}\n"
            + "  Slimy.\n"
            + "{else}\n"
            + "  {$foo.moo}\n"
            + "{/if}\n";
    assertThat(paramsErrorsForTemplate(params, templateBody)).isEmpty();

    // ------ 'switch', 'case', 'default', '/switch' ------
    params = "{@param boo: ?}{@param foo: ?}{@param moo: ?}{@param too: ?}{@param zoo: ?}";
    templateBody =
        "{switch $boo}\n"
            + "{case $foo.goo}\n"
            + "  Slimy.\n"
            + "{case 0, $moo, $too}\n"
            + "  Soggy.\n"
            + "{default}\n"
            + "  Not {$zoo}.\n"
            + "{/switch}\n";
    assertThat(paramsErrorsForTemplate(params, templateBody)).isEmpty();

    // ------ 'foreach', 'ifempty', '/foreach' ------
    params = "{@param moose: ?}{@param? meese: ?}";
    templateBody =
        "{for $moo in $moose}\n"
            + "  Cow says {$moo}.\n"
            + "{ifempty}\n"
            + "  No {$meese}.\n"
            + "{/for}\n";
    assertThat(paramsErrorsForTemplate(params, templateBody)).isEmpty();

    // ------ 'for', '/for' ------
    params = "{@param boo: ?}";
    templateBody = "{for $i in range(length($boo))}\n" + "  {$i + 1}: {$boo[$i]}.\n" + "{/for}\n";
    assertThat(paramsErrorsForTemplate(params, templateBody)).isEmpty();
  }

  @Test
  public void testCalls() {
    String fileContent1 =
        "{namespace boo}\n"
            + "\n"
            + "{template .foo}\n"
            + "  {@param? goo: ?}\n"
            + "  {@param too: ?}\n"
            + "  {@param woo: ?}\n"
            + "  {@param? zoo: ?}\n"
            + "  {@param gee: ?}\n" // no 'mee' is okay because user may not want to list it
            // (chrisn)
            + "  {@param maa: ?}\n" // no 'gaa' is okay because it may be optional in 'baa.faa'
            + "  {@param transParam: ?}\n" // okay (not required) because it's used in transitive
            // callee
            + "  {call .fee data=\"$goo.moo\" /}\n"
            + "  {call .fee data=\"$too\"}\n"
            + "    {param gee : $woo.hoo /}\n"
            + "    {param mee kind=\"text\"}\n"
            + "      {$zoo}\n"
            + "    {/param}\n"
            + "  {/call}\n"
            + "  {call .fee data=\"all\" /}\n"
            + "  {call baa.faa data=\"all\" /}\n"
            + "  {call .transitive1 data=\"all\" /}\n"
            + "{/template}\n"
            + "\n"
            + "{template .fee}\n"
            + "  {@param gee: ?}\n"
            + "  {@param mee: ?}\n"
            + "  {$gee}{$mee}\n"
            + "{/template}\n"
            + "\n"
            + "{template .transitive1}\n"
            + "  {call .transitive2 data=\"all\" /}\n"
            + "{/template}\n"
            + "\n"
            + "{template .transitive2}\n"
            + "  {@param transParam: ?}\n"
            + "  {$transParam}\n"
            + "{/template}\n";

    String fileContent2 =
        "{namespace baa}\n"
            + "\n"
            + "{template .faa}\n"
            + "  {@param gaa: ?}\n"
            + "  {@param maa: ?}\n"
            + "  {$gaa}{$maa}\n"
            + "{/template}\n";

    assertThat(soyDocErrorsFor(fileContent1, fileContent2)).isEmpty();
  }

  @Test
  public void testCallWithMissingParam() {
    String fileContent =
        "{namespace boo}\n"
            + "\n"
            + "{template .caller}\n"
            + "  {@param a: ?}\n"
            + "  {call .callee}\n"
            + "    {param x: $a /}\n"
            + "  {/call}\n"
            + "{/template}\n"
            + "\n"
            + "{template .callee}\n"
            + "  {@param x: ?}\n"
            + "  {@param y: ?}\n"
            + "  {$x}{$y}\n"
            + "{/template}\n";

    // This is actually not reported as an error by this visitor CheckTemplateParams doesn't check
    // calls
    assertThat(Iterables.getOnlyElement(soyDocErrorsFor(fileContent)).message())
        .isEqualTo("Call missing required param 'y'.");
  }

  @Test
  public void testUndeclaredParam() {
    String params = "{@param foo: ?}";
    String templateBody = "{$boo.foo}";
    ImmutableList<SoyError> errors = paramsErrorsForTemplate(params, templateBody);
    assertThat(errors).hasSize(2);
    assertThat(errors.get(0).message()).contains("Unknown data key 'boo'. Did you mean 'foo'?");
    assertThat(errors.get(1).message()).isEqualTo("'foo' unused in template body.");
  }

  @Test
  public void testUnusedParam() {
    String params = "{@param boo: ?}{@param? foo: ?}";
    String templateBody = "{$boo.foo}";
    ImmutableList<SoyError> errors = paramsErrorsForTemplate(params, templateBody);
    assertThat(Iterables.getOnlyElement(errors).message())
        .isEqualTo("'foo' unused in template body.");
  }

  @Test
  public void testUnusedParamInCallWithAllData() {
    String fileContent =
        "{namespace boo}\n"
            + "\n"
            + "{template .foo}\n"
            + "  {@param moo: ?}\n"
            + "  {@param zoo: ?}\n" // 'zoo' is not used, even in call to .goo
            + "  {call .goo data=\"all\" /}\n"
            + "{/template}\n"
            + "\n"
            + "{template .goo}\n"
            + "  {@param moo: ?}\n"
            + "  {$moo}\n"
            + "{/template}\n";

    ImmutableList<SoyError> errors = soyDocErrorsFor(fileContent);
    assertThat(Iterables.getOnlyElement(errors).message())
        .isEqualTo("'zoo' unused in template body.");
  }

  @Test
  public void testWithExternalCallWithAllData() {
    String fileContent =
        "{namespace boo}\n"
            + "\n"
            + "{template .foo}\n"
            + "  {@param zoo: ?}\n" // 'zoo' is okay because it may be used in 'goo.moo'
            + "  {call goo.moo data=\"all\" /}\n"
            + "{/template}\n";

    assertThat(soyDocErrorsFor(fileContent)).isEmpty();
  }

  @Test
  public void testUnusedParamWithRecursiveCall() {
    String params = "{@param boo: ?}{@param foo: ?}";
    String templateBody = "{call .foo data=\"all\" /}";
    ImmutableList<SoyError> errors = paramsErrorsForTemplate(params, templateBody);
    assertThat(errors).hasSize(2);
    assertThat(errors.get(0).message()).isEqualTo("'boo' unused in template body.");
    assertThat(errors.get(1).message()).isEqualTo("'foo' unused in template body.");
  }

  @Test
  public void testUnusedParamInDelegateTemplate() {
    String fileContent =
        "{namespace boo}\n"
            + "\n"
            + "{deltemplate MagicButton}\n"
            + "  {@param zoo: ?}\n" // 'zoo' may be needed in other implementations of the same
            // delegate
            + "  blah\n"
            + "{/deltemplate}\n";

    assertThat(soyDocErrorsFor(fileContent)).isEmpty();
  }

  @Test
  public void testPassingUnusedParamToUnknownTemplate() {
    String fileContent =
        "{namespace ns}\n"
            + "\n"
            + "{template .a}\n"
            // This is fine because we know nothing about ns2.b.
            + "  {call ns2.b}{param a: '' /}{/call}\n"
            + "{/template}\n";

    assertThat(soyDocErrorsFor(fileContent)).isEmpty();
  }

  @Test
  public void testDelegateCallVariant() {
    String fileContent =
        ""
            + "{namespace boo}\n"
            + "\n"
            + "{template .foo}\n"
            + "  {@param variant: ?}\n"
            + "  {delcall MagicButton variant=\"$variant\" /}\n"
            + "{/template}\n";

    assertThat(soyDocErrorsFor(fileContent)).isEmpty();
  }

  @Test
  public void testV1Expression() {
    String fileContent1 =
        "{namespace boo1}\n"
            + "\n"
            + "/** Template 1 */\n"
            + "{template .foo1}\n"
            + "  {v1Expression('$goo1.moo1()')}\n"
            + "{/template}\n";

    String fileContent2 =
        "{namespace boo2}\n"
            + "\n"
            + "/** Template 2 */\n"
            + "{template .foo2}\n"
            + "  {$goo2.moo2}\n"
            + "{/template}\n";

    ImmutableList<SoyError> errors = soyDocErrorsFor(fileContent1, fileContent2);
    assertThat(errors).hasSize(2);
    assertThat(errors.get(0).message()).isEqualTo("Unknown data key 'goo1'.");
    assertThat(errors.get(1).message()).isEqualTo("Unknown data key 'goo2'.");
  }

  @Test
  public void testUndeclaredStateVar() {
    String stateVarDecl = "{@state foo:= 1}";
    String templateBody = "<div>{$boo}</div>";
    ImmutableList<SoyError> errors = soyErrorsForElement(stateVarDecl, templateBody);
    assertThat(errors).hasSize(2);
    assertThat(errors.get(0).message()).contains("Unknown data key 'boo'. Did you mean 'foo'?");
    assertThat(errors.get(1).message()).isEqualTo("'foo' unused in template body.");
  }

  @Test
  public void testUnusedStateVar() {
    String stateVarDecl = "{@state foo:= 2}";
    String templateBody = "<b>Hello</b>";
    ImmutableList<SoyError> errors = soyErrorsForElement(stateVarDecl, templateBody);
    assertThat(Iterables.getOnlyElement(errors).message())
        .isEqualTo("'foo' unused in template body.");
  }

  private static ImmutableList<SoyError> paramsErrorsForTemplate(
      String params, String templateBody) {
    String testFileContent =
        "{namespace boo}\n"
            + "\n"
            + "{template .foo}\n"
            + params
            + templateBody
            + "\n"
            + "{/template}\n";

    return soyDocErrorsFor(testFileContent);
  }

  private static ImmutableList<SoyError> soyDocErrorsFor(String... soyFileContents) {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(soyFileContents)
        .allowV1Expression(true)
        .errorReporter(errorReporter)
        .parse();
    return errorReporter.getErrors();
  }

  private static ImmutableList<SoyError> soyErrorsForElement(
      String headerVarDecl, String templateBody) {
    String testFileContent =
        "{namespace boo}\n"
            + "\n"
            + "{element .foo}\n"
            + headerVarDecl
            + templateBody
            + "\n"
            + "{/element}\n";

    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(testFileContent)
        .errorReporter(errorReporter)
        .parse();
    return errorReporter.getErrors();
  }
}
