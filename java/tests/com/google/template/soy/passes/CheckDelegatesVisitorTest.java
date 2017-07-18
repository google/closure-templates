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

import com.google.common.collect.Iterables;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for CheckDelegatesVisitor.
 *
 */
@RunWith(JUnit4.class)
public final class CheckDelegatesVisitorTest {

  @Test
  public void testRecognizeValidDelegatePackage() {
    assertValidSoyFiles(
        ""
            + "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .boo}\n"
            + "  blah\n"
            + "{/template}\n",
        ""
            + "{delpackage SecretFeature}\n"
            + "{namespace ns2 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .foo}\n"
            + "  blah\n"
            + "{/template}\n");
  }

  @Test
  public void testRecognizeValidDelegateTemplate() {
    assertValidSoyFiles(
        ""
            + "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .boo}\n"
            + "  blah\n"
            + "{/template}\n"
            + "\n"
            + "/** @param foo */\n"
            + "{deltemplate MagicButton}\n"
            + "  000\n"
            + "{/deltemplate}\n",
        ""
            + "{delpackage SecretFeature}\n"
            + "{namespace ns2 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** @param foo */\n"
            + "{deltemplate MagicButton}\n"
            + "  111 {$foo}\n"
            + "{/deltemplate}\n");
  }

  @Test
  public void testRecognizeValidDelegateCall() {
    assertValidSoyFiles(
        ""
            + "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .boo}\n"
            + "  {delcall MagicButton}{param foo : '' /}{/delcall}\n"
            + "{/template}\n"
            + "\n"
            + "/** @param foo */\n"
            + "{deltemplate MagicButton}\n"
            + "  000\n"
            + "{/deltemplate}\n",
        ""
            + "{delpackage SecretFeature}\n"
            + "{namespace ns2 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** @param foo */\n"
            + "{deltemplate MagicButton}\n"
            + "  111 {$foo}\n"
            + "{/deltemplate}\n");
  }

  @Test
  public void testErrorReusedTemplateName() {
    assertInvalidSoyFiles(
        "Found deltemplate ns1.boo with the same name as a basic template at no-path:4:1.",
        ""
            + "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .boo}\n"
            + "  blah\n"
            + "{/template}\n",
        ""
            + "{delpackage SecretFeature}\n"
            + "{namespace ns2 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{deltemplate ns1.boo}\n"
            + // reused name ns1.boo
            "  111\n"
            + "{/deltemplate}\n");
  }

  @Test
  public void testErrorParamsMismatch() {
    assertInvalidSoyFiles(
        "Found delegate template with same name 'MagicButton' "
            + "but different param declarations compared to the "
            + "definition at no-path:9:1.",
        ""
            + "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .boo}\n"
            + "  blah\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + // no params
            "{deltemplate MagicButton}\n"
            + "  000\n"
            + "{/deltemplate}\n",
        ""
            + "{delpackage SecretFeature}\n"
            + "{namespace ns2 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** @param foo */\n"
            + // has param 'foo'
            "{deltemplate MagicButton}\n"
            + "  111 {$foo}\n"
            + "{/deltemplate}\n");

    assertInvalidSoyFiles(
        "Found delegate template with same name 'MagicButton' but different param "
            + "declarations compared to the definition at no-path:9:1.",
        ""
            + "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .boo}\n"
            + "  blah\n"
            + "{/template}\n"
            + "\n"
            + "/** @param? foo */\n"
            + // param 'foo' is optional
            "{deltemplate MagicButton}\n"
            + "  000\n"
            + "{/deltemplate}\n",
        ""
            + "{delpackage SecretFeature}\n"
            + "{namespace ns2 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** @param foo */\n"
            + // param 'foo' is required
            "{deltemplate MagicButton}\n"
            + "  111 {$foo}\n"
            + "{/deltemplate}\n");
  }

  @Test
  public void testErrorParamsMismatchAcrossVariants() {
    assertInvalidSoyFiles(
        "Found delegate template with same name 'MagicButton' "
            + "but different param declarations compared to the definition at no-path:4:1.",
        ""
            + "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + // no params
            "{deltemplate MagicButton}\n"
            + "  vanilla\n"
            + "{/deltemplate}\n"
            + "/** @param foo */\n"
            + // some params params
            "{deltemplate MagicButton variant=\"'something'\"}\n"
            + "  something\n"
            + "{/deltemplate}\n");
  }

  @Test
  public void testOkNonRequiredParamsMismatchAcrossVariants() {

    assertValidSoyFiles(
        ""
            + "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + // no params
            "{deltemplate MagicButton}\n"
            + "  vanilla\n"
            + "{/deltemplate}\n"
            + "/** @param? foo */\n"
            + // an optional param
            "{deltemplate MagicButton variant=\"'something'\"}\n"
            + "  something\n"
            + "{/deltemplate}\n");
  }

  @Test
  public void testAllowPublicBasicTemplateInDelegatePackage() {
    assertValidSoyFiles(
        ""
            + "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .boo}\n"
            + "  blah\n"
            + "{/template}\n",
        ""
            + "{delpackage SecretFeature}\n"
            + "{namespace ns2 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .foo}\n"
            + // not marked private
            "  blah\n"
            + "{/template}\n");
  }

