/*
 * Copyright 2017 Google Inc.
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
package com.google.template.soy.soyparse;

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.soyparse.SoyParseUtils.unescapeCommandAttributeValue;

import com.google.common.base.Strings;
import com.google.template.soy.base.internal.QuoteStyle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SoyParseUtilsTest {

  @Test
  public void testUnescapeCommandAttributeValue_simple() throws Exception {
    assertThat(unescapeCommandAttributeValue("foo", QuoteStyle.DOUBLE)).isEqualTo("foo");
    assertThat(unescapeCommandAttributeValue("foo", QuoteStyle.SINGLE)).isEqualTo("foo");
    assertThat(unescapeCommandAttributeValue("\\\"", QuoteStyle.DOUBLE)).isEqualTo("\"");
    assertThat(unescapeCommandAttributeValue("\\'", QuoteStyle.SINGLE)).isEqualTo("'");
    assertThat(unescapeCommandAttributeValue("foo\\\"bar", QuoteStyle.DOUBLE))
        .isEqualTo("foo\"bar");
    assertThat(unescapeCommandAttributeValue("foo\\'bar", QuoteStyle.SINGLE)).isEqualTo("foo'bar");
  }

  @Test
  public void testUnescapeCommandAttributeValue_many() throws Exception {
    final int n = 10_000;
    String tenKFoos = Strings.repeat("foo", n);
    assertThat(unescapeCommandAttributeValue(tenKFoos, QuoteStyle.DOUBLE)).isEqualTo(tenKFoos);
    assertThat(unescapeCommandAttributeValue(tenKFoos, QuoteStyle.SINGLE)).isEqualTo(tenKFoos);
    assertThat(unescapeCommandAttributeValue(Strings.repeat("\\\"", n), QuoteStyle.DOUBLE))
        .isEqualTo(Strings.repeat("\"", n));
    assertThat(unescapeCommandAttributeValue(Strings.repeat("\\'", n), QuoteStyle.SINGLE))
        .isEqualTo(Strings.repeat("'", n));
    assertThat(unescapeCommandAttributeValue(Strings.repeat("foo\\\"bar", n), QuoteStyle.DOUBLE))
        .isEqualTo(Strings.repeat("foo\"bar", n));
    assertThat(unescapeCommandAttributeValue(Strings.repeat("foo\\'bar", n), QuoteStyle.SINGLE))
        .isEqualTo(Strings.repeat("foo'bar", n));
  }
}
