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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ComparisonChain;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Describes a source location in a Soy input file.
 *
 * @author brndn@google.com (Brendan Linn)
 */
@ParametersAreNonnullByDefault
public final class SourceLocation implements Comparable<SourceLocation> {

  /** A file path or URI useful for error messages. */
  @Nonnull private final String filePath;

  private final String fileName;

  private final Point begin;
  private final Point end;

  /**
   * A nullish source location.
   *
   * @deprecated There is no reason to use this other than laziness. Soy has complete source
   *     location information.
   */
  @Deprecated public static final SourceLocation UNKNOWN = new SourceLocation("unknown");

  /**
   * @param filePath A file path or URI useful for error messages.
   * @param beginLine The line number in the source file where this location begins (1-based), or -1
   *     if associated with the entire file instead of a line.
   * @param beginColumn The column number in the source file where this location begins (1-based),
   *     or -1 if associated with the entire file instead of a line.
   * @param endLine The line number in the source file where this location ends (1-based), or -1 if
   *     associated with the entire file instead of a line.
   * @param endColumn The column number in the source file where this location ends (1-based), or -1
   *     if associated with the entire file instead of a line.
   */
  public SourceLocation(
      String filePath, int beginLine, int beginColumn, int endLine, int endColumn) {
    this(filePath, Point.create(beginLine, beginColumn), Point.create(endLine, endColumn));
  }

  public SourceLocation(String filePath) {
    this(filePath, -1, -1, -1, -1);
  }

  public SourceLocation(String filePath, Point begin, Point end) {
    int lastBangIndex = filePath.lastIndexOf('!');
    if (lastBangIndex != -1) {
      // This is a resource in a JAR file. Only keep everything after the bang.
      filePath = filePath.substring(lastBangIndex + 1);
    }

    this.fileName = fileNameFromPath(filePath);
    this.filePath = filePath;
    this.begin = checkNotNull(begin);
    this.end = checkNotNull(end);
  }

  /** Extracts the file name from a path. */
  public static String fileNameFromPath(String filePath) {
    // TODO(lukes): consider using Java 7 File APIs here.
    int lastSlashIndex = CharMatcher.anyOf("/\\").lastIndexIn(filePath);
    if (lastSlashIndex != -1 && lastSlashIndex != filePath.length() - 1) {
      return filePath.substring(lastSlashIndex + 1);
    }
    return filePath;
  }

  /**
   * Returns a file path or URI useful for error messages. This should not be used to fetch content
   * from the file system.
   */
  @Nonnull
  public String getFilePath() {
    return filePath;
  }

  @Nullable
  public String getFileName() {
    if (UNKNOWN.equals(this)) {
      // This is to replicate old behavior where SoyFileNode#getFileName returns null when an
      // invalid SoyFileNode is created.
      return null;
    }
    return fileName;
  }

  /** Returns the line number in the source file where this location begins (1-based). */
  public int getBeginLine() {
    return begin.line();
  }

  /** Returns the column number in the source file where this location begins (1-based). */
  public int getBeginColumn() {
    return begin.column();
  }

  /** Returns the line number in the source file where this location ends (1-based). */
  public int getEndLine() {
    return end.line();
  }

  /** Returns the column number in the source file where this location ends (1-based). */
  public int getEndColumn() {
    return end.column();
  }

  /**
   * True iff this location is known, i.e. not the special value {@link #UNKNOWN}.
   *
   * @deprecated For the same reason that {@link #UNKNOWN} is.
   */
  @Deprecated
  public boolean isKnown() {
    return !this.equals(UNKNOWN);
  }

  @Override
  public int compareTo(SourceLocation o) {
    // TODO(user): use Comparator.comparing(...)
    return ComparisonChain.start()
        .compare(this.filePath, o.filePath)
        .compare(this.begin, o.begin)
        // These last two are unlikely to make a difference, but if they do it means we sort smaller
        // source locations first.
        .compare(this.end, o.end)
        .result();
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (!(o instanceof SourceLocation)) {
      return false;
    }
    SourceLocation that = (SourceLocation) o;
    return this.filePath.equals(that.filePath)
        && this.begin.equals(that.begin)
        && this.end.equals(that.end);
  }

  @Override
  public int hashCode() {
    return filePath.hashCode() + 31 * begin.hashCode() + 31 * 31 * end.hashCode();
  }

  @Override
  public String toString() {
    return begin.line() != -1 ? (filePath + ":" + begin.line() + ":" + begin.column()) : filePath;
  }

  public SourceLocation offsetStartCol(int offset) {
    return new SourceLocation(filePath, begin.offset(0, offset), end);
  }

  public SourceLocation offsetEndCol(int offset) {
    return new SourceLocation(filePath, begin, end.offset(0, offset));
  }

  /**
   * Returns a new SourceLocation that starts where this SourceLocation starts and ends where {@code
   * other} ends.
   */
  public SourceLocation extend(SourceLocation other) {
    checkState(
        filePath.equals(other.filePath),
        "Mismatched files paths: %s and %s",
        filePath,
        other.filePath);
    return new SourceLocation(filePath, begin, other.end);
  }

  /**
   * Returns a new SourceLocation that starts where this SourceLocation starts and ends {@code
   * lines} and {@code cols} further than where it ends.
   */
  public SourceLocation extend(int lines, int cols) {
    return new SourceLocation(filePath, begin, end.offset(lines, cols));
  }

  /** Returns a new location that points to the first character of this location. */
  public SourceLocation getBeginLocation() {
    return new SourceLocation(filePath, begin, begin);
  }

  public SourceLocation.Point getBeginPoint() {
    return begin;
  }

  /** Returns a new location that points to the last character of this location. */
  public SourceLocation getEndLocation() {
    return new SourceLocation(filePath, end, end);
  }

  public SourceLocation.Point getEndPoint() {
    return end;
  }

  /** A Point in a source file. */
  @AutoValue
  public abstract static class Point implements Comparable<Point> {
    public static final Point UNKNOWN_POINT = new AutoValue_SourceLocation_Point(-1, -1);

    public static Point create(int line, int column) {
      if (line == -1 && column == -1) {
        return UNKNOWN_POINT;
      }
      checkArgument(line > 0);
      checkArgument(column > 0);
      return new AutoValue_SourceLocation_Point(line, column);
    }

    public abstract int line();

    public abstract int column();

    public Point offset(int byLines, int byColumns) {
      if (line() == -1) {
        return this;
      }
      return Point.create(line() + byLines, column() + byColumns);
    }

    public SourceLocation asLocation(String filePath) {
      return new SourceLocation(filePath, this, this);
    }

    @Override
    public int compareTo(Point o) {
      return ComparisonChain.start()
          .compare(line(), o.line())
          .compare(column(), o.column())
          .result();
    }
  }
}
