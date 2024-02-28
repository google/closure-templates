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

import static com.google.common.base.Utf8.encodedLength;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Splitter;
import com.google.common.base.Utf8;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SourceLocation.ByteSpan;
import com.google.template.soy.base.SourceLocation.Point;

/** Translation table between {@link SourceLocation} and {@link ByteSpan}, per source file. */
@Immutable
public final class ByteOffsetIndex {

  public static final ByteOffsetIndex EMPTY =
      new ByteOffsetIndex(ImmutableList.of(), ImmutableList.of());

  public static ByteOffsetIndex parse(String contents) {
    ImmutableList<String> lines =
        Splitter.on('\n').splitToStream(contents).collect(toImmutableList());
    ImmutableList.Builder<Integer> startOffsets = ImmutableList.builder();

    int currIndex = 0;
    for (String line : lines) {
      startOffsets.add(currIndex);
      currIndex += Utf8.encodedLength(line);
      currIndex++; // new line char
    }

    startOffsets.add(currIndex);
    return new ByteOffsetIndex(lines, startOffsets.build());
  }

  private final ImmutableList<String> lines;
  private final ImmutableList<Integer> startOffsets;

  ByteOffsetIndex(ImmutableList<String> lines, ImmutableList<Integer> startOffsets) {
    this.lines = lines;
    this.startOffsets = startOffsets;
  }

  public ByteSpan getByteSpan(SourceLocation loc) {
    Point begin = loc.getBeginPoint();
    Point end = loc.getEndPoint();
    try {
      // We want to calculate the begin/end byte index values, which are put in the begin and end
      // variables below. Note these need to be 0-based.
      String firstLine = getLine(begin.line());

      // Figure out where the begin column is. Note the -1 for column values are 1-based.
      int beginOffset =
          getOffset(begin.line()) + encodedLength(firstLine.substring(0, begin.column() - 1));

      if (begin.line() == end.line()) {
        return ByteSpan.create(
            beginOffset,
            beginOffset + encodedLength(firstLine.substring(begin.column() - 1, end.column())));
      }

      int endOffset = beginOffset + encodedLength(firstLine.substring(begin.column() - 1));
      for (int i = begin.line() + 1; i < end.line(); i++) {
        endOffset++; // new line
        endOffset += encodedLength(getLine(i));
      }
      endOffset++; // new line
      endOffset += encodedLength(getLine(end.line()).substring(0, end.column()));

      return ByteSpan.create(beginOffset, endOffset);
    } catch (IndexOutOfBoundsException e) {
      throw new IllegalArgumentException(
          String.format("Location %s-%s invalid for contents", begin, end), e);
    }
  }

  /** Returns the line at 1-based index {@code oneBased}. */
  String getLine(int oneBased) {
    return lines.get(oneBased - 1);
  }

  /**
   * Returns the utf8 encoded length of all the lines up to the line at 1-based index {@code
   * oneBased}.
   */
  int getOffset(int oneBased) {
    return startOffsets.get(oneBased - 1);
  }
}
