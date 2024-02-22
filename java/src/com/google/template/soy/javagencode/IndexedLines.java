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

package com.google.template.soy.javagencode;

import static com.google.common.base.Utf8.encodedLength;

import com.google.common.base.Utf8;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SourceLocation.Point;

/**
 * Cached decomposition of a source file into lines, keeping track of the character offset start of
 * each line.
 */
public final class IndexedLines {
  private final String[] lines;
  private final int[] startOffsets;

  public IndexedLines(String contents) {
    lines = contents.split("\n", -1);
    startOffsets = new int[lines.length + 1];

    int currIndex = 0;
    for (int i = 0; i < lines.length; i++) {
      startOffsets[i] = currIndex;
      currIndex += Utf8.encodedLength(lines[i]);
      currIndex++; // new line char
    }

    startOffsets[startOffsets.length - 1] = currIndex;
  }

  public ByteSpan convertToSpan(SourceLocation loc) {
    Point begin = loc.getBeginPoint();
    Point end = loc.getEndPoint();

    // We want to calculate the begin/end byte index values, which are put in the begin and end
    // variables below. Note these need to be 0-based.

    String firstLine = getLine(begin.line());

    // Figure out where the begin column is. Note the -1 for column values are 1-based.
    int beginOffset =
        getOffset(begin.line()) + encodedLength(firstLine.substring(0, begin.column() - 1));

    if (begin.line() == end.line()) {
      return new ByteSpan(
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

    return new ByteSpan(beginOffset, endOffset);
  }

  public String getContents(SourceLocation loc) {
    return getContents(loc.getBeginPoint(), loc.getEndPoint());
  }

  public String getContents(Point begin, Point endInc) {
    if (begin.line() == endInc.line()) {
      return lines[begin.line() - 1].substring(begin.column() - 1, endInc.column());
    }
    StringBuilder sb = new StringBuilder();
    sb.append(lines[begin.line() - 1].substring(begin.column() - 1));
    for (int i = begin.line() + 1; i < endInc.line(); i++) {
      sb.append('\n').append(lines[i - 1]);
    }
    sb.append('\n').append(lines[endInc.line() - 1].substring(0, endInc.column()));
    return sb.toString();
  }

  /** Returns the line at 1-based index {@code oneBased}. */
  public String getLine(int oneBased) {
    return lines[oneBased - 1];
  }

  /**
   * Returns the utf8 encoded length of all the lines up to the line at 1-based index {@code
   * oneBased}.
   */
  public int getOffset(int oneBased) {
    return startOffsets[oneBased - 1];
  }

  /** Returns the utf8 encoded length of the line at 1-based index {@code oneBased}. */
  public int getLength(int oneBased) {
    return startOffsets[oneBased] - startOffsets[oneBased - 1] - /* new line char */ 1;
  }
}
