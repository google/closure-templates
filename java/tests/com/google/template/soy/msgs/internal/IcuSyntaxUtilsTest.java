/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.msgs.internal;

import com.google.template.soy.base.SoySyntaxException;

import junit.framework.TestCase;


/**
 * Unit tests for IcuSyntaxUtils.
 *
 */
public class IcuSyntaxUtilsTest extends TestCase {


  public void testIcuEscape() {

    assertEquals("", IcuSyntaxUtils.icuEscape(""));
    assertEquals("Hello world!", IcuSyntaxUtils.icuEscape("Hello world!"));
    assertEquals("Don't", IcuSyntaxUtils.icuEscape("Don't"));
    assertEquals("#5", IcuSyntaxUtils.icuEscape("#5"));  // no escape because we disable ICU '#'
    assertEquals("Don'''t", IcuSyntaxUtils.icuEscape("Don''t"));
    assertEquals("Don'''''t", IcuSyntaxUtils.icuEscape("Don'''t"));
    assertEquals("the ''", IcuSyntaxUtils.icuEscape("the '"));
    assertEquals("the ''''", IcuSyntaxUtils.icuEscape("the ''"));
    assertEquals("''#5", IcuSyntaxUtils.icuEscape("'#5"));
    assertEquals("Set '{'0, 1, ...'}'", IcuSyntaxUtils.icuEscape("Set {0, 1, ...}"));
    assertEquals("Set '{'don't'}'", IcuSyntaxUtils.icuEscape("Set {don't}"));
    assertEquals("Set '''{'0, 1, ...'}'''", IcuSyntaxUtils.icuEscape("Set '{0, 1, ...}'"));
  }


  public void testCheckIcuEscapingIsNotNeeded() {

    IcuSyntaxUtils.checkIcuEscapingIsNotNeeded("");
    IcuSyntaxUtils.checkIcuEscapingIsNotNeeded("Hello world!");
    IcuSyntaxUtils.checkIcuEscapingIsNotNeeded("Don't");
    IcuSyntaxUtils.checkIcuEscapingIsNotNeeded("#5");  // no escape because we disable ICU '#'

    String expectedErrorMsgForNotSingleQuote =
        "Apologies, Soy currently does not support open/close brace characters in plural/gender" +
            " source msgs.";
    assertCheckIcuEscapingIsNotNeededFails("Set {0, 1, ...}", expectedErrorMsgForNotSingleQuote);
    assertCheckIcuEscapingIsNotNeededFails("Set {don't}", expectedErrorMsgForNotSingleQuote);
    assertCheckIcuEscapingIsNotNeededFails("Set '{0, 1, ...}'", expectedErrorMsgForNotSingleQuote);

    String expectedErrorMsgForSingleQuoteAtEnd =
        "Apologies, Soy currently does not support a single quote character at the end of a" +
            " text part in plural/gender source msgs (including immediately preceding an HTML" +
            " tag or Soy tag).";
    assertCheckIcuEscapingIsNotNeededFails("the '", expectedErrorMsgForSingleQuoteAtEnd);

    String expectedErrorMsgForSingleQuoteBeforeHash =
        "Apologies, Soy currently does not support a single quote character preceding a hash" +
            " character in plural/gender source msgs.";
    assertCheckIcuEscapingIsNotNeededFails("'#5", expectedErrorMsgForSingleQuoteBeforeHash);

    String expectedErrorMsgForConsecSingleQuote =
        "Apologies, Soy currently does not support consecutive single quote characters in" +
            " plural/gender source msgs.";
    assertCheckIcuEscapingIsNotNeededFails("Don''t", expectedErrorMsgForConsecSingleQuote);
    assertCheckIcuEscapingIsNotNeededFails("Don'''t", expectedErrorMsgForConsecSingleQuote);
    assertCheckIcuEscapingIsNotNeededFails("the ''", expectedErrorMsgForConsecSingleQuote);
  }


  private void assertCheckIcuEscapingIsNotNeededFails(
      String rawText, String expectedErrorMsgSubstr) {

    try {
      IcuSyntaxUtils.checkIcuEscapingIsNotNeeded(rawText);
      fail();
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage().contains(expectedErrorMsgSubstr));
    }
  }

}
