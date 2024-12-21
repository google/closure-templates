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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.ForOverride;
import com.google.template.soy.base.SourceLocation;

/** Collects errors during parsing. */
public abstract class ErrorReporter {

  /**
   * An instance of an {@link ErrorReporter} that is bound to a specific {@link SourceLocation}.
   * Utility to reduce the number of parameters passed to shared utility functions.
   */
  public interface LocationBound {
    void report(SoyErrorKind error, Object... args);

    void warn(SoyErrorKind error, Object... args);
  }

  /** Creates a new ErrorReporter which can create source snippets from the given files. */
  public static ErrorReporter create() {
    return new ErrorReporterImpl();
  }

  public static ErrorReporter devnull() {
    return new ExplodingErrorReporter() {
      @Override
      public void report(SourceLocation sourceLocation, SoyErrorKind error, Object... args) {}

      @Override
      public void warn(SourceLocation sourceLocation, SoyErrorKind error, Object... args) {}
    };
  }

  /**
   * Returns an ErrorReporter that throws an assertion error on every error and warning.
   *
   * <p>Useful for tests.
   */
  public static ErrorReporter exploding() {
    return ExplodingErrorReporter.EXPLODING;
  }

  /**
   * Returns an ErrorReporter that throws an IllegalArgumentException on every error and warning.
   *
   * <p>Useful for tests.
   */
  public static ErrorReporter illegalArgumentExceptionExploding() {
    return IllegalArgumentExceptionErrorReporter.INSTANCE;
  }

  /** Returns an ErrorReporter that throws assertion error on every error but ignores warnings. */
  public static ErrorReporter explodeOnErrorsAndIgnoreWarnings() {
    return ExplodingErrorReporter.EXPLODING_IGNORE_WARNINGS;
  }

  public static ErrorReporter explodeOnErrorsAndIgnoreDeprecations() {
    return new ExplodingErrorReporter() {
      @Override
      public void warn(SourceLocation sourceLocation, SoyErrorKind error, Object... args) {
        if (!error.isDeprecation()) {
          super.warn(sourceLocation, error, args);
        }
      }
    };
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

  /** Returns a new error report bound to {@code sourceLocation}. */
  public LocationBound bind(SourceLocation sourceLocation) {
    return new LocationBoundImp(sourceLocation);
  }

  /**
   * Returns a new error report bound to {@code sourceLocation}. If sourceLocation is unknown then
   * no errors or warnings will be logged via the returned instance.
   */
  public LocationBound bindIgnoringUnknown(SourceLocation sourceLocation) {
    return sourceLocation.isKnown() ? new LocationBoundImp(sourceLocation) : NO_OP;
  }

  /**
   * Returns an opaque token (the checkpoint) that callers can later pass back into {@link
   * #errorsSince} to see if any errors have occurred in the interim.
   */
  public final Checkpoint checkpoint() {
    return new Checkpoint(this, getCurrentNumberOfReports(), getCurrentNumberOfErrors());
  }

  /**
   * Returns true iff errors have occurred since {@code checkpoint} was obtained from {@link
   * #checkpoint}.
   *
   * <p>Useful for callers whose outputs are dependent on whether some code path resulted in new
   * errors (for example, returning an error node if parsing encountered errors).
   */
  public final boolean errorsSince(Checkpoint checkpoint) {
    if (checkpoint.owner != this) {
      throw new IllegalArgumentException(
          "Can only call errorsSince on a Checkpoint instance that was returned from this same "
              + "reporter");
    }
    return getCurrentNumberOfErrors() > checkpoint.errorsSoFar;
  }

  public final ImmutableList<SoyError> getErrorsSince(Checkpoint checkpoint) {
    return getReportsSince(checkpoint).stream()
        .filter(e -> !e.isWarning())
        .collect(toImmutableList());
  }

  public final ImmutableList<SoyError> getReportsSince(Checkpoint checkpoint) {
    if (checkpoint.owner != this) {
      throw new IllegalArgumentException(
          "Can only call errorsSince on a Checkpoint instance that was returned from this same "
              + "reporter");
    }
    return getReports(checkpoint.reportsSoFar, getCurrentNumberOfReports());
  }

  /** Returns true if any errors have been reported. */
  public final boolean hasErrors() {
    return getCurrentNumberOfErrors() != 0;
  }

  /** Returns true if any errors or warnings have been reported. */
  public final boolean hasErrorsOrWarnings() {
    return getCurrentNumberOfReports() != 0;
  }

  /**
   * Returns the current number of reported errors. Useful for detecting if an error has been
   * reported.
   */
  @ForOverride
  int getCurrentNumberOfErrors() {
    return (int) getReports().stream().filter(r -> !r.isWarning()).count();
  }

  @ForOverride
  int getCurrentNumberOfReports() {
    return getReports().size();
  }

  /** Returns all the errors reported so far. */
  public abstract ImmutableList<SoyError> getReports();

  protected ImmutableList<SoyError> getReports(int from, int to) {
    return getReports().subList(from, to);
  }

  /** Returns all the errors reported so far. */
  public ImmutableList<SoyError> getErrors() {
    return getReports().stream().filter(r -> !r.isWarning()).collect(toImmutableList());
  }

  public Iterable<SoyError> throwableErrors(){

    if(this.hasErrors()){
      Iterable<SoyError> errors =
              Iterables.concat(this.getErrors(), this.getWarnings());
      return errors;
    }
    return  null;
  }

  /** Returns all the warnings reported so far. */
  public ImmutableList<SoyError> getWarnings() {
    return getReports().stream().filter(SoyError::isWarning).collect(toImmutableList());
  }

  /** Opaque token, used by {@link #checkpoint} and {@link #errorsSince}. */
  public static final class Checkpoint {
    private final ErrorReporter owner;
    private final int reportsSoFar;
    private final int errorsSoFar;

    private Checkpoint(ErrorReporter owner, int reportsSoFar, int errorsSoFar) {
      this.owner = owner;
      this.reportsSoFar = reportsSoFar;
      this.errorsSoFar = errorsSoFar;
    }
  }

  private class LocationBoundImp implements LocationBound {

    private final SourceLocation sourceLocation;

    public LocationBoundImp(SourceLocation sourceLocation) {
      this.sourceLocation = sourceLocation;
    }

    @Override
    public void report(SoyErrorKind error, Object... args) {
      ErrorReporter.this.report(sourceLocation, error, args);
    }

    @Override
    public void warn(SoyErrorKind error, Object... args) {
      ErrorReporter.this.warn(sourceLocation, error, args);
    }
  }

  private static final LocationBound NO_OP =
      new LocationBound() {
        @Override
        public void report(SoyErrorKind error, Object... args) {}

        @Override
        public void warn(SoyErrorKind error, Object... args) {}
      };
}
