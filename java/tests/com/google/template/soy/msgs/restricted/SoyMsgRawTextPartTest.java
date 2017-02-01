/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.msgs.restricted;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit test for raw text messages, which use a semi complex dynamic encoding.
 *
 */
@RunWith(JUnit4.class)
public class SoyMsgRawTextPartTest {

  private static final String LATIN_STRING = "Hello Kitty";
  private static final String LATIN_STRING_2 = "Goodbye Kitty";
  private static final String CHINESE_STRING = "\u4F60\u597D\u5C0F\u732B";
  private static final String CHINESE_STRING_2 = "\u518D\u89C1\u5C0F\u732B";

  private static boolean isUtf8Encoding(SoyMsgRawTextPart msg) {
    return msg instanceof SoyMsgRawTextPart.Utf8SoyMsgRawTextPart;
  }

  @Test
  public void testLatin() {
    SoyMsgRawTextPart part = SoyMsgRawTextPart.of(LATIN_STRING);
    assertEquals(LATIN_STRING, part.getRawText());
    assertTrue("UTF8 is optimal for Latin strings", isUtf8Encoding(part));

    part = SoyMsgRawTextPart.of(LATIN_STRING_2);
    assertEquals(LATIN_STRING_2, part.getRawText());
    assertTrue("UTF8 is optimal for Latin strings", isUtf8Encoding(part));
  }

  @Test
  public void testChinese() {
    SoyMsgRawTextPart part = SoyMsgRawTextPart.of(CHINESE_STRING);
    assertEquals(CHINESE_STRING, part.getRawText());
    assertFalse("UTF16 is optimal for Chinese strings", isUtf8Encoding(part));

    part = SoyMsgRawTextPart.of(CHINESE_STRING_2);
    assertEquals(CHINESE_STRING_2, part.getRawText());
    assertFalse("UTF16 is optimal for Chinese strings", isUtf8Encoding(part));
  }

  @Test
  public void testEmpty() {
    SoyMsgRawTextPart part = SoyMsgRawTextPart.of("");
    assertEquals("", part.getRawText());
    assertFalse("UTF16 is used when there's a tie", isUtf8Encoding(part));
  }

  @Test
  public void testEquals() {
    assertEquals(SoyMsgRawTextPart.of(LATIN_STRING), SoyMsgRawTextPart.of(LATIN_STRING));
    assertEquals(SoyMsgRawTextPart.of(CHINESE_STRING), SoyMsgRawTextPart.of(CHINESE_STRING));

    assertFalse(SoyMsgRawTextPart.of(LATIN_STRING).equals(SoyMsgRawTextPart.of(LATIN_STRING_2)));
    assertFalse(
        SoyMsgRawTextPart.of(CHINESE_STRING).equals(SoyMsgRawTextPart.of(CHINESE_STRING_2)));

    assertFalse(SoyMsgRawTextPart.of(LATIN_STRING).equals(SoyMsgRawTextPart.of(CHINESE_STRING)));
    assertFalse(SoyMsgRawTextPart.of(CHINESE_STRING).equals(SoyMsgRawTextPart.of(LATIN_STRING)));

    assertFalse(SoyMsgRawTextPart.of(CHINESE_STRING).equals(SoyMsgRawTextPart.of("")));
    assertFalse(SoyMsgRawTextPart.of("").equals(SoyMsgRawTextPart.of(LATIN_STRING)));

    assertFalse("Different types", SoyMsgRawTextPart.of(LATIN_STRING).equals(new Object()));
    assertFalse("Different types", SoyMsgRawTextPart.of(CHINESE_STRING).equals(CHINESE_STRING));
  }

  @Test
  public void testHashCode() {
    assertEquals(
        SoyMsgRawTextPart.of(LATIN_STRING).hashCode(),
        SoyMsgRawTextPart.of(LATIN_STRING).hashCode());
    assertEquals(
        SoyMsgRawTextPart.of(CHINESE_STRING).hashCode(),
        SoyMsgRawTextPart.of(CHINESE_STRING).hashCode());

    assertFalse(
        SoyMsgRawTextPart.of(LATIN_STRING).hashCode()
            == SoyMsgRawTextPart.of(LATIN_STRING_2).hashCode());
    assertFalse(
        SoyMsgRawTextPart.of(CHINESE_STRING).hashCode()
            == SoyMsgRawTextPart.of(CHINESE_STRING_2).hashCode());

    assertFalse(
        SoyMsgRawTextPart.of(LATIN_STRING).hashCode()
            == SoyMsgRawTextPart.of(CHINESE_STRING).hashCode());
    assertFalse(
        SoyMsgRawTextPart.of(CHINESE_STRING).hashCode()
            == SoyMsgRawTextPart.of(LATIN_STRING).hashCode());

    assertFalse(
        SoyMsgRawTextPart.of(CHINESE_STRING).hashCode() == SoyMsgRawTextPart.of("").hashCode());
  }
}
