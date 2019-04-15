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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.ForOverride;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.SoyFileSupplier;
import java.util.Map;

/**
 * Collects errors during parsing.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public abstract class ErrorReporter {

  /** Creates a new ErrorReporter which can create source snippets from the given files. */
  public static ErrorReporter create(Map<String, SoyFileSupplier> filePathsToSuppliers) {
    return new ErrorReporterImpl(ImmutableMap.copyOf(filePathsToSuppliers));
  }

  /** Creates a new ErrorReporter suitable for asserting on messages in tests. */
  public static ErrorReporter createForTest() {
    return new ErrorReporterImpl(ImmutableMap.of());
  }

  /**
   * Returns an ErrorReporter that throws an assertion error on every error and warning.
   *
   * <p>Useful for tests.
   */
  public static ErrorReporter exploding() {
    return ExplodingErrorReporter.EXPLODING;
  }

  /** Returns an ErrorReporter that throws assertion error on every error but ignores warnings. */
  public static ErrorReporter explodeOnErrorsAndIgnoreWarnings() {
    return ExplodingErrorReporter.EXPLODING_IGNORE_WARNINGS;
  }

  /**
   * Reports the given {@code error}, formatted according to {@code args} and associated with the
   * given {@code sourceLocation}.
   */
  public abstract void report(SourceLocation sourceLocation, SoyErrorKind error, Object... args);

  /**
   * Reports a warning.
   *
   * <p>The formatting is identical to {@link #report}, but it will not cause the build to fail.
   * This should be used sparingly, the best usecase is to add warnings as part of a migration to
   * stop temporary backsliding.
   */
  public abstract void warn(SourceLocation sourceLocation, SoyErrorKind error, Object... args);

  /** Copies the errors from one error reprorter to another one. */
  public abstract void copyTo(ErrorReporter other);

  /**
   * Returns an opaque token (the checkpoint) that callers can later pass back into {@link
   * #errorsSince} to see if any errors have occurred in the interim.
   */
  public final Checkpoint checkpoint() {
    return new Checkpoint(this, getCurrentNumberOfErrors());
  }

  /**
   * Returns true iff errors have occurred since {@code checkpoint} was obtained from {@link
   * #checkpoint}.
   *
   * <p>Useful for callers whose outputs are dependent on whether some code path resulted in new
   * errors (for example, returning an error node if parsing encountered errors).
   */
  public final boolean errorsSince(Checkpoint checkpoint) {
    Checkpoint impl = checkpoint;
    if (impl.owner != this) {
      throw new IllegalArgumentException(
          "Can only call errorsSince on a Checkpoint instance that was returned from this same "
              + "reporter");
    }
    return getCurrentNumberOfErrors() > impl.errorsSoFar;
  }

  /** Returns true if any errors have been reported. */
  public final boolean hasErrors() {
    return getCurrentNumberOfErrors() != 0;
  }

  /**
   * Returns the current number of reported errors. Useful for detecting if an error has been
   * reported.
   */
  @ForOverride
  abstract int getCurrentNumberOfErrors();

  /** Returns all the errors reported so far. */
  public abstract ImmutableList<SoyError> getErrors();

  /** Returns all the warnings reported so far. */
  public abstract ImmutableList<SoyError> getWarnings();

  /** Opaque token, used by {@link #checkpoint} and {@link #errorsSince}. */
  public static final class Checkpoint {
    private final ErrorReporter owner;
    private final int errorsSoFar;

    private Checkpoint(ErrorReporter owner, int errorsSoFar) {
      this.owner = owner;
      this.errorsSoFar = errorsSoFar;
    }
  }
}
