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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;

/**
 * {@link ErrorReporter} implementation that throws an {@link AssertionError} whenever an error is
 * reported to it. This should only be used when no errors are expected. This is seldom desirable in
 * production code, but often desirable in tests, which should fail in the presence of any errors
 * that are not specifically checked for.
 *
 * <p>To write a test that does not have this exploding behavior (for example, a test that needs to
 * check the full list of errors encountered during compilation), pass a non-exploding ErrorReporter
 * instance to {@link com.google.template.soy.testing.SoyFileSetParserBuilder#errorReporter}.
 *
 * @author brndn@google.com (Brendan Linn)
 */
final class ExplodingErrorReporter extends ErrorReporter {
  static final ErrorReporter EXPLODING = new ExplodingErrorReporter(false);
  static final ErrorReporter EXPLODING_IGNORE_WARNINGS = new ExplodingErrorReporter(true);

  private final boolean ignoreWarnings;

  private ExplodingErrorReporter(boolean ignoreWarnings) {
    this.ignoreWarnings = ignoreWarnings;
  }

  @Override
  public void report(SourceLocation sourceLocation, SoyErrorKind error, Object... args) {
    checkNotNull(sourceLocation);
    throw new AssertionError(
        String.format("Unexpected error: %s at %s", error.format(args), sourceLocation));
  }

  @Override
  public void warn(SourceLocation sourceLocation, SoyErrorKind error, Object... args) {
    checkNotNull(sourceLocation);
    if (!ignoreWarnings) {
      throw new AssertionError(
          String.format("Unexpected warning: %s at %s", error.format(args), sourceLocation));
    }
  }

  @Override
  int getCurrentNumberOfErrors() {
    return 0;
  }

  @Override
  int getCurrentNumberOfReports() {
    return 0;
  }

  @Override
  public void copyTo(ErrorReporter other) {}

  @Override
  public ImmutableList<SoyError> getErrors() {
    return ImmutableList.of();
  }

  @Override
  public ImmutableList<SoyError> getWarnings() {
    return ImmutableList.of();
  }
}
