/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.shared.internal.gencode;

import com.google.common.base.Preconditions;
import com.google.common.base.Utf8;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.base.SourceLocation.ByteSpan;
import com.google.template.soy.javagencode.KytheHelper;
import javax.annotation.Nullable;

/**
 * A wrapped StringBuilder used for building text with indented lines.
 *
 * <p>The maximum indent length is 24.
 */
public class IndentedLinesBuilder implements CharSequence {

  /** Constant string of 24 spaces (the maximum indent length). */
  private static final String SPACES = "                        ";

  /** Maximum indent length. */
  private static final int MAX_INDENT_LEN = 24;

  /** The underlying StringBuilder. */
  private final StringBuilder sb;

  /** The number of spaces between indent stops. */
  private final int indentIncrementLen;

  @Nullable private final KytheHelper kytheHelper;

  /** The current indent length. */
  private int indentLen;

  /** The current indent as a string of spaces. */
  private String indent;

  private int byteLength = 0;

  public IndentedLinesBuilder(@Nullable KytheHelper kytheHelper) {
    this.kytheHelper = kytheHelper;
    sb = new StringBuilder();
    indentIncrementLen = 2;
    indentLen = 0;
    indent = "";
  }

  @CanIgnoreReturnValue
  public IndentedLinesBuilder appendImputee(String s, @Nullable ByteSpan byteSpan) {
    int start = getByteLength();
    appendInternal(s);
    int end = getByteLength();
    if (kytheHelper != null && byteSpan != null && byteSpan.isKnown()) {
      kytheHelper.addKytheLinkTo(byteSpan.getStart(), byteSpan.getEnd(), start, end);
    }
    return this;
  }

  @CanIgnoreReturnValue
  private IndentedLinesBuilder appendInternal(String s) {
    sb.append(s);
    byteLength += Utf8.encodedLength(s);
    return this;
  }

  public int getByteLength() {
    return byteLength;
  }

  /** Returns the current indent length. */
  public int getCurrIndentLen() {
    return indentLen;
  }

  /** Increases the indent by one stop. */
  @CanIgnoreReturnValue
  public IndentedLinesBuilder increaseIndent() {
    return increaseIndent(1);
  }

  /** Increases the indent by the given number of stops. */
  @CanIgnoreReturnValue
  public IndentedLinesBuilder increaseIndent(int numStops) {
    indentLen += numStops * indentIncrementLen;
    Preconditions.checkState(0 <= indentLen && indentLen <= MAX_INDENT_LEN);
    indent = SPACES.substring(0, indentLen);
    return this;
  }

  /** Decreases the indent by one stop. */
  @CanIgnoreReturnValue
  public IndentedLinesBuilder decreaseIndent() {
    return decreaseIndent(1);
  }

  /** Decreases the indent by the given number of stops. */
  @CanIgnoreReturnValue
  public IndentedLinesBuilder decreaseIndent(int numStops) {
    indentLen -= numStops * indentIncrementLen;
    Preconditions.checkState(0 <= indentLen && indentLen <= MAX_INDENT_LEN);
    indent = SPACES.substring(0, indentLen);
    return this;
  }

  /**
   * Appends a line. The indent at the start is automatically added whenever the line is nonempty
   * (nonzero number of params). The newline char at the end is always added.
   *
   * @param parts The parts that make up the line.
   */
  @CanIgnoreReturnValue
  public IndentedLinesBuilder appendLine(Object... parts) {
    if (parts.length > 0) {
      appendInternal(indent);
    }
    appendParts(parts);
    appendInternal("\n");
    return this;
  }

  /**
   * Appends some parts to the current line.
   *
   * @param parts The parts to append.
   * @return This object.
   */
  @CanIgnoreReturnValue
  public IndentedLinesBuilder appendParts(Object... parts) {
    for (Object part : parts) {
      appendInternal(part.toString());
    }
    return this;
  }

  /**
   * Appends the current indent, then the given strings.
   *
   * @param parts The parts to append.
   * @return This object.
   */
  @CanIgnoreReturnValue
  public IndentedLinesBuilder appendLineStart(Object... parts) {
    appendInternal(indent);
    appendParts(parts);
    return this;
  }

  @CanIgnoreReturnValue
  public IndentedLinesBuilder appendLineMiddle(Object... parts) {
    return appendParts(parts);
  }

  /**
   * Appends the given strings, then a newline.
   *
   * @param parts The parts to append.
   * @return This object.
   */
  @CanIgnoreReturnValue
  public IndentedLinesBuilder appendLineEnd(Object... parts) {
    return appendParts(parts).appendInternal("\n");
  }

  /** Returns the current content as a string. */
  @Override
  public String toString() {
    return sb.toString();
  }

  // -----------------------------------------------------------------------------------------------
  // Methods for CharSequence interface.

  @Override
  public int length() {
    return sb.length();
  }

  @Override
  public char charAt(int index) {
    return sb.charAt(index);
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    return sb.subSequence(start, end);
  }
}