  @Test
  public void testErrorBasicCallToDelegateTemplate() {
    assertInvalidSoyFiles(
        "'call' to delegate template 'MagicButton' (expected 'delcall').",
        ""
            + "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .boo}\n"
            + "  {call MagicButton /}\n"
            + // basic call (should be delegate call)
            "{/template}\n"
            + "\n"
            + "/** @param foo */\n"
            + "{deltemplate MagicButton}\n"
            + "  000\n"
            + "{/deltemplate}\n",
        ""
            + "{delpackage SecretFeature}\n"
            + "{namespace ns2 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** @param foo */\n"
            + "{deltemplate MagicButton}\n"
            + "  111 {$foo}\n"
            + "{/deltemplate}\n");
  }

  @Test
  public void testErrorBasicDepFromNonDelpackageOnOtherDelegatePackage() {
    assertInvalidSoyFiles(
        "Found illegal call from 'ns1.boo' to 'ns2.foo', which is in a different delegate package.",
        ""
            + "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .boo}\n"
            + "  {call ns2.foo /}\n"
            + // call to ns2.foo, which is public
            "{/template}\n",
        ""
            + "{delpackage SecretFeature}\n"
            + "{namespace ns2 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .foo}\n"
            + "  blah\n"
            + "{/template}\n");
  }

  @Test
  public void testErrorBasicDepOnOtherDelegatePackage() {
    assertInvalidSoyFiles(
        "Found illegal call from 'ns1.boo' to 'ns2.foo', which is in a different delegate package.",
        ""
            + "{delpackage NotQuiteSoSecretFeature}\n"
            + "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .boo}\n"
            + "  {call ns2.foo /}\n"
            + // call to ns2.foo, which is public
            "{/template}\n",
        ""
            + "{delpackage SecretFeature}\n"
            + "{namespace ns2 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .foo}\n"
            + "  blah\n"
            + "{/template}\n");
  }

  @Test
  public void testErrorDelegateCallToBasicTemplate() {
    assertInvalidSoyFiles(
        "'delcall' to basic template 'ns2.foo' (expected 'call').",
        ""
            + "{namespace ns1 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .boo}\n"
            + "  {delcall ns2.foo /}\n"
            + // delegate call (should be basic call)
            "{/template}\n",
        ""
            + "{namespace ns2 autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/***/\n"
            + "{template .foo}\n"
            + "  blah\n"
            + "{/template}\n");
  }

  @Test
  public void testStrictModeContentKindMatches() {
    // One is strict and the other is not.
    assertInvalidSoyFiles(
        "If one deltemplate has strict autoescaping, all its peers must also be strictly "
            + "autoescaped with the same content kind: null != HTML. "
            + "Conflicting definition at no-path-2:5:1.",
        ""
            + "{namespace ns}\n\n"
            + "/***/\n"
            + "{template .main autoescape=\"deprecated-contextual\"}\n"
            + "{delcall foo}\n"
            + "{param x: '' /}\n"
            + "{/delcall}\n"
            + "{/template}",
        ""
            + "{delpackage dp1}\n"
            + "{namespace ns}\n\n"
            + "/** @param x */\n"
            + "{deltemplate foo autoescape=\"deprecated-contextual\"}\n"
            + "<b>{$x}</b>\n"
            + "{/deltemplate}",
        ""
            + "{delpackage dp2}\n"
            + "{namespace ns}\n\n"
            + "/** @param x */\n"
            + "{deltemplate foo autoescape=\"strict\" kind=\"html\"}\n"
            + "<i>{$x}</i>\n"
            + "{/deltemplate}");

    // Both are strict, but have non-matching kinds.
    assertInvalidSoyFiles(
        "If one deltemplate has strict autoescaping, all its peers must also be strictly "
            + "autoescaped with the same content kind: HTML != TEXT. "
            + "Conflicting definition at no-path-2:4:1.",
        ""
            + "{namespace ns}\n\n"
            + "/***/\n"
            + "{template .main autoescape=\"deprecated-contextual\"}\n"
            + "{delcall foo}\n"
            + "{param x: '' /}\n"
            + "{/delcall}\n"
            + "{/template}",
        ""
            + "{namespace ns.default}\n\n"
            + "/** @param x */\n"
            + "{deltemplate foo autoescape=\"strict\" kind=\"html\"}\n"
            + "<b>{$x}</b>\n"
            + "{/deltemplate}",
        ""
            + "{delpackage dp2}\n"
            + "{namespace ns}\n\n"
            + "/** @param x */\n"
            + "{deltemplate foo autoescape=\"strict\" kind=\"text\"}\n"
            + "<i>{$x}</i>\n"
            + "{/deltemplate}");
  }

  private void assertValidSoyFiles(String... soyFileContents) {
    SoyFileSetParserBuilder.forFileContents(soyFileContents).parse();
  }

  private void assertInvalidSoyFiles(String expectedErrorMsgSubstr, String... soyFileContents) {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(soyFileContents).errorReporter(errorReporter).parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo(expectedErrorMsgSubstr);
  }
}
