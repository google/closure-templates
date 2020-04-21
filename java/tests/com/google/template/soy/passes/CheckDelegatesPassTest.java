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
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for CheckDelegatesPass.
 *
 */
@RunWith(JUnit4.class)
public final class CheckDelegatesPassTest {

  @Test
  public void testRecognizeValidDelegatePackage() {
    assertValidSoyFiles(
        ""
            + "{namespace ns1}\n"
            + "\n"
            + "/***/\n"
            + "{template .boo}\n"
            + "  blah\n"
            + "{/template}\n",
        ""
            + "{delpackage SecretFeature}\n"
            + "{namespace ns2}\n"
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
            + "{namespace ns1}\n"
            + "\n"
            + "/***/\n"
            + "{template .boo}\n"
            + "  blah\n"
            + "{/template}\n"
            + "\n"
            + "{deltemplate MagicButton}\n"
            + "  {@param foo: ?}\n"
            + "  000\n"
            + "{/deltemplate}\n",
        ""
            + "{delpackage SecretFeature}\n"
            + "{namespace ns2}\n"
            + "\n"
            + "{deltemplate MagicButton}\n"
            + "  {@param foo: ?}\n"
            + "  111 {$foo}\n"
            + "{/deltemplate}\n");
  }

  @Test
  public void testRecognizeValidDelegateCall() {
    assertValidSoyFiles(
        ""
            + "{namespace ns1}\n"
            + "\n"
            + "/***/\n"
            + "{template .boo}\n"
            + "  {delcall MagicButton}{param foo : '' /}{/delcall}\n"
            + "{/template}\n"
            + "\n"
            + "{deltemplate MagicButton}\n"
            + "  {@param foo: ?}\n"
            + "  000\n"
            + "{/deltemplate}\n",
        ""
            + "{delpackage SecretFeature}\n"
            + "{namespace ns2}\n"
            + "\n"
            + "{deltemplate MagicButton}\n"
            + "  {@param foo: ?}\n"
            + "  111 {$foo}\n"
            + "{/deltemplate}\n");
  }

  @Test
  public void testErrorReusedTemplateName() {
    assertInvalidSoyFiles(
        "Found deltemplate ns1.boo with the same name as a template/element at no-path:4:1-6:11.",
        ""
            + "{namespace ns1}\n"
            + "\n"
            + "/***/\n"
            + "{template .boo}\n"
            + "  blah\n"
            + "{/template}\n",
        ""
            + "{delpackage SecretFeature}\n"
            + "{namespace ns2}\n"
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
            + "definition at no-path:9:1-11:14."
            + "\n  Unexpected params: [foo: ?]",
        ""
            + "{namespace ns1}\n"
            + "\n"
            + "/***/\n"
            + "{template .boo}\n"
            + "  blah\n"
            + "{/template}\n"
            + "\n"
            + "/***/\n"
            + "{deltemplate MagicButton}\n" // no params
            + "  000\n"
            + "{/deltemplate}\n",
        ""
            + "{delpackage SecretFeature}\n"
            + "{namespace ns2}\n"
            + "\n"
            + "{deltemplate MagicButton}\n"
            + "  {@param foo: ?}\n" // has param 'foo'
            + "  111 {$foo}\n"
            + "{/deltemplate}\n");

    assertInvalidSoyFiles(
        "Found delegate template with same name 'MagicButton' but different param "
            + "declarations compared to the definition at no-path:8:1-11:14."
            + "\n  Missing params: [foo: ? (optional)]"
            + "\n  Unexpected params: [foo: ?]",
        ""
            + "{namespace ns1}\n"
            + "\n"
            + "/***/\n"
            + "{template .boo}\n"
            + "  blah\n"
            + "{/template}\n"
            + "\n"
            + "{deltemplate MagicButton}\n"
            + "  {@param? foo: ?}\n" // param 'foo' is optional
            + "  000\n"
            + "{/deltemplate}\n",
        ""
            + "{delpackage SecretFeature}\n"
            + "{namespace ns2}\n"
            + "\n"
            + "{deltemplate MagicButton}\n"
            + "  {@param foo: ?}\n" // param 'foo' is required
            + "  111 {$foo}\n"
            + "{/deltemplate}\n");
  }

