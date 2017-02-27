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
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.error.FormattingErrorReporter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link CheckTemplateParamsVisitor}.
 *
 */
@RunWith(JUnit4.class)
public final class CheckTemplateParamsVisitorTest {

  @Test
  public void testMatchingSimple() {
    // ------ No params ------
    String soyDoc = "";
    String templateBody = "Hello world!";
    assertThat(soyDocErrorsForTemplate(soyDoc, templateBody)).isEmpty();

    // ------ Only 'print' statements ------
    soyDoc = "@param boo @param foo @param? goo @param moo";
    templateBody = "{$boo}{$foo.goo |noAutoescape}{2 * $goo[round($moo)]}";
    assertThat(soyDocErrorsForTemplate(soyDoc, templateBody)).isEmpty();

    // ------ Simple 'if' statement with nested 'print' statement ------
    soyDoc = "@param boo Something scary.\n" + "@param? goo Something slimy.\n";
    templateBody = "{if $boo.foo}\n" + "  Slimy {$goo}.\n" + "{/if}\n";
    assertThat(soyDocErrorsForTemplate(soyDoc, templateBody)).isEmpty();
  }

  @Test
  public void testMatchingWithAdvancedStmts() {
    // ------ 'if', 'elseif', 'else', '/if' ------
    String soyDoc = "@param boo @param foo";
    String templateBody =
        "{if $boo}\n"
            + "  Scary.\n"
            + "{elseif $foo.goo}\n"
            + "  Slimy.\n"
            + "{else}\n"
            + "  {$foo.moo}\n"
            + "{/if}\n";
    assertThat(soyDocErrorsForTemplate(soyDoc, templateBody)).isEmpty();

    // ------ 'switch', 'case', 'default', '/switch' ------
    soyDoc = "@param boo @param foo @param moo @param too @param zoo";
    templateBody =
        "{switch $boo}\n"
            + "{case $foo.goo}\n"
            + "  Slimy.\n"
            + "{case 0, $moo, $too}\n"
            + "  Soggy.\n"
            + "{default}\n"
            + "  Not {$zoo}.\n"
            + "{/switch}\n";
    assertThat(soyDocErrorsForTemplate(soyDoc, templateBody)).isEmpty();

    // ------ 'foreach', 'ifempty', '/foreach' ------
    soyDoc = "@param moose @param? meese";
    templateBody =
        "{foreach $moo in $moose}\n"
            + "  Cow says {$moo}.\n"
            + "{ifempty}\n"
            + "  No {$meese}.\n"
            + "{/foreach}\n";
    assertThat(soyDocErrorsForTemplate(soyDoc, templateBody)).isEmpty();

    // ------ 'for', '/for' ------
    soyDoc = "@param boo";
    templateBody = "{for $i in range(length($boo))}\n" + "  {$i + 1}: {$boo[$i]}.\n" + "{/for}\n";
    assertThat(soyDocErrorsForTemplate(soyDoc, templateBody)).isEmpty();
  }

  @Test
  public void testCalls() {
    String fileContent1 =
        "{namespace boo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/**\n"
            + " * @param? goo @param too @param woo @param? zoo\n"
            + " * @param gee\n"
            + // no 'mee' is okay because user may not want to list it (chrisn)
            " * @param maa\n"
            + // no 'gaa' is okay because it may be optional in 'baa.faa'
            " * @param transParam \n"
            + // okay (not required) because it's used in transitive callee
            " */\n"
            + "{template .foo}\n"
            + "  {call .fee data=\"$goo.moo\" /}\n"
            + "  {call .fee data=\"$too\"}\n"
            + "    {param gee : $woo.hoo /}\n"
            + "    {param mee}\n"
            + "      {$zoo}\n"
            + "    {/param}\n"
            + "  {/call}\n"
            + "  {call .fee data=\"all\" /}\n"
            + "  {call baa.faa data=\"all\" /}\n"
            + "  {call .transitive1 data=\"all\" /}\n"
            + "{/template}\n"
            + "\n"
            + "/** @param gee @param mee */\n"
            + "{template .fee}\n"
            + "  {$gee}{$mee}\n"
            + "{/template}\n"
            + "\n"
            + "/** */\n"
            + "{template .transitive1}\n"
            + "  {call .transitive2 data=\"all\" /}\n"
            + "{/template}\n"
            + "\n"
            + "/** @param transParam */\n"
            + "{template .transitive2}\n"
            + "  {$transParam}\n"
            + "{/template}\n";

    String fileContent2 =
        "{namespace baa autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** @param gaa @param maa */\n"
            + "{template .faa}\n"
            + "  {$gaa}{$maa}\n"
            + "{/template}\n";

    assertThat(soyDocErrorsFor(fileContent1, fileContent2)).isEmpty();
  }

  @Test
  public void testCallWithMissingParam() {
    String fileContent =
        "{namespace boo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** @param a */\n"
            + "{template .caller}\n"
            + "  {call .callee}\n"
            + "    {param x: $a /}\n"
            + "  {/call}\n"
            + "{/template}\n"
            + "\n"
            + "/** @param x @param y */\n"
            + "{template .callee}\n"
            + "  {$x}{$y}\n"
            + "{/template}\n";

    // This is actually not reported as an error by this visitor CheckTemplateParams doesn't check
    // calls
    assertThat(soyDocErrorsFor(fileContent)).containsExactly("Call missing required param 'y'.");
  }

