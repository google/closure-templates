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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ComparisonChain;
import com.google.template.soy.base.SourceLocation;

/** A structured error object for reporting */
@AutoValue
public abstract class SoyError implements Comparable<SoyError> {
  /** A factory for constructing Errors. Typically this is used to apply formatting. */
  public interface Factory {
    SoyError create(SourceLocation location, SoyErrorKind kind, Object... args);
  }

  /** provides a default implementation for tests. */
  public static final Factory DEFAULT_FACTORY =
      new Factory() {
        @Override
        public SoyError create(SourceLocation location, SoyErrorKind kind, Object... args) {
          checkNotNull(location);
          String message = kind.format(args);
          return createError(location, kind, message, location + ": error: " + message);
        }
      };

  static SoyError createError(
      SourceLocation location, SoyErrorKind kind, String message, String formattedError) {
    return new AutoValue_SoyError(location, kind, message, formattedError);
  }

  SoyError() {} // package private to prevent external subclassing

  /** The location where the error occured. */
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
  abstract String formattedMessage();

  /** The full formatted error. */
  @Override
  public String toString() {
    return formattedMessage();
  }

  @Override
  public int compareTo(SoyError o) {
    // TODO(user): use Comparator.comparing(...)
    return ComparisonChain.start()
        .compare(location(), o.location())
        .compare(message(), o.message())
        .result();
  }
}
