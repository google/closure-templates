/*
 * Copyright 2010 Google Inc.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.CharMatcher;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Describes a source location in a Soy input file.
 *
 * @author brndn@google.com (Brendan Linn)
 */
@ParametersAreNonnullByDefault
public class SourceLocation {

  /** A file path or URI useful for error messages. */
  @Nonnull private final String filePath;

  private final String fileName;

  private final int beginLine;
  private final int beginColumn;
  private final int endLine;
  private final int endColumn;

  /**
   * A nullish source location.
   * @deprecated There is no reason to use this other than laziness. Soy has complete source
   * location information.
   */
  @Deprecated
  public static final SourceLocation UNKNOWN = new SourceLocation("unknown");

  /**
   * @param filePath A file path or URI useful for error messages.
   * @param beginLine The line number in the source file where this location begins (1-based),
   *     or -1 if associated with the entire file instead of a line.
   * @param beginColumn The column number in the source file where this location begins (1-based),
   *     or -1 if associated with the entire file instead of a line.
   * @param endLine The line number in the source file where this location ends (1-based),
   *     or -1 if associated with the entire file instead of a line.
   * @param endColumn The column number in the source file where this location ends (1-based),
   *     or -1 if associated with the entire file instead of a line.
   */
  public SourceLocation(
      String filePath, int beginLine, int beginColumn, int endLine, int endColumn) {
    checkArgument(beginLine > 0 || beginLine == -1);
    checkArgument(beginColumn > 0 || beginColumn == -1);
    checkArgument(endLine > 0 || endLine == -1);
    checkArgument(endColumn > 0 || endColumn == -1);

    int lastBangIndex = filePath.lastIndexOf('!');
    if (lastBangIndex != -1) {
      // This is a resource in a JAR file. Only keep everything after the bang.
      filePath = filePath.substring(lastBangIndex + 1);
    }

    // TODO(lukes): consider using Java 7 File APIs here.
    int lastSlashIndex = CharMatcher.anyOf("/\\").lastIndexIn(filePath);
    if (lastSlashIndex != -1 && lastSlashIndex != filePath.length() - 1) {
      this.fileName = filePath.substring(lastSlashIndex + 1);
    } else {
      this.fileName = filePath;
    }

    this.filePath = filePath;
    this.beginLine = beginLine;
    this.beginColumn = beginColumn;
    this.endLine = endLine;
    this.endColumn = endColumn;
  }

  public SourceLocation(String filePath) {
    this(filePath, -1, -1, -1, -1);
  }

  /**
   * Returns a file path or URI useful for error messages. This should not be used to fetch content
   *     from the file system.
   */
  @Nonnull public String getFilePath() {
    return filePath;
  }

  @Nullable public String getFileName() {
    if (UNKNOWN.equals(this)) {
      // This is to replicate old behavior where SoyFileNode#getFileName returns null when an
      // invalid SoyFileNode is created.
      return null;
    }
    return fileName;
  }

  /**
   * Returns the line number in the source file where this location begins (1-based).
   * TODO(brndn): rename this to getBeginLine.
   */
  public int getLineNumber() {
    return beginLine;
  }

  /**
   * Returns the column number in the source file where this location begins (1-based).
   */
  public int getBeginColumn() {
    return beginColumn;
  }

  /**
   * Returns the line number in the source file where this location ends (1-based).
   */
  public int getEndLine() {
    return endLine;
  }

  /**
   * Returns the column number in the source file where this location ends (1-based).
   */
  public int getEndColumn() {
    return endColumn;
  }

  /**
   * True iff this location is known, i.e. not the special value {@link #UNKNOWN}.
   * @deprecated For the same reason that {@link #UNKNOWN} is.
   */
  @Deprecated
  public boolean isKnown() {
    return !this.equals(UNKNOWN);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (!(o instanceof SourceLocation)) {
      return false;
    }
    SourceLocation that = (SourceLocation) o;
    return this.filePath.equals(that.filePath)
        && this.beginLine == that.beginLine
        && this.beginColumn == that.beginColumn
        && this.endLine == that.endLine
        && this.endColumn == that.endColumn;
  }

  @Override
  public int hashCode() {
    return filePath.hashCode() + 31 * beginLine;
  }

  @Override public String toString() {
    return beginLine != -1
        ? (filePath + ":" + beginLine + ":" + beginColumn)
        : filePath;
  }

  /**
   * Returns a new SourceLocation that starts where this SourceLocation starts
   * and ends where {@code other} ends.
   */
  public SourceLocation extend(SourceLocation other) {
    checkState(filePath.equals(other.filePath),
        "Mismatched files paths: %s and %s", filePath, other.filePath);
    return new SourceLocation(filePath, beginLine, beginColumn, other.endLine, other.endColumn);
  }
}
