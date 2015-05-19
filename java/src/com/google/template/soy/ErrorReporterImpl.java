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

package com.google.template.soy;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple {@link com.google.template.soy.error.ErrorReporter} implementation.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class ErrorReporterImpl implements ErrorReporter {
  private final List<SoySyntaxException> errors = new ArrayList<>();

  @Override
  public void report(SourceLocation sourceLocation, SoyError error, Object... args) {
    errors.add(SoySyntaxException.createWithMetaInfo(error.format(args), sourceLocation));
  }

  @Override
  public Checkpoint checkpoint() {
    return new CheckpointImpl(errors.size());
  }

  @Override
  public boolean errorsSince(Checkpoint checkpoint) {
    // Throws a ClassCastException if callers try to pass in a Checkpoint instance
    // that wasn't returned by checkpoint(). We could probably ensure this at compile time
    // with a bunch of generics, but it's not worth the effort.
    return errors.size() > ((CheckpointImpl) checkpoint).numErrors;
  }

  /** Returns the full list of errors reported to this error reporter. */
  ImmutableCollection<? extends SoySyntaxException> getErrors() {
    ImmutableCollection<? extends SoySyntaxException> copy = ImmutableList.copyOf(errors);
    // TODO(user): remove. ErrorReporter is currently injected as a singleton in SharedModule.
    // This is not a problem for JS and Python compilations, which are traditional short-lived
    // binaries, but it is very bad for local development servers that can compile Tofu multiple
    // times. Every non-initial call to compileToTofu returns all the errors reported by all
    // previous calls.
    // The right solution is to provide an appropriate custom scope for the error reporter,
    // or even better, remove the Guice binding entirely. For now, just clear the error list.
    errors.clear();
    return copy;
  }

  /**
   * If errors have been reported, throws a single {@link SoySyntaxException} containing
   * all of the errors as suppressed exceptions.
   *
   * <p>This should only be used for entry points that cannot be converted to pretty-print
   * the {@link SoyError}s directly (example: {@link SoyFileSet#compileToTofu()}).
   */
  void throwIfErrorsPresent() throws SoySyntaxException {
    if (!errors.isEmpty()) {
      SoySyntaxException combined = new SoySyntaxException("errors during Soy compilation");
      for (SoySyntaxException e : errors) {
        combined.addSuppressed(e);
      }
      errors.clear();
      throw combined;
    }
  }

  private static final class CheckpointImpl implements Checkpoint {
    private final int numErrors;

    private CheckpointImpl(int numErrors) {
      this.numErrors = numErrors;
    }
  }
}
