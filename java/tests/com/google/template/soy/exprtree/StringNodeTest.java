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

package com.google.template.soy.exprtree;

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.QuoteStyle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for StringNode.
 *
 */
@RunWith(JUnit4.class)
public final class StringNodeTest {

  @Test
  public void testToSourceString() {
    StringNode sn =
        new StringNode("Aa`! \n \r \t \\ ' \"", QuoteStyle.SINGLE, SourceLocation.UNKNOWN);
    assertThat(sn.toSourceString()).isEqualTo("'Aa`! \\n \\r \\t \\\\ \\\' \"'");

    sn = new StringNode("\u2222 \uEEEE \u9EC4 \u607A", QuoteStyle.SINGLE, SourceLocation.UNKNOWN);
    assertThat(sn.toSourceString()).isEqualTo("'\u2222 \uEEEE \u9EC4 \u607A'");
    assertThat(sn.toSourceString(true)).isEqualTo("'\\u2222 \\uEEEE \\u9EC4 \\u607A'");
  }

  @Test
  public void testToSourceString_doubleQuoted() {
    StringNode sn =
        new StringNode("Aa`! \n \r \t \\ ' \"", QuoteStyle.DOUBLE, SourceLocation.UNKNOWN);
    assertThat(sn.toSourceString()).isEqualTo("\"Aa`! \\n \\r \\t \\\\ ' \\\"\"");

    sn = new StringNode("\u2222 \uEEEE \u9EC4 \u607A", QuoteStyle.DOUBLE, SourceLocation.UNKNOWN);
    assertThat(sn.toSourceString()).isEqualTo("\"\u2222 \uEEEE \u9EC4 \u607A\"");
    assertThat(sn.toSourceString(true)).isEqualTo("\"\\u2222 \\uEEEE \\u9EC4 \\u607A\"");
  }
}
