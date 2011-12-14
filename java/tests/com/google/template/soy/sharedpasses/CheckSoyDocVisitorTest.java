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

package com.google.template.soy.sharedpasses;

import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.shared.internal.SharedTestUtils;
import com.google.template.soy.soytree.SoyFileSetNode;

import junit.framework.TestCase;


/**
 * Unit tests for CheckSoyDocVisitor.
 *
 */
public class CheckSoyDocVisitorTest extends TestCase {


  public void testMatchingSimple() throws SoySyntaxException {

    // ------ No params ------
    String soyDoc = "";
    String templateBody = "Hello world!";
    runTemplateTestHelper(soyDoc, templateBody);  // should not throw exception

    // ------ Only 'print' statements ------
    soyDoc = "@param boo @param foo @param? goo @param moo";
    templateBody = "{$boo}{$foo.goo |noAutoescape}{2 * $goo[round($moo)]}";
    runTemplateTestHelper(soyDoc, templateBody);  // should not throw exception

    // ------ Simple 'if' statement with nested 'print' statement ------
    soyDoc =
        "@param boo Something scary.\n" +
        "@param? goo Something slimy.\n";
    templateBody =
        "{if $boo.foo}\n" +
        "  Slimy {$goo}.\n" +
        "{/if}\n";
    runTemplateTestHelper(soyDoc, templateBody);  // should not throw exception
  }


  public void testMatchingWithAdvancedStmts() throws SoySyntaxException {

    // ------ 'if', 'elseif', 'else', '/if' ------
    String soyDoc = "@param boo @param foo";
    String templateBody =
        "{if $boo}\n" +
        "  Scary.\n" +
        "{elseif $foo.goo}\n" +
        "  Slimy.\n" +
        "{else}\n" +
        "  {$foo.moo}\n" +
        "{/if}\n";
    runTemplateTestHelper(soyDoc, templateBody);  // should not throw exception

    // ------ 'switch', 'case', 'default', '/switch' ------
    soyDoc = "@param boo @param foo @param moo @param too @param zoo";
    templateBody =
        "{switch $boo}\n" +
        "{case $foo.goo}\n" +
        "  Slimy.\n" +
        "{case 0, $moo, $too}\n" +
        "  Soggy.\n" +
        "{default}\n" +
        "  Not {$zoo}.\n" +
        "{/switch}\n";
    runTemplateTestHelper(soyDoc, templateBody);  // should not throw exception

    // ------ 'foreach', 'ifempty', '/foreach' ------
    soyDoc = "@param moose @param? meese";
    templateBody =
        "{foreach $moo in $moose}\n" +
        "  Cow says {$moo}.\n" +
        "{ifempty}\n" +
        "  No {$meese}.\n" +
        "{/foreach}\n";
    runTemplateTestHelper(soyDoc, templateBody);  // should not throw exception

    // ------ 'for', '/for' ------
    soyDoc = "@param boo";
    templateBody =
        "{for $i in range(length($boo))}\n" +
        "  {$i + 1}: {$boo[$i]}.\n" +
        "{/for}\n";
    runTemplateTestHelper(soyDoc, templateBody);  // should not throw exception
  }


  public void testCalls() throws SoySyntaxException {

    String fileContent1 =
        "{namespace boo}\n" +
        "\n" +
        "/**\n" +
        " * @param? goo @param too @param woo @param? zoo\n" +
        " * @param gee\n" +  // no 'mee' is okay because user may not want to list it (chrisn)
        " * @param maa\n" +  // no 'gaa' is okay because it may be optional in 'baa.faa'
        " * @param transParam \n" +  // okay (not required) because it's used in transitive callee
        " */\n" +
        "{template name=\".foo\"}\n" +
        "  {call name=\".fee\" data=\"$goo.moo\" /}\n" +
        "  {call name=\".fee\" data=\"$too\"}\n" +
        "    {param key=\"gee\" value=\"$woo.hoo\" /}\n" +
        "    {param key=\"mee\"}\n" +
        "      {$zoo}\n" +
        "    {/param}\n" +
        "  {/call}\n" +
        "  {call name=\".fee\" data=\"all\" /}\n" +
        "  {call name=\"baa.faa\" data=\"all\" /}\n" +
        "  {call .transitive1 data=\"all\" /}\n" +
        "{/template}\n" +
        "\n" +
        "/** @param gee @param mee */\n" +
        "{template name=\".fee\"}\n" +
        "  {$gee}{$mee}\n" +
        "{/template}\n" +
        "\n" +
        "/** */\n" +
        "{template .transitive1}\n" +
        "  {call .transitive2 data=\"all\" /}\n" +
        "{/template}\n" +
        "\n" +
        "/** @param transParam */\n" +
        "{template .transitive2}\n" +
        "  {$transParam}\n" +
        "{/template}\n";

    String fileContent2 =
        "{namespace baa}\n" +
        "\n" +
        "/** @param gaa @param maa */\n" +
        "{template name=\".faa\"}\n" +
        "  {$gaa}{$maa}\n" +
        "{/template}\n";

    runSoyFilesTestHelper(fileContent1, fileContent2);
  }


