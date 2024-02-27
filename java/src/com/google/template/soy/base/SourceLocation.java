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
import com.google.common.base.Preconditions;
import com.google.common.collect.ComparisonChain;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.Immutable;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/** Describes a source location in a Soy input file. */
@ParametersAreNonnullByDefault
@Immutable
@CheckReturnValue
public final class SourceLocation implements Comparable<SourceLocation> {
  /** A file path or URI useful for error messages. */
  @Nonnull private final SourceFilePath filePath;

  private final Point begin;
  private final Point end;
  private final ByteSpan byteSpan;

  /**
   * A nullish source location.
   *
   * <p>This is useful for default initialization of field values or when dealing with situations
   * when you may or may not have a location. Obviously, associating real locations is always
   * preferred when possible.
   */
  public static final SourceLocation UNKNOWN =
      new SourceLocation(SourceFilePath.create("unknown", "unknown"));

  /**
   * This method ignores byte offsets and should only be used in contexts where byte offset is not
   * used (e.g. Formatter, tests). Prefer {@link SourceLocation#SourceLocation(SourceFilePath,
   * Point, Point)}.
   *
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
      SourceFilePath filePath, int beginLine, int beginColumn, int endLine, int endColumn) {
    this(filePath, Point.create(beginLine, beginColumn), Point.create(endLine, endColumn));
  }

  public SourceLocation(SourceFilePath filePath) {
    this(filePath, Point.UNKNOWN_POINT, Point.UNKNOWN_POINT);
  }

  public SourceLocation(SourceFilePath filePath, Point begin, Point end) {
    this(filePath, begin, end, ByteSpan.UNKNOWN);
  }

  public SourceLocation(SourceFilePath filePath, Point begin, Point end, ByteSpan byteSpan) {
    this.filePath = checkNotNull(filePath);
    this.begin = checkNotNull(begin);
    this.end = checkNotNull(end);
    this.byteSpan = checkNotNull(byteSpan);
    checkArgument(
        begin.isKnown() == end.isKnown(),
        "Either both the begin and end locations should be known, or neither should be. Got [%s,"
            + " %s]",
        begin,
        end);
    checkArgument(
        begin.compareTo(end) <= 0,
        "begin %s should be before end %s in file %s",
        begin,
        end,
        filePath);
  }

  /**
   * Returns a file path or URI useful for error messages. This should not be used to fetch content
   * from the file system.
   */
  @Nonnull
  public SourceFilePath getFilePath() {
    return filePath;
  }