  @Test
  public void testErrorParamsMismatchAcrossVariants() {
    assertInvalidSoyFiles(
        "Found delegate template with same name 'MagicButton' "
            + "but different param declarations compared to the definition at no-path:4:1-6:14."
            + "\n  Unexpected params: [foo: ?]",
        ""
            + "{namespace ns1}\n"
            + "\n"
            + "/***/\n"
            + "{deltemplate MagicButton}\n" // no params
            + "  vanilla\n"
            + "{/deltemplate}\n"
            + "{deltemplate MagicButton variant=\"'something'\"}\n"
            + "  {@param foo: ?}\n" // some params params
            + "  something\n"
            + "{/deltemplate}\n");
  }

  @Test
  public void testOkNonRequiredParamsMismatchAcrossVariants() {

    assertValidSoyFiles(
        ""
            + "{namespace ns1}\n"
            + "\n"
            + "/***/\n"
            + "{deltemplate MagicButton}\n" // no params
            + "  vanilla\n"
            + "{/deltemplate}\n"
            + "{deltemplate MagicButton variant=\"'something'\"}\n"
            + "  {@param? foo: ?}\n" // an optional param
            + "  something\n"
            + "{/deltemplate}\n");
  }

  @Test
  public void testAllowPublicBasicTemplateInDelegatePackage() {
    assertValidSoyFiles(
        ""
            + "{namespace ns1}\n"
            + "\n"
            + "/***/\n"
            + "{template .boo}\n"
            + "  blah\n"
            + "{/template}\n",
        ""
            + "{delpackage SecretFeature}\n"
            + "{namespace ns2}\n"
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
        "'call' to delegate template 'ns1.MagicButton' (expected 'delcall').",
        ""
            + "{namespace ns1}\n"
            + "\n"
            + "/***/\n"
            + "{template .boo}\n"
            + "  {call .MagicButton /}\n"
            + // basic call (should be delegate call)
            "{/template}\n"
            + "\n"
            + "{deltemplate ns1.MagicButton}\n"
            + "  {@param foo: ?}\n"
            + "  000\n"
            + "{/deltemplate}\n",
        ""
            + "{delpackage SecretFeature}\n"
            + "{namespace ns1}\n"
            + "\n"
            + "{deltemplate ns1.MagicButton}\n"
            + "  {@param foo: ?}\n"
            + "  111 {$foo}\n"
            + "{/deltemplate}\n");
  }

  @Test
  public void testErrorBasicDepFromNonDelpackageOnOtherDelegatePackage() {
    assertInvalidSoyFiles(
        "Found illegal call from 'ns1.boo' to 'ns2.foo', which is in a different delegate package.",
        ""
            + "{namespace ns1}\n"
            + "\n"
            + "/***/\n"
            + "{template .boo}\n"
            + "  {call ns2.foo /}\n"
            + // call to ns2.foo, which is public
            "{/template}\n",
        ""
            + "{delpackage SecretFeature}\n"
            + "{namespace ns2}\n"
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
            + "{namespace ns1}\n"
            + "\n"
            + "/***/\n"
            + "{template .boo}\n"
            + "  {call ns2.foo /}\n"
            + // call to ns2.foo, which is public
            "{/template}\n",
        ""
            + "{delpackage SecretFeature}\n"
            + "{namespace ns2}\n"
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
            + "{namespace ns1}\n"
            + "\n"
            + "/***/\n"
            + "{template .boo}\n"
            + "  {delcall ns2.foo /}\n"
            + // delegate call (should be basic call)
            "{/template}\n",
        ""
            + "{namespace ns2}\n"
            + "\n"
            + "/***/\n"
            + "{template .foo}\n"
            + "  blah\n"
            + "{/template}\n");
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
