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

package com.google.template.soy.base;

import com.google.common.base.Preconditions;


/**
 * A wrapped StringBuilder used for building text with indented lines.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> The maximum indent length is 24.
 *
 */
public class IndentedLinesBuilder implements CharSequence, Appendable {


  /** Constant string of 24 spaces (the maximum indent length). */
  private static final String SPACES = "                        ";

  /** Maximum indent length. */
  private static final int MAX_INDENT_LEN = 24;


  /** The underlying StringBuilder. */
  private final StringBuilder sb;

  /** The number of spaces between indent stops. */
  private final int indentIncrementLen;

  /** The current indent length. */
  private int indentLen;

  /** The current indent as a string of spaces. */
  private String indent;


  /**
   * Constructor with initial indent length of 0.
   * @param indentIncrementLen The number of spaces between indent stops.
   */
  public IndentedLinesBuilder(int indentIncrementLen) {
    sb = new StringBuilder();
    this.indentIncrementLen = indentIncrementLen;
    indentLen = 0;
    indent = "";
  }


  /**
   * Constructor with a specified initial indent length.
   * @param indentIncrementLen The number of spaces between indent stops.
   * @param initialIndentLen The inital indent length.
   */
  public IndentedLinesBuilder(int indentIncrementLen, int initialIndentLen) {
    sb = new StringBuilder();
    this.indentIncrementLen = indentIncrementLen;
    indentLen = initialIndentLen;
    Preconditions.checkState(0 <= indentLen && indentLen <= MAX_INDENT_LEN);
    indent = SPACES.substring(0, indentLen);
  }


  /**
   * Gets the internal StringBuilder for this instance. You may directly add content to it as long
   * as you only add complete lines (i.e. the last character you add must be '\n').
   */
  public StringBuilder sb() {
    return sb;
  }

  /**
   * Returns the number of spaces between indent stops.
   */
  public int getIndentIncrementLen() {
    return indentIncrementLen;
  }

  /**
   * Returns the current indent length.
   */
  public int getCurrIndentLen() {
    return indentLen;
  }


  /**
   * Increases the indent by one stop.
   */
  public void increaseIndent() {
    increaseIndent(1);
  }


  /**
   * Increases the indent by the given number of stops.
   */
  public void increaseIndent(int numStops) {
    indentLen += numStops * indentIncrementLen;
    Preconditions.checkState(0 <= indentLen && indentLen <= MAX_INDENT_LEN);
    indent = SPACES.substring(0, indentLen);
  }


  /**
   * Decreases the indent by one stop.
   */
  public void decreaseIndent() {
    decreaseIndent(1);
  }


  /**
   * Decreases the indent by the given number of stops.
   */
  public void decreaseIndent(int numStops) {
    indentLen -= numStops * indentIncrementLen;
    Preconditions.checkState(0 <= indentLen && indentLen <= MAX_INDENT_LEN);
    indent = SPACES.substring(0, indentLen);
  }


  /**
   * Sets the indent length.
   * @param indentLen The new indent length.
   */
  public void setIndentLen(int indentLen) {
    this.indentLen = indentLen;
    Preconditions.checkState(0 <= indentLen && indentLen <= MAX_INDENT_LEN);
    indent = SPACES.substring(0, indentLen);
  }


  /**
   * Appends a line. The indent at the start is automatically added whenever the line is nonempty
   * (nonzero number of params). The newline char at the end is always added.
   * @param parts The parts that make up the line.
   */
  public void appendLine(String... parts) {
    if (parts.length > 0) {
      sb.append(indent);
    }
    appendParts(parts);
    appendLineEnd();
  }


  /**
   * Appends some parts to the current line.
   * @param parts The parts to append.
   * @return This object.
   */
  public IndentedLinesBuilder appendParts(String... parts) {
    for (String part : parts) {
      sb.append(part);
    }
    return this;
  }


  /**
   * Appends a line. The indent at the start is automatically added whenever the line is nonempty
   * (nonzero number of params). The newline char at the end is always added.
   * @param parts The parts that make up the line.
   */
  public void appendLine(Object... parts) {
    if (parts.length > 0) {
      sb.append(indent);
    }
    appendParts(parts);
    appendLineEnd();
  }


  /**
   * Appends some parts to the current line.
   * @param parts The parts to append.
   * @return This object.
   */
  public IndentedLinesBuilder appendParts(Object... parts) {
    for (Object part : parts) {
      sb.append(part);
    }
    return this;
  }


  /**
   * Appends the start-of-line indent (assuming the line will be nonempty).
   * @return This object.
   */
  public IndentedLinesBuilder appendIndent() {
    sb.append(indent);
    return this;
  }


  /**
   * Appends the end-of-line terminator.
   */
  public void appendLineEnd() {
    sb.append('\n');
  }


  /**
   * Returns the current content as a string.
   */
  @Override public String toString() {
    return sb.toString();
  }


  // -----------------------------------------------------------------------------------------------
  // Methods for CharSequence and Appendable interfaces.


  @Override public int length() {
    return sb.length();
  }

  @Override public char charAt(int index) {
    return sb.charAt(index);
  }

  @Override public CharSequence subSequence(int start, int end) {
    return sb.subSequence(start, end);
  }

  @Override public IndentedLinesBuilder append(CharSequence csq) {
    sb.append(csq);
    return this;
  }

  @Override public IndentedLinesBuilder append(CharSequence csq, int start, int end) {
    sb.append(csq, start, end);
    return this;
  }

  @Override public IndentedLinesBuilder append(char c) {
    sb.append(c);
    return this;
  }

}
