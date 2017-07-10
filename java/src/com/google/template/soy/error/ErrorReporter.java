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
import com.google.errorprone.annotations.ForOverride;
import com.google.template.soy.base.SourceLocation;

/**
 * Collects errors during parsing.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public abstract class ErrorReporter {

  /**
   * Reports the given {@code error}, formatted according to {@code args} and associated with the
   * given {@code sourceLocation}.
   */
  public abstract void report(SourceLocation sourceLocation, SoyErrorKind error, Object... args);

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
