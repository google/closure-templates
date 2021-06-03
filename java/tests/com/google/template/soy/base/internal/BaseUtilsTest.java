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

package com.google.template.soy.base.internal;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for BaseUtils.
 */
@RunWith(JUnit4.class)
public final class BaseUtilsTest {

  @Test
  public void testIsIdentifier() {

    assertThat(BaseUtils.isIdentifier("boo")).isTrue();
    assertThat(BaseUtils.isIdentifier("boo8Foo")).isTrue();
    assertThat(BaseUtils.isIdentifier("_8")).isTrue();
    assertThat(BaseUtils.isIdentifier("BOO_FOO")).isTrue();
    assertThat(BaseUtils.isIdentifier("boo_foo")).isTrue();
    assertThat(BaseUtils.isIdentifier("BooFoo_")).isTrue();
    assertThat(BaseUtils.isIdentifier("")).isFalse();
    assertThat(BaseUtils.isIdentifier("boo.")).isFalse();
    assertThat(BaseUtils.isIdentifier(".boo")).isFalse();
    assertThat(BaseUtils.isIdentifier("boo.foo")).isFalse();
    assertThat(BaseUtils.isIdentifier("boo-foo")).isFalse();
    assertThat(BaseUtils.isIdentifier("8boo")).isFalse();
  }

  @Test
  public void testIsIdentifierWithLeadingDot() {

    assertThat(BaseUtils.isIdentifierWithLeadingDot(".boo")).isTrue();
    assertThat(BaseUtils.isIdentifierWithLeadingDot("._8")).isTrue();
    assertThat(BaseUtils.isIdentifierWithLeadingDot(".BOO_FOO")).isTrue();
    assertThat(BaseUtils.isIdentifierWithLeadingDot("")).isFalse();
    assertThat(BaseUtils.isIdentifierWithLeadingDot("boo.")).isFalse();
    assertThat(BaseUtils.isIdentifierWithLeadingDot("boo")).isFalse();
    assertThat(BaseUtils.isIdentifierWithLeadingDot("boo.foo")).isFalse();
    assertThat(BaseUtils.isIdentifierWithLeadingDot(".boo-foo")).isFalse();
    assertThat(BaseUtils.isIdentifierWithLeadingDot(".8boo")).isFalse();
  }

  @Test
  public void testIsDottedIdentifier() {

    assertThat(BaseUtils.isDottedIdentifier("boo")).isTrue();
    assertThat(BaseUtils.isDottedIdentifier("boo.foo8.goo")).isTrue();
    assertThat(BaseUtils.isDottedIdentifier("Boo.FooGoo")).isTrue();
    assertThat(BaseUtils.isDottedIdentifier("___I_._I._I_.__")).isTrue();
    assertThat(BaseUtils.isDottedIdentifier(".boo.fooGoo")).isFalse();
    assertThat(BaseUtils.isDottedIdentifier("boo.")).isFalse();
    assertThat(BaseUtils.isDottedIdentifier("boo.8")).isFalse();
    assertThat(BaseUtils.isDottedIdentifier("boo-foo")).isFalse();
    assertThat(BaseUtils.isDottedIdentifier("_...I._I_..")).isFalse();
  }

  @Test
  public void testConvertToUpperUnderscore() {

    assertThat(BaseUtils.convertToUpperUnderscore("booFoo")).isEqualTo("BOO_FOO");
    assertThat(BaseUtils.convertToUpperUnderscore("_booFoo")).isEqualTo("BOO_FOO");
    assertThat(BaseUtils.convertToUpperUnderscore("booFoo_")).isEqualTo("BOO_FOO");
    assertThat(BaseUtils.convertToUpperUnderscore("BooFoo")).isEqualTo("BOO_FOO");
    assertThat(BaseUtils.convertToUpperUnderscore("boo_foo")).isEqualTo("BOO_FOO");
    assertThat(BaseUtils.convertToUpperUnderscore("BOO_FOO")).isEqualTo("BOO_FOO");
    assertThat(BaseUtils.convertToUpperUnderscore("__BOO__FOO__")).isEqualTo("BOO_FOO");
    assertThat(BaseUtils.convertToUpperUnderscore("Boo_Foo")).isEqualTo("BOO_FOO");
    assertThat(BaseUtils.convertToUpperUnderscore("boo8Foo")).isEqualTo("BOO_8_FOO");
    assertThat(BaseUtils.convertToUpperUnderscore("booFoo88")).isEqualTo("BOO_FOO_88");
    assertThat(BaseUtils.convertToUpperUnderscore("boo88_foo")).isEqualTo("BOO_88_FOO");
    assertThat(BaseUtils.convertToUpperUnderscore("_boo_8foo")).isEqualTo("BOO_8_FOO");
    assertThat(BaseUtils.convertToUpperUnderscore("boo_foo8")).isEqualTo("BOO_FOO_8");
    assertThat(BaseUtils.convertToUpperUnderscore("_BOO__8_FOO_")).isEqualTo("BOO_8_FOO");
  }

