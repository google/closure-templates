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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.testing.EqualsTester;
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
    assertThat(part.getRawText()).isEqualTo(LATIN_STRING);
    assertWithMessage("UTF8 is optimal for Latin strings").that(isUtf8Encoding(part)).isTrue();

    part = SoyMsgRawTextPart.of(LATIN_STRING_2);
    assertThat(part.getRawText()).isEqualTo(LATIN_STRING_2);
    assertWithMessage("UTF8 is optimal for Latin strings").that(isUtf8Encoding(part)).isTrue();
  }

  @Test
  public void testChinese() {
    SoyMsgRawTextPart part = SoyMsgRawTextPart.of(CHINESE_STRING);
    assertThat(part.getRawText()).isEqualTo(CHINESE_STRING);
    assertWithMessage("UTF16 is optimal for Chinese strings").that(isUtf8Encoding(part)).isFalse();

    part = SoyMsgRawTextPart.of(CHINESE_STRING_2);
    assertThat(part.getRawText()).isEqualTo(CHINESE_STRING_2);
    assertWithMessage("UTF16 is optimal for Chinese strings").that(isUtf8Encoding(part)).isFalse();
  }

  @Test
  public void testEmpty() {
    SoyMsgRawTextPart part = SoyMsgRawTextPart.of("");
    assertThat(part.getRawText()).isEmpty();
    assertWithMessage("UTF16 is used when there's a tie").that(isUtf8Encoding(part)).isFalse();
  }

  @Test
  public void testEquals() {
    assertThat(SoyMsgRawTextPart.of(LATIN_STRING)).isEqualTo(SoyMsgRawTextPart.of(LATIN_STRING));
    assertThat(SoyMsgRawTextPart.of(CHINESE_STRING))
        .isEqualTo(SoyMsgRawTextPart.of(CHINESE_STRING));

    assertThat(SoyMsgRawTextPart.of(LATIN_STRING).equals(SoyMsgRawTextPart.of(LATIN_STRING_2)))
        .isFalse();
    assertThat(SoyMsgRawTextPart.of(CHINESE_STRING).equals(SoyMsgRawTextPart.of(CHINESE_STRING_2)))
        .isFalse();

    assertThat(SoyMsgRawTextPart.of(LATIN_STRING).equals(SoyMsgRawTextPart.of(CHINESE_STRING)))
        .isFalse();
    assertThat(SoyMsgRawTextPart.of(CHINESE_STRING).equals(SoyMsgRawTextPart.of(LATIN_STRING)))
        .isFalse();

    assertThat(SoyMsgRawTextPart.of(CHINESE_STRING).equals(SoyMsgRawTextPart.of(""))).isFalse();
    assertThat(SoyMsgRawTextPart.of("").equals(SoyMsgRawTextPart.of(LATIN_STRING))).isFalse();

    new EqualsTester()
        .addEqualityGroup(SoyMsgRawTextPart.of(LATIN_STRING))
        .addEqualityGroup(new Object())
        .testEquals();
    new EqualsTester()
        .addEqualityGroup(SoyMsgRawTextPart.of(CHINESE_STRING))
        .addEqualityGroup(CHINESE_STRING)
        .testEquals();
  }

  @Test
  public void testHashCode() {
    assertThat(SoyMsgRawTextPart.of(LATIN_STRING).hashCode())
        .isEqualTo(SoyMsgRawTextPart.of(LATIN_STRING).hashCode());
    assertThat(SoyMsgRawTextPart.of(CHINESE_STRING).hashCode())
        .isEqualTo(SoyMsgRawTextPart.of(CHINESE_STRING).hashCode());

    assertThat(
            SoyMsgRawTextPart.of(LATIN_STRING).hashCode()
                == SoyMsgRawTextPart.of(LATIN_STRING_2).hashCode())
        .isFalse();
    assertThat(
            SoyMsgRawTextPart.of(CHINESE_STRING).hashCode()
                == SoyMsgRawTextPart.of(CHINESE_STRING_2).hashCode())
        .isFalse();

    assertThat(
            SoyMsgRawTextPart.of(LATIN_STRING).hashCode()
                == SoyMsgRawTextPart.of(CHINESE_STRING).hashCode())
        .isFalse();
    assertThat(
            SoyMsgRawTextPart.of(CHINESE_STRING).hashCode()
                == SoyMsgRawTextPart.of(LATIN_STRING).hashCode())
        .isFalse();

    assertThat(
            SoyMsgRawTextPart.of(CHINESE_STRING).hashCode() == SoyMsgRawTextPart.of("").hashCode())
        .isFalse();
  }
}