  @Test
  public void testUndeclaredParam() {
    String soyDoc = "@param foo";
    String templateBody = "{$boo.foo}";
    ImmutableList<String> errors = soyDocErrorsForTemplate(soyDoc, templateBody);
    assertThat(errors).hasSize(2);
    assertThat(errors.get(0)).contains("Unknown data key 'boo'. Did you mean 'foo'?");
    assertThat(errors.get(1)).isEqualTo("Param 'foo' unused in template body.");
  }

  @Test
  public void testUnusedParam() {
    String soyDoc = "@param boo @param? foo";
    String templateBody = "{$boo.foo}";
    ImmutableList<String> errors = soyDocErrorsForTemplate(soyDoc, templateBody);
    assertThat(errors).containsExactly("Param 'foo' unused in template body.");
  }

  @Test
  public void testUnusedParamInCallWithAllData() {
    String fileContent =
        "{namespace boo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/**\n"
            + " * @param moo\n"
            + " * @param zoo\n"
            + // 'zoo' is not used, even in call to .goo
            " */\n"
            + "{template .foo}\n"
            + "  {call .goo data=\"all\" /}\n"
            + "{/template}\n"
            + "\n"
            + "/** @param moo */\n"
            + "{template .goo}\n"
            + "  {$moo}\n"
            + "{/template}\n";

    ImmutableList<String> errors = soyDocErrorsFor(fileContent);
    assertThat(errors).containsExactly("Param 'zoo' unused in template body.");
  }

  @Test
  public void testWithExternalCallWithAllData() {
    String fileContent =
        "{namespace boo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/**\n"
            + " * @param zoo\n"
            + // 'zoo' is okay because it may be used in 'goo.moo'
            " */\n"
            + "{template .foo}\n"
            + "  {call goo.moo data=\"all\" /}\n"
            + "{/template}\n";

    assertThat(soyDocErrorsFor(fileContent)).isEmpty();
  }

  @Test
  public void testUnusedParamWithRecursiveCall() {
    String soyDoc = "@param boo @param foo";
    String templateBody = "{call .foo data=\"all\" /}";
    ImmutableList<String> errors = soyDocErrorsForTemplate(soyDoc, templateBody);
    assertThat(errors)
        .containsExactly(
            "Param 'boo' unused in template body.", "Param 'foo' unused in template body.")
        .inOrder();
  }

  @Test
  public void testUnusedParamInDelegateTemplate() {
    String fileContent =
        "{namespace boo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/**\n"
            + " * @param zoo\n"
            + // 'zoo' may be needed in other implementations of the same delegate
            " */\n"
            + "{deltemplate MagicButton}\n"
            + "  blah\n"
            + "{/deltemplate}\n";

    assertThat(soyDocErrorsFor(fileContent)).isEmpty();
  }

  @Test
  public void testDelegateCallVariant() {
    String fileContent =
        ""
            + "{namespace boo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/**\n"
            + " * @param variant\n"
            + " */\n"
            + "{template .foo}\n"
            + "  {delcall MagicButton variant=\"$variant\" /}\n"
            + "{/template}\n";

    assertThat(soyDocErrorsFor(fileContent)).isEmpty();
  }

  @Test
  public void testOnlyCheckFilesInV2() {
    String fileContent0 =
        "{namespace boo0 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + // template is tagged as v1
            "{template .foo0 deprecatedV1=\"true\"}\n"
            + "  {$goo0.moo0}\n"
            + "{/template}\n";

    String fileContent1 =
        "{namespace boo1 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** Template 1 */\n"
            + "{template .foo1 deprecatedV1=\"true\"}\n"
            + "  {$goo1}\n"
            + "  {v1Expression('$goo1.moo1()')}\n"
            + // file is not all V2 syntax due to this expression
            "{/template}\n";

    String fileContent2 =
        "{namespace boo2 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** Template 2 */\n"
            + "{template .foo2}\n"
            + "  {$goo2.moo2}\n"
            + "{/template}\n";

    ImmutableList<String> errors = soyDocErrorsFor(fileContent0, fileContent1, fileContent2);
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0)).contains("Unknown data key 'goo2'.");
  }

  @Test
  public void testWithHeaderParams() {
    String fileContent =
        "{namespace boo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** */\n"
            + "{template .foo}\n"
            + "  {@param goo: string}\n"
            + "  {@inject zoo: string}\n"
            + "  {$goo}\n"
            + "  {$zoo}\n"
            + "{/template}\n";

    assertThat(soyDocErrorsFor(fileContent)).isEmpty();

    fileContent =
        "{namespace boo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** */\n"
            + "{template .foo}\n"
            + "  {@param goo: string}\n"
            + "  {@inject zoo: string}\n"
            + "{/template}\n";

    ImmutableList<String> errors = soyDocErrorsFor(fileContent);
    assertThat(errors)
        .containsExactly(
            "Param 'goo' unused in template body.", "Param 'zoo' unused in template body.")
        .inOrder();
  }

  private static ImmutableList<String> soyDocErrorsForTemplate(String soyDoc, String templateBody) {
    String testFileContent =
        "{namespace boo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** "
            + soyDoc
            + " */\n"
            + "{template .foo}\n"
            + templateBody
            + "\n"
            + "{/template}\n";

    return soyDocErrorsFor(testFileContent);
  }

  private static ImmutableList<String> soyDocErrorsFor(String... soyFileContents) {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    SoyFileSetParserBuilder.forFileContents(soyFileContents)
        .declaredSyntaxVersion(SyntaxVersion.V1_0)
        .errorReporter(errorReporter)
        .parse();
    return errorReporter.getErrorMessages();
  }
}
