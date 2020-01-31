/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.error;

import static java.util.Comparator.comparing;

import com.google.auto.value.AutoValue;
import com.google.template.soy.base.SourceLocation;
import java.util.Optional;

/** A structured error object for reporting */
@AutoValue
public abstract class SoyError implements Comparable<SoyError> {

  static SoyError create(
      SourceLocation location,
      SoyErrorKind kind,
      String message,
      Optional<String> snippet,
      boolean isWarning) {
    return new AutoValue_SoyError(location, kind, message, snippet, isWarning);
  }

  SoyError() {} // package private to prevent external subclassing

  /** The location where the error occurred. */
  public abstract SourceLocation location();

  /** The error kind. For classification usecases. */
  public abstract SoyErrorKind errorKind();

  /**
   * The error message.
   *
   * <p>This does not contain location information. Use {@link #toString} for a formatted message.
   */
  public abstract String message();

  // Should be accessed via toString()
  abstract Optional<String> snippet();

  /** Whether or not this error should be considered a warning (i.e., don't fail the build). */
  public abstract boolean isWarning();

  /** The full formatted error. */
  @Override
  public final String toString() {
    return toStringInternal(true);
  }

  public String toStringWithoutSnippet() {
    return toStringInternal(false);
  }

  private String toStringInternal(boolean snippet) {
    return location().getFilePath()
        + ':'
        + location().getBeginLine()
        + ": "
        + (isWarning() ? "warning" : "error")
        + ": "
        + message()
        + (snippet ? "\n" + snippet().orElse("") : "");
  }

  @Override
  public int compareTo(SoyError o) {
    return comparing(SoyError::location).thenComparing(SoyError::message).compare(this, o);
  }
}
