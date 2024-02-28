/*
 * Copyright 2024 Google Inc.
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

package com.google.template.soy.soytree;

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SourceLocation.ByteSpan;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ByteOffsetIndexTest {

  private static final SourceFilePath SOME_SOY_PATH = SourceFilePath.forTest("baz.soy");

  private static final String SOME_SOY_CONTENT =
      "{namespace ns}\n" // Line 1
          + "\n" // Line 2
          + "{template foo}\n" // Line 3
          + "  {call bar /}\n" // Line 4
          + "{/template}\n" // Line 5
          + "\n" // Line 6
          + "{template bar}\n" // Line 7
          + "  bar bar bar!\n" // Line 8
          + "{/template}"; // Line 9

  private static final String SOME_SOY_CONTENT_WITH_CARRIAGE_RETURNS =
      "{namespace ns}\r\n" // Line 1
          + "\r\n" // Line 2
          + "{template foo}\r\n" // Line 3
          + "  {call bar /}\r\n" // Line 4
          + "{/template}\r\n" // Line 5
          + "\r\n" // Line 6
          + "{template bar}\r\n" // Line 7
          + "  bar bar bar!\r\n" // Line 8
          + "{/template}"; // Line 9

  @Test
  public void testConvertToSpan_LocationIsOnFirstLine() {
    // Create a source location that points to "ns" in template definition.
    SourceLocation location = new SourceLocation(SOME_SOY_PATH, 1, 12, 1, 13);

    // Check that the span is created correctly.
    ByteSpan span = ByteOffsetIndex.parse(SOME_SOY_CONTENT).getByteSpan(location);
    assertThat(span).isNotNull();
    assertThat(span.getStart()).isEqualTo(11);
    assertThat(span.getEnd()).isEqualTo(13);
  }

  @Test
  public void testConvertToSpan_LocationIsBeyondFirstFewLines() {
    // Create a source location that points to ".bar" in template definition.
    SourceLocation location = new SourceLocation(SOME_SOY_PATH, 7, 11, 7, 14);

    // Check that the span is created correctly.
    ByteSpan span = ByteOffsetIndex.parse(SOME_SOY_CONTENT).getByteSpan(location);
    assertThat(span).isNotNull();
    assertThat(span.getStart()).isEqualTo(69);
    assertThat(span.getEnd()).isEqualTo(73);
  }

  @Test
  public void testConvertToSpan_LocationIsBeyondFirstFewLinesWithCarriageReturns() {
    // Create a source location that points to ".bar" in template definition.
    SourceLocation location = new SourceLocation(SOME_SOY_PATH, 7, 11, 7, 14);

    // Check that the span is created correctly.
    ByteSpan span =
        ByteOffsetIndex.parse(SOME_SOY_CONTENT_WITH_CARRIAGE_RETURNS).getByteSpan(location);
    assertThat(span).isNotNull();
    assertThat(span.getStart()).isEqualTo(75);
    assertThat(span.getEnd()).isEqualTo(79);
  }
}
