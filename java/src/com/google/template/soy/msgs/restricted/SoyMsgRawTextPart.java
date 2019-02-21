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

package com.google.template.soy.msgs.restricted;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Utf8;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Represents a raw text string within a message (the stuff that translators change).
 *
 */
public abstract class SoyMsgRawTextPart extends SoyMsgPart {
  // TODO(lukes): when java9 is released, remove this.. the optimization performed by these
  // subclasses is getting added to the vm. see http://openjdk.java.net/jeps/254

  private static final int BYTES_PER_CHAR = 2;

  /** Returns a SoyMsgRawTextPart representing the specified raw text string. */
  public static SoyMsgRawTextPart of(String rawText) {
    int utf8Length = Utf8.encodedLength(rawText);

    // Determine whether UTF8 or UTF16 uses less memory, and choose between one of the two internal
    // implementations. char[] is preferred if the sizes are equal because it is faster to turn
    // back into a String. In a realistic application with 1 million messages in memory, using
    // UTF-8 saves about 35M, and dynamicaly switching encodings saves another 10M.

    // IMPORTANT! This choice is deterministic, so that for any particular input string the choice
    // of implementation class is the same. This ensures operations like equals() and hashCode()
    // do not have to decode the contents.

    if (utf8Length < rawText.length() * BYTES_PER_CHAR) {
      return new Utf8SoyMsgRawTextPart(rawText);
    } else {
      return new CharArraySoyMsgRawTextPart(rawText);
    }
  }

  /** Returns the raw text string. */
  public abstract String getRawText();

  @Override
  public final String toString() {
    return getRawText();
  }

  /** Constructor only intended to be used internally. */
  SoyMsgRawTextPart() {}

  /**
   * UTF-8 raw message text representation.
   *
   * <p>For most messages, UTF8 represents the string more compactly. For Latin strings, UTF8 will
   * always be half the size of UTF16.
   */
  @VisibleForTesting
  static final class Utf8SoyMsgRawTextPart extends SoyMsgRawTextPart {
    @SuppressWarnings("Immutable") // we never modify this array
    private final byte[] utf8Bytes;

    Utf8SoyMsgRawTextPart(String rawText) {
      this.utf8Bytes = rawText.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean equals(Object other) {
      // NOTE: Since message encoding is deterministic, we know the messages don't match if
      // the other one is encoded as chars.
      return other instanceof Utf8SoyMsgRawTextPart
          && Arrays.equals(utf8Bytes, ((Utf8SoyMsgRawTextPart) other).utf8Bytes);
    }

    @Override
    public int hashCode() {
      return getClass().hashCode() + Arrays.hashCode(utf8Bytes);
    }

    @Override
    public String getRawText() {
      return new String(utf8Bytes, UTF_8);
    }
  }

  /**
   * Character array representation.
   *
   * <p>Using a character array over String saves another 7M on a realistic application with 1
   * million messages in memory, by avoiding the overhead of the String object.
   */
  @VisibleForTesting
  static final class CharArraySoyMsgRawTextPart extends SoyMsgRawTextPart {
    @SuppressWarnings("Immutable") // we never modify this array
    private final char[] charArray;

    CharArraySoyMsgRawTextPart(String rawText) {
      this.charArray = rawText.toCharArray();
    }

    @Override
    public boolean equals(Object other) {
      // NOTE: Since message encoding is deterministic, we know the messages don't match if
      // the other one is encoded as UTF8.
      return other instanceof CharArraySoyMsgRawTextPart
          && Arrays.equals(charArray, ((CharArraySoyMsgRawTextPart) other).charArray);
    }

    @Override
    public int hashCode() {
      return getClass().hashCode() + Arrays.hashCode(charArray);
    }

    @Override
    public String getRawText() {
      return new String(charArray);
    }
  }
}