  @Nullable
  public String getFileName() {
    if (UNKNOWN.equals(this)) {
      // This is to replicate old behavior where SoyFileNode#getFileName returns null when an
      // invalid SoyFileNode is created.
      return null;
    }
    return filePath.fileName();
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

  public ByteSpan getByteSpan() {
    return byteSpan;
  }

  /** Returns true if this location ends one character before the other location starts. */
  public boolean isJustBefore(SourceLocation that) {
    if (!this.filePath.equals(that.filePath)) {
      return false;
    }

    return this.getEndLine() == that.getBeginLine()
        && this.getEndColumn() + 1 == that.getBeginColumn();
  }

  public boolean isBefore(SourceLocation that) {
    if (!this.filePath.equals(that.filePath)) {
      return false;
    }
    return this.getEndPoint().isBefore(that.getBeginPoint());
  }

  /** True iff this location has valid begin and end locations. */
  public boolean isKnown() {
    // our ctor enforces that if begin is known then end is known, so we only need to check one.
    return begin.isKnown();
  }

  @Override
  public int compareTo(SourceLocation o) {
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
    String lineColumnString = toLineColumnString();
    return (lineColumnString == null)
        ? filePath.realPath()
        : String.format("%s:%s", filePath.realPath(), lineColumnString);
  }

  /**
   * Returns string representing the line:col range of the location, or null if line numbers are not
   * available.
   */
  @Nullable
  public String toLineColumnString() {
    return (begin.line() == -1)
        ? null
        : String.format("%s:%s-%s:%s", begin.line(), begin.column(), end.line(), end.column());
  }

  /** See {@link Point#offsetCols(int)} for a warning about this method. */
  public SourceLocation offsetStartCol(int offset) {
    return new SourceLocation(
        filePath, begin.offsetCols(offset), end, byteSpan.offsetStart(offset));
  }

  /** See {@link Point#offsetCols(int)} for a warning about this method. */
  public SourceLocation offsetEndCol(int offset) {
    return new SourceLocation(filePath, begin, end.offsetCols(offset), byteSpan.offsetEnd(offset));
  }

  public boolean isSingleLine() {
    return begin.line() == end.line();
  }

  public int getLength() {
    Preconditions.checkState(isSingleLine());
    return end.column() - begin.column() + 1;
  }

  /**
   * Returns a new SourceLocation that starts where this SourceLocation starts and ends where {@code
   * other} ends.
   */
  public SourceLocation extend(SourceLocation other) {
    if (!isKnown() || !other.isKnown()) {
      return UNKNOWN;
    }
    checkState(
        filePath.equals(other.filePath),
        "Mismatched files paths: %s and %s",
        filePath,
        other.filePath);
    return new SourceLocation(
        filePath, begin, other.end, ByteSpan.create(byteSpan.getStart(), other.byteSpan.getEnd()));
  }

  /**
   * Returns a new SourceLocation that starts where this SourceLocation starts and ends where {@code
   * other} ends.
   */
  public SourceLocation extend(Point other) {
    if (!isKnown() || !other.isKnown()) {
      return UNKNOWN;
    }
    return new SourceLocation(filePath, begin, other, ByteSpan.UNKNOWN);
  }

  /**
   * Creates a source location that fully spans the two source locations. They do not need to be
   * adjacent locations. You probably don't want to use this unless you're doing something in the
   * formatter with reordering.
   */
  public SourceLocation createSuperRangeWith(SourceLocation other) {
    Point begin = this.begin.isBefore(other.begin) ? this.begin : other.begin;
    Point end = this.end.isAfter(other.end) ? this.end : other.end;
    return new SourceLocation(filePath, begin, end, byteSpan.union(other.byteSpan));
  }

  /**
   * Returns a new SourceLocation that covers the union of the two points. If the two locations are
   * not adjacent or overlapping, throws an error.
   */
  public SourceLocation unionWith(SourceLocation other) {
    if (!isKnown() || !other.isKnown()) {
      return UNKNOWN;
    }
    checkState(
        filePath.equals(other.filePath),
        "Mismatched files paths: %s and %s",
        filePath,
        other.filePath);

    checkState(
        isAdjacentOrOverlappingWith(other),
        "Cannot compute union of nonadjacent source locations: %s and %s",
        this.asLineColumnRange(),
        other.asLineColumnRange());

    Point newBegin = begin.isBefore(other.getBeginPoint()) ? begin : other.getBeginPoint();
    Point newEnd = end.isAfter(other.getEndPoint()) ? end : other.getEndPoint();
    return new SourceLocation(filePath, newBegin, newEnd, byteSpan.union(other.byteSpan));
  }

  /** Returns whether the two source locations are adjacent or overlapping. */
  public boolean isAdjacentOrOverlappingWith(SourceLocation other) {
    Point lowerEndPoint = end.isBefore(other.getEndPoint()) ? end : other.getEndPoint();
    Point higherBeginPoint = begin.isAfter(other.getBeginPoint()) ? begin : other.getBeginPoint();

    SourceLocation locWithLowerEndPoint = end.isBefore(other.getEndPoint()) ? this : other;
    SourceLocation locWithHigherEndPoint = locWithLowerEndPoint.equals(this) ? other : this;

    return locWithLowerEndPoint.isJustBefore(locWithHigherEndPoint) // Adjacent
        || lowerEndPoint.equals(higherBeginPoint) // Contiguous
        || lowerEndPoint.isAfter(higherBeginPoint); // Overlapping (or one is a subset).
  }

  private String asLineColumnRange() {
    return getBeginLine() + ":" + getBeginColumn() + " - " + getEndLine() + ":" + getEndColumn();
  }

  /** Returns a new location that points to the first character of this location. */
  public SourceLocation getBeginLocation() {
    return begin.asLocation(filePath);
  }

  public Point getBeginPoint() {
    return begin;
  }

  /** Whether this source location fully contains the given {@code range}, inclusively. */
  public boolean fullyContainsRange(SourceLocation range) {
    // If either location is unknown, return false.
    if (!this.isKnown() || !range.isKnown()) {
      return false;
    }
    // This source location should start before or at the same point as the given range.
    boolean rangeStartsAfterOrAtBeginPoint = !this.begin.isAfter(range.begin);

    // This source location should end after or at the same point as the given range.
    boolean rangeEndsAfterOrAtEndPoint = !this.end.isBefore(range.end);

    return rangeStartsAfterOrAtBeginPoint && rangeEndsAfterOrAtEndPoint;
  }

  public Optional<SourceLocation> getOverlapWith(SourceLocation other) {
    Point lowerEndPoint = end.isBefore(other.getEndPoint()) ? end : other.getEndPoint();
    Point higherBeginPoint = begin.isAfter(other.getBeginPoint()) ? begin : other.getBeginPoint();

    if (!lowerEndPoint.isBefore(higherBeginPoint)) {
      return Optional.of(new SourceLocation(filePath, higherBeginPoint, lowerEndPoint));
    }
    return Optional.empty();
  }

  /** Returns a new location that points to the last character of this location. */
  public SourceLocation getEndLocation() {
    return end.asLocation(filePath);
  }

  public Point getEndPoint() {
    return end;
  }

  public boolean containsPoint(Point point) {
    return begin.compareTo(point) <= 0 && end.compareTo(point) >= 0;
  }

  /** Returns a new source location in the same file with unknown start and end points. */
  public SourceLocation clearRange() {
    return new SourceLocation(filePath);
  }

  /** A Point in a source file. */
  @AutoValue
  @Immutable
  public abstract static class Point implements Comparable<Point> {
    public static final Point UNKNOWN_POINT = new AutoValue_SourceLocation_Point(-1, -1);
    public static final Point FIRST_POINT = create(1, 1);

    public static Point create(int line, int column) {
      if (line == -1 && column == -1) {
        return UNKNOWN_POINT;
      }
      checkArgument(line > 0, "line must be positive: %s", line);
      checkArgument(column > 0, "column must be positive: %s", column);
      return new AutoValue_SourceLocation_Point(line, column);
    }

    public abstract int line();

    public abstract int column();

    public final boolean isKnown() {
      return !this.equals(UNKNOWN_POINT);
    }

    public final Point offsetCols(int byColumns) {
      if (!isKnown()) {
        return this;
      }
      return Point.create(line(), column() + byColumns);
    }

    public final SourceLocation asLocation(SourceFilePath filePath) {
      return new SourceLocation(filePath, this, this);
    }

    @Override
    public final int compareTo(Point o) {
      return ComparisonChain.start()
          .compare(line(), o.line())
          .compare(column(), o.column())
          .result();
    }

    public final boolean isBefore(Point o) {
      return compareTo(o) < 0;
    }

    public final boolean isAfter(Point o) {
      return compareTo(o) > 0;
    }

    @Override
    public final boolean equals(Object o) {
      if (!(o instanceof Point)) {
        return false;
      }
      return compareTo(((Point) o)) == 0;
    }

    @Override
    public final int hashCode() {
      return (line() * 31 * 31) + (column() * 31);
    }
  }

  /** A span of byte offsets in a file. */
  @AutoValue
  @Immutable
  public abstract static class ByteSpan {

    public static final ByteSpan UNKNOWN = new AutoValue_SourceLocation_ByteSpan(-1, -1);

    public static ByteSpan create(int start, int end) {
      if (start == -1 || end == -1) {
        return UNKNOWN;
      }
      return new AutoValue_SourceLocation_ByteSpan(start, end);
    }

    public abstract int getStart();

    public abstract int getEnd();

    public boolean isKnown() {
      return this != UNKNOWN;
    }

    public ByteSpan offsetStart(int offset) {
      if (!isKnown()) {
        return UNKNOWN;
      }
      return create(getStart() + offset, getEnd());
    }

    public ByteSpan offsetEnd(int offset) {
      if (!isKnown()) {
        return UNKNOWN;
      }
      return create(getStart(), getEnd() + offset);
    }

    public ByteSpan union(ByteSpan o) {
      if (!isKnown() || !o.isKnown()) {
        return UNKNOWN;
      }
      return create(Math.min(getStart(), o.getStart()), Math.max(getEnd(), o.getEnd()));
    }
  }
}