  public void testCallWithMissingParam() throws SoySyntaxException {

    String fileContent =
        "{namespace boo}\n" +
        "\n" +
        "/** @param a */\n" +
        "{template .caller}\n" +
        "  {call .callee}\n" +
        "    {param x: $a /}\n" +
        "  {/call}\n" +
        "{/template}\n" +
        "\n" +
        "/** @param x @param y */\n" +
        "{template .callee}\n" +
        "  {$x}{$y}\n" +
        "{/template}\n";

    // This is actually not reported as an error right now because param 'y' in the callee may be
    // optional, even though the SoyDoc does not list the param as optional (many people don't use
    // the @param? tag at all).
    runSoyFilesTestHelper(fileContent);
  }


  public void testUndeclaredParam() throws SoySyntaxException {

    String soyDoc = "@param foo";
    String templateBody = "{$boo.foo}";
    try {
      runTemplateTestHelper(soyDoc, templateBody);
      fail();
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage().contains(
          "Found references to data keys that are not declared in SoyDoc: [boo]"));
    }
  }


  public void testUnusedParam() throws SoySyntaxException {

    String soyDoc = "@param boo @param? foo";
    String templateBody = "{$boo.foo}";
    try {
      runTemplateTestHelper(soyDoc, templateBody);
      fail();
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage().contains(
          "Found params declared in SoyDoc but not used in template: [foo]"));
    }
  }


  public void testUnusedParamInCallWithAllData() throws SoySyntaxException {

    String fileContent =
        "{namespace boo}\n" +
        "\n" +
        "/**\n" +
        " * @param moo\n" +
        " * @param zoo\n" +  // 'zoo' is not used, even in call to .goo
        " */\n" +
        "{template name=\".foo\"}\n" +
        "  {call .goo data=\"all\" /}\n" +
        "{/template}\n" +
        "\n" +
        "/** @param moo */\n" +
        "{template .goo}\n" +
        "  {$moo}\n" +
        "{/template}\n";

    try {
      runSoyFilesTestHelper(fileContent);
      fail();
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage().contains(
          "Found params declared in SoyDoc but not used in template: [zoo]"));
    }
  }


  public void testWithExternalCallWithAllData() throws SoySyntaxException {

    String fileContent =
        "{namespace boo}\n" +
        "\n" +
        "/**\n" +
        " * @param zoo\n" +  // 'zoo' is okay because it may be used in 'goo.moo'
        " */\n" +
        "{template .foo}\n" +
        "  {call goo.moo data=\"all\" /}\n" +
        "{/template}\n";

    runSoyFilesTestHelper(fileContent);
  }


  public void testUnusedParamWithRecursiveCall() throws SoySyntaxException {

    String soyDoc = "@param boo @param foo";
    String templateBody = "{call name=\".foo\" data=\"all\" /}";
    try {
      runTemplateTestHelper(soyDoc, templateBody);
      fail();
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage().contains(
          "Found params declared in SoyDoc but not used in template: [boo, foo]"));
    }
  }


  public void testUnusedParamInDelegateTemplate() throws SoySyntaxException {

    String fileContent =
        "{namespace boo}\n" +
        "\n" +
        "/**\n" +
        " * @param zoo\n" +  // 'zoo' may be needed in other implementations of the same delegate
        " */\n" +
        "{deltemplate MagicButton}\n" +
        "  blah\n" +
        "{/deltemplate}\n";

    runSoyFilesTestHelper(fileContent);
  }


  public void testOnlyCheckFilesInV2() throws SoySyntaxException {

    String fileContent0 =
        "{namespace boo0}\n" +
        "\n" +  // file is missing SoyDoc
        "{template .foo0}\n" +
        "  {$goo0.moo0}\n" +
        "{/template}\n";

    String fileContent1 =
        "{namespace boo1}\n" +
        "\n" +
        "/** Template 1 */\n" +
        "{template .foo1}\n" +
        "  {$goo1}\n" +
        "  {$goo1.moo1()}\n" +  // file is not all V2 syntax due to this expression
        "{/template}\n";

    String fileContent2 =
        "{namespace boo2}\n" +
        "\n" +
        "/** Template 2 */\n" +
        "{template .foo2}\n" +
        "  {$goo2.moo2}\n" +
        "{/template}\n";

    try {
      runSoyFilesTestHelper(fileContent0, fileContent1, fileContent2);
      fail();
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage().contains(
          "Found references to data keys that are not declared in SoyDoc: [goo2]"));
    }
  }


  public void testUnnecessaryUsageOfFunctionHasData() throws SoySyntaxException {

    String soyDoc = "@param boo @param? foo";
    String templateBody = "{if hasData() and $foo}{$boo}{/if}";
    try {
      runTemplateTestHelper(soyDoc, templateBody);
      fail();
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage().contains(
          "Unnecessary usage of hasData() since template has at least one required parameter."));
    }
  }


  private static void runTemplateTestHelper(String soyDoc, String templateBody)
      throws SoySyntaxException {

    String testFileContent =
        "{namespace boo}\n" +
        "\n" +
        "/** " + soyDoc + " */\n" +
        "{template name=\".foo\"}\n" +
        templateBody + "\n" +
        "{/template}\n";

    runSoyFilesTestHelper(testFileContent);
  }


  private static void runSoyFilesTestHelper(String... soyFileContents)
      throws SoySyntaxException {

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(soyFileContents);

    (new CheckSoyDocVisitor(false)).exec(soyTree);
  }

}
