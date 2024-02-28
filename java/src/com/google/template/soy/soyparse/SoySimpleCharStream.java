/*
 * Copyright 2020 Google Inc.
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

import com.google.common.base.Utf8;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.base.SourceLocation.Point;
import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.Arrays;

/**
 * Extends the generated char stream here:
 */
final class SoySimpleCharStream extends SimpleCharStream {
  // The length of each line (1-indexed), updated only up to the latest char we've parsed so far
  // (e.g. if we're midway through parsing line 150, only lines 1 - 149 will have the correct
  // lengths).
  int[] lineLengths = new int[2048];

  // Current utf8 byte offset.
  private int currentByteOffset = 0;
  // Buffered chars waiting to be used for calculating encodedLength.
  private final char[] utf8Buffer = new char[2048];
  // Buffer index.
  private int utf8BufferIdx = 0;
  // Byte offset of last token start.
  int tokenBeginByteOffset = -1;

  /** Returns the full contents of the file read so far. Only call this after encountering EOF. */
  String getFullFile() {
    return ((FullBuffer) inputStream).buffer.toString();
  }

  @Override
  public char readChar() throws IOException {
    char c = super.readChar();
    updateLineLengthsForNewChar();
    utf8Buffer[utf8BufferIdx++] = c;
    if (utf8BufferIdx == utf8Buffer.length) {
      flushUtf8Buffer();
    }
    return c;
  }

  private void flushUtf8Buffer() {
    int limit = 4;
    int badTrailingBytes = 0;
    while (badTrailingBytes < limit) {
      try {
        currentByteOffset +=
            Utf8.encodedLength(CharBuffer.wrap(utf8Buffer, 0, utf8BufferIdx - badTrailingBytes));
        break;
      } catch (RuntimeException e) {
        // Ignore error when the last few bytes are non-terminated sequences.
        if (badTrailingBytes == limit - 1) {
          throw e;
        }
      }
      badTrailingBytes++;
    }
    if (badTrailingBytes > 0) {
      System.arraycopy(
          utf8Buffer, utf8Buffer.length - badTrailingBytes, utf8Buffer, 0, badTrailingBytes);
    }
    utf8BufferIdx = badTrailingBytes;
  }

  public int getCurrentByteOffset() {
    if (utf8BufferIdx > 0) {
      currentByteOffset += Utf8.encodedLength(CharBuffer.wrap(utf8Buffer, 0, utf8BufferIdx));
      utf8BufferIdx = 0;
    }
    return currentByteOffset;
  }

  @Override
  public void backup(int amount) {
    if (utf8BufferIdx >= amount) {
      utf8BufferIdx -= amount;
    } else if (utf8BufferIdx > 0) {
      int approxBytes = amount - utf8BufferIdx;
      utf8BufferIdx = 0;
      currentByteOffset -= approxBytes; // Won't work backing up over emoji.
    }
    super.backup(amount);
  }

  @Override
  @CanIgnoreReturnValue
  public char BeginToken() throws IOException {
    tokenBeginByteOffset = getCurrentByteOffset();
    return super.BeginToken();
  }

  /** Update line lengths for the current char. */
  private void updateLineLengthsForNewChar() {
    // Increase the size of the array, if necessary.
    if (line >= lineLengths.length) {
      lineLengths = Arrays.copyOf(lineLengths, Math.max(lineLengths.length, line) + 2048);
    }

    // Update the line lengths for the most recently parsed char.
    lineLengths[line] = Math.max(lineLengths[line], column);
  }

  /**
   * Gets the point just before the upcoming token. Note that the char stream's "current token" is
   * the upcoming token that hasn't actually been parsed yet. So for "foo bar baz", once the parser
   * has executed a line like "token foo = foo()", then current token would refer to bar and not
   * foo.
   */
  Point getPointJustBeforeNextToken() {
    // Point constructors here are ok to omit byteOffset because this utility method is only
    // used for calculating source locations of {if}, {else}, {case}, and {default}, none of which
    // are used in Kythe indexing, which is what uses the byte offsets.

    if (bufline[tokenBegin] <= 1 && bufcolumn[tokenBegin] <= 1) {
      throw new IllegalStateException("Can't get point before beginning of file");
    }

    // If bufline & bufcolumn still have the previous point stored, just use that.
    if (tokenBegin > 0) {
      return Point.create(bufline[tokenBegin - 1], bufcolumn[tokenBegin - 1]);
    }

    // Otherwise (if the buffer was reset), manually construct the previous point.

    // If the column is one, return the last point on the previous line.
    if (bufcolumn[tokenBegin] == 1) {
      return Point.create(bufline[tokenBegin] - 1, lineLengths[bufline[tokenBegin] - 1]);
    }

    // If the column is > 1, just subtract 1 from it.
    return Point.create(bufline[tokenBegin], bufcolumn[tokenBegin] - 1);
  }

  /** Constructor. */
  public SoySimpleCharStream(
      java.io.Reader dstream, int startline, int startcolumn, int buffersize) {
    super(new FullBuffer(dstream), startline, startcolumn, buffersize);
  }

  /** Constructor. */
  public SoySimpleCharStream(java.io.Reader dstream, int startline, int startcolumn) {
    this(dstream, startline, startcolumn, 4096);
  }

  /** Constructor. */
  public SoySimpleCharStream(java.io.Reader dstream) {
    this(dstream, 1, 1, 4096);
  }

  private static class FullBuffer extends Reader {
    private final Reader delegate;
    private final StringBuilder buffer = new StringBuilder();

    public FullBuffer(Reader delegate) {
      this.delegate = delegate;
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
      int nRead = delegate.read(cbuf, off, len);
      if (nRead != -1) {
        buffer.append(cbuf, off, nRead);
      }
      return nRead;
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }
  }
}
