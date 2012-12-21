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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Describes a line in a Soy input file.
 *
 * @author Mike Samuel
 */
@ParametersAreNonnullByDefault
public final class SourceLocation {


  /** A file path or URI useful for error messages. */
  private final @Nonnull String filePath;

  /** The line number in the source file (1-based), or 0 if associated with the entire file instead
   *  of a line. */
  private final int lineNumber;

  /** A nullish source location. */
  public static final SourceLocation UNKNOWN = new SourceLocation("unknown", 0);


  /**
   * @param filePath A file path or URI useful for error messages.
   * @param lineNumber The line number in the source file (1-based), or 0 if associated with the
   *     entire file instead of a line.
   */
  public SourceLocation(String filePath, int lineNumber) {

    int lastBangIndex = filePath.lastIndexOf('!');
    if (lastBangIndex != -1) {
      // This is a resource in a JAR file. Only keep everything after the bang.
      filePath = filePath.substring(lastBangIndex + 1);
    }

    this.filePath = filePath;
    this.lineNumber = lineNumber;
  }


  /**
   * Returns a file path or URI useful for error messages. This should not be used to fetch content
   *     from the file system.
   */
  public @Nonnull String getFilePath() {
    return filePath;
  }


  /**
   * Returns the line number in the source file (1-based), or 0 if associated with the entire file
   * instead of a line.
   */
  public int getLineNumber() {
    return lineNumber;
  }


  /**
   * True iff this location is known, i.e. not the special value {@link #UNKNOWN}.
   */
  public boolean isKnown() {
    return !this.equals(UNKNOWN);
  }


  @Override
  public boolean equals(@Nullable Object o) {
    if (!(o instanceof SourceLocation)) {
      return false;
    }
    SourceLocation that = (SourceLocation) o;
    return this.filePath.equals(that.filePath) && this.lineNumber == that.lineNumber;
  }

  @Override
  public int hashCode() {
    return filePath.hashCode() + 31 * lineNumber;
  }

  @Override public String toString() {
    return lineNumber != 0 ? filePath + ":" + lineNumber : filePath;
  }
}