  @Test
  public void testEscapeToSoyString() {
    assertThat(BaseUtils.escapeToSoyString("xXx", false, QuoteStyle.SINGLE)).isEqualTo("xXx");
    assertThat(BaseUtils.escapeToSoyString("\\\"\'`\b\f\n\r\t", true, QuoteStyle.SINGLE))
        .isEqualTo("\\\\\"\\\'`\\b\\f\\n\\r\\t");

    assertThat(BaseUtils.escapeToSoyString("\u2028 \u2029", false, QuoteStyle.SINGLE))
        .isEqualTo("\u2028 \u2029");
    assertThat(BaseUtils.escapeToSoyString("<body onload='jscode'>", false, QuoteStyle.SINGLE))
        .isEqualTo("<body onload=\\'jscode\\'>");

    assertThat(BaseUtils.escapeToSoyString("\0" + "7", false, QuoteStyle.SINGLE))
        .isEqualTo("\0" + "7");

    // Properly escape supplementary codepoints
    assertThat(
            BaseUtils.escapeToSoyString(
                new StringBuilder().appendCodePoint(0x1D177).toString(), false, QuoteStyle.SINGLE))
        .isEqualTo("\ud834\udd77");
    assertThat(Character.toCodePoint((char) 0xD834, (char) 0xDD77)).isEqualTo(0x1D177);

    // Supplemental code points that aren't format controls need not be escaped
    assertThat(
            BaseUtils.escapeToSoyString(
                new StringBuilder().appendCodePoint(0x1D120).toString(), false, QuoteStyle.SINGLE))
        .isEqualTo(new StringBuilder().appendCodePoint(0x1D120).toString());
  }

  @Test
  public void testEscapeToSoyString_toAscii() {
    assertThat(BaseUtils.escapeToSoyString("xXx", true, QuoteStyle.SINGLE)).isEqualTo("xXx");
    assertThat(BaseUtils.escapeToSoyString("\\\"\'`\b\f\n\r\t", true, QuoteStyle.SINGLE))
        .isEqualTo("\\\\\"\\\'`\\b\\f\\n\\r\\t");

    assertThat(BaseUtils.escapeToSoyString("\u2028 \u2029", true, QuoteStyle.SINGLE))
        .isEqualTo("\\u2028 \\u2029");
    assertThat(BaseUtils.escapeToSoyString("<body onload='jscode'>", true, QuoteStyle.SINGLE))
        .isEqualTo("<body onload=\\'jscode\\'>");

    assertThat(BaseUtils.escapeToSoyString("\0" + "7", true, QuoteStyle.SINGLE))
        .isEqualTo("\\u00007");

    // Properly escape all supplementary codepoints
    assertThat(
            BaseUtils.escapeToSoyString(
                new StringBuilder().appendCodePoint(0x1D177).toString(), true, QuoteStyle.SINGLE))
        .isEqualTo("\\uD834\\uDD77");
    assertThat(Character.toCodePoint((char) 0xD834, (char) 0xDD77)).isEqualTo(0x1D177);
    assertThat(
            BaseUtils.escapeToSoyString(
                new StringBuilder().appendCodePoint(0x1D120).toString(), true, QuoteStyle.SINGLE))
        .isEqualTo("\\uD834\\uDD20");
    assertThat(Character.toCodePoint((char) 0xD834, (char) 0xDD20)).isEqualTo(0x1D120);
  }

  @Test
  public void testEscapeToSoyString_doubleQuoteStyle() {
    assertThat(
            BaseUtils.escapeToSoyString(
                "console.log(\"test's\", `quote$`);", false, QuoteStyle.DOUBLE))
        .isEqualTo("console.log(\\\"test's\\\", `quote$`);");
  }

  @Test
  public void testEscapeToSoyString_backtickQuoteStyle() {
    assertThat(
            BaseUtils.escapeToSoyString(
                "console.log(\"test's\", `quote$`);", false, QuoteStyle.BACKTICK))
        .isEqualTo("console.log(\"test's\", \\`quote\\$\\`);");
  }

  @Test
  public void testEscapeToWrappedSoyString() {
    assertThat(
            BaseUtils.escapeToWrappedSoyString(
                "they said, \"this'll be a `string` literal\"", false, QuoteStyle.SINGLE))
        .isEqualTo("'they said, \"this\\'ll be a `string` literal\"'");
    assertThat(
            BaseUtils.escapeToWrappedSoyString(
                "they said, \"this'll be a `string` literal\"", false, QuoteStyle.DOUBLE))
        .isEqualTo("\"they said, \\\"this'll be a `string` literal\\\"\"");
    assertThat(
            BaseUtils.escapeToWrappedSoyString(
                "they said, \"this'll be a `string` literal\"", false, QuoteStyle.BACKTICK))
        .isEqualTo("`they said, \"this'll be a \\`string\\` literal\"`");
  }

  // TODO: fix callers of wrapped
}
