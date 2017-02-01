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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.template.soy.base.SoySyntaxException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for IcuSyntaxUtils.
 *
 */
@RunWith(JUnit4.class)
public class IcuSyntaxUtilsTest {

  @Test
  public void testIcuEscape() {
    assertThat(IcuSyntaxUtils.icuEscape("")).isEmpty();
    assertThat(IcuSyntaxUtils.icuEscape("Hello world!")).isEqualTo("Hello world!");
    assertThat(IcuSyntaxUtils.icuEscape("Don't")).isEqualTo("Don't");
    assertThat(IcuSyntaxUtils.icuEscape("#5")).isEqualTo("#5");
    // no escape because we disable ICU '#'
    assertThat(IcuSyntaxUtils.icuEscape("Don''t")).isEqualTo("Don'''t");
    assertThat(IcuSyntaxUtils.icuEscape("Don'''t")).isEqualTo("Don'''''t");
    assertThat(IcuSyntaxUtils.icuEscape("the '")).isEqualTo("the ''");
    assertThat(IcuSyntaxUtils.icuEscape("the ''")).isEqualTo("the ''''");
    assertThat(IcuSyntaxUtils.icuEscape("'#5")).isEqualTo("''#5");
    assertThat(IcuSyntaxUtils.icuEscape("Set {0, 1, ...}")).isEqualTo("Set '{'0, 1, ...'}'");
    assertThat(IcuSyntaxUtils.icuEscape("Set {don't}")).isEqualTo("Set '{'don't'}'");
    assertThat(IcuSyntaxUtils.icuEscape("Set '{0, 1, ...}'")).isEqualTo("Set '''{'0, 1, ...'}'''");
  }

  @Test
  public void testCheckIcuEscapingIsNotNeeded() {

    IcuSyntaxUtils.checkIcuEscapingIsNotNeeded("");
    IcuSyntaxUtils.checkIcuEscapingIsNotNeeded("Hello world!");
    IcuSyntaxUtils.checkIcuEscapingIsNotNeeded("Don't");
    IcuSyntaxUtils.checkIcuEscapingIsNotNeeded("#5"); // no escape because we disable ICU '#'

    String expectedErrorMsgForNotSingleQuote =
        "Apologies, Soy currently does not support open/close brace characters in plural/gender"
            + " source msgs.";
    assertCheckIcuEscapingIsNotNeededFails("Set {0, 1, ...}", expectedErrorMsgForNotSingleQuote);
    assertCheckIcuEscapingIsNotNeededFails("Set {don't}", expectedErrorMsgForNotSingleQuote);
    assertCheckIcuEscapingIsNotNeededFails("Set '{0, 1, ...}'", expectedErrorMsgForNotSingleQuote);

    String expectedErrorMsgForSingleQuoteAtEnd =
        "Apologies, Soy currently does not support a single quote character at the end of a"
            + " text part in plural/gender source msgs (including immediately preceding an HTML"
            + " tag or Soy tag).";
    assertCheckIcuEscapingIsNotNeededFails("the '", expectedErrorMsgForSingleQuoteAtEnd);

    String expectedErrorMsgForSingleQuoteBeforeHash =
        "Apologies, Soy currently does not support a single quote character preceding a hash"
            + " character in plural/gender source msgs.";
    assertCheckIcuEscapingIsNotNeededFails("'#5", expectedErrorMsgForSingleQuoteBeforeHash);

    String expectedErrorMsgForConsecSingleQuote =
        "Apologies, Soy currently does not support consecutive single quote characters in"
            + " plural/gender source msgs.";
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
      assertThat(sse.getMessage()).contains(expectedErrorMsgSubstr);
    }
  }
}
