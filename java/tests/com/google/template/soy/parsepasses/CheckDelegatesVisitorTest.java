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

import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.shared.internal.SharedTestUtils;
import com.google.template.soy.sharedpasses.CheckSoyDocVisitor;
import com.google.template.soy.soytree.SoyFileSetNode;

import junit.framework.TestCase;


/**
 * Unit tests for CheckDelegatesVisitor.
 *
 */
public class CheckDelegatesVisitorTest extends TestCase {


  public void testRecognizeValidDelegatePackage() {

    assertValidSoyFiles(
        "" +
            "{namespace ns1}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  blah\n" +
            "{/template}\n",
        "" +
            "{delpackage SecretFeature}\n" +
            "{namespace ns2}\n" +
            "\n" +
            "/***/\n" +
            "{template .foo private=\"true\"}\n" +
            "  blah\n" +
            "{/template}\n");
  }


  public void testRecognizeValidDelegateTemplate() {

    assertValidSoyFiles(
        "" +
            "{namespace ns1}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  blah\n" +
            "{/template}\n" +
            "\n" +
            "/** @param foo */\n" +
            "{deltemplate MagicButton}\n" +
            "  000\n" +
            "{/deltemplate}\n",
        "" +
            "{delpackage SecretFeature}\n" +
            "{namespace ns2}\n" +
            "\n" +
            "/** @param foo */\n" +
            "{deltemplate MagicButton}\n" +
            "  111 {$foo}\n" +
            "{/deltemplate}\n");
  }


  public void testRecognizeValidDelegateCall() {

    assertValidSoyFiles(
        "" +
            "{namespace ns1}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  {delcall MagicButton /}\n" +
            "{/template}\n" +
            "\n" +
            "/** @param foo */\n" +
            "{deltemplate MagicButton}\n" +
            "  000\n" +
            "{/deltemplate}\n",
        "" +
            "{delpackage SecretFeature}\n" +
            "{namespace ns2}\n" +
            "\n" +
            "/** @param foo */\n" +
            "{deltemplate MagicButton}\n" +
            "  111 {$foo}\n" +
            "{/deltemplate}\n");
  }


  public void testErrorReusedTemplateName() {

    assertInvalidSoyFiles(
        "Found template name [ns1.boo] being reused for both basic and delegate templates.",
        "" +
            "{namespace ns1}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  blah\n" +
            "{/template}\n",
        "" +
            "{delpackage SecretFeature}\n" +
            "{namespace ns2}\n" +
            "\n" +
            "/***/\n" +
            "{deltemplate ns1.boo}\n" +  // reused name ns1.boo
            "  111\n" +
            "{/deltemplate}\n");
  }


  public void testErrorParamsMismatch() {

    assertInvalidSoyFiles(
        "Found delegate templates with same name 'MagicButton' but different param declarations" +
            " in delegate packages 'SecretFeature' and 'none'.",
        "" +
            "{namespace ns1}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  blah\n" +
            "{/template}\n" +
            "\n" +
            "/***/\n" +  // no params
            "{deltemplate MagicButton}\n" +
            "  000\n" +
            "{/deltemplate}\n",
        "" +
            "{delpackage SecretFeature}\n" +
            "{namespace ns2}\n" +
            "\n" +
            "/** @param foo */\n" +  // has param 'foo'
            "{deltemplate MagicButton}\n" +
            "  111 {$foo}\n" +
            "{/deltemplate}\n");

    assertInvalidSoyFiles(
        "Found delegate templates with same name 'MagicButton' but different param declarations" +
            " in delegate packages 'SecretFeature' and 'none'.",
        "" +
            "{namespace ns1}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  blah\n" +
            "{/template}\n" +
            "\n" +
            "/** @param? foo */\n" +  // param 'foo' is optional
            "{deltemplate MagicButton}\n" +
            "  000\n" +
            "{/deltemplate}\n",
        "" +
            "{delpackage SecretFeature}\n" +
            "{namespace ns2}\n" +
            "\n" +
            "/** @param foo */\n" +  // param 'foo' is required
            "{deltemplate MagicButton}\n" +
            "  111 {$foo}\n" +
            "{/deltemplate}\n");
  }


  public void testErrorPublicBasicTemplateInDelegatePackage() {

    assertInvalidSoyFiles(
        "Found public template 'ns2.foo' in delegate package 'SecretFeature'" +
            " (must mark as private).",
        "" +
            "{namespace ns1}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  blah\n" +
            "{/template}\n",
        "" +
            "{delpackage SecretFeature}\n" +
            "{namespace ns2}\n" +
            "\n" +
            "/***/\n" +
            "{template .foo}\n" +  // not marked private
            "  blah\n" +
            "{/template}\n");
  }


  public void testErrorBasicCallToDelegateTemplate() {

    assertInvalidSoyFiles(
        "In template 'ns1.boo', found a 'call' referencing a delegate template 'MagicButton'" +
            " (expected 'delcall').",
        "" +
            "{namespace ns1}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  {call MagicButton /}\n" +  // basic call (should be delegate call)
            "{/template}\n" +
            "\n" +
            "/** @param foo */\n" +
            "{deltemplate MagicButton}\n" +
            "  000\n" +
            "{/deltemplate}\n",
        "" +
            "{delpackage SecretFeature}\n" +
            "{namespace ns2}\n" +
            "\n" +
            "/** @param foo */\n" +
            "{deltemplate MagicButton}\n" +
            "  111 {$foo}\n" +
            "{/deltemplate}\n");
  }


  public void testErrorBasicDepOnOtherDelegatePackage() {

    assertInvalidSoyFiles(
        "Found illegal call from 'ns1.boo' to 'ns2.foo', which is in a different delegate package.",
        "" +
            "{namespace ns1}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  {call ns2.foo /}\n" +  // call to ns2.foo
            "{/template}\n",
        "" +
            "{delpackage SecretFeature}\n" +
            "{namespace ns2}\n" +
            "\n" +
            "/***/\n" +
            "{template .foo private=\"true\"}\n" +
            "  blah\n" +
            "{/template}\n");
  }


  public void testErrorDelegateCallToBasicTemplate() {

    assertInvalidSoyFiles(
        "In template 'ns1.boo', found a 'delcall' referencing a basic template 'ns2.foo'" +
            " (expected 'call').",
        "" +
            "{namespace ns1}\n" +
            "\n" +
            "/***/\n" +
            "{template .boo}\n" +
            "  {delcall ns2.foo /}\n" +  // delegate call (should be basic call)
            "{/template}\n",
        "" +
            "{namespace ns2}\n" +
            "\n" +
            "/***/\n" +
            "{template .foo private=\"true\"}\n" +
            "  blah\n" +
            "{/template}\n");
  }


  private void assertValidSoyFiles(String... soyFileContents) {
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(soyFileContents);
    (new CheckSoyDocVisitor(false)).exec(soyTree);
    (new CheckDelegatesVisitor()).exec(soyTree);
  }


  private void assertInvalidSoyFiles(String expectedErrorMsgSubstr, String... soyFileContents) {

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(soyFileContents);
    (new CheckSoyDocVisitor(false)).exec(soyTree);
    try {
      (new CheckDelegatesVisitor()).exec(soyTree);
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage().contains(expectedErrorMsgSubstr));
      return;  // test passes
    }
    fail();
  }

}
