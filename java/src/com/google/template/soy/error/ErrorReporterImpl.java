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
import com.google.template.soy.base.SourceLocation;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple {@link com.google.template.soy.error.ErrorReporter} implementation.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class ErrorReporterImpl extends AbstractErrorReporter {
  private final List<SoyError> errors = new ArrayList<>();
  private final SoyError.Factory errorFactory;

  public ErrorReporterImpl(SoyError.Factory defaultFactory) {
    this.errorFactory = defaultFactory;
  }

  @Override
  public void report(SourceLocation sourceLocation, SoyErrorKind error, Object... args) {
    errors.add(errorFactory.create(sourceLocation, error, args));
  }

  /** Returns the full list of errors reported to this error reporter. */
  public Iterable<SoyError> getErrors() {
    return ImmutableList.copyOf(errors);
  }

  @Override
  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  @Override
  protected int getCurrentNumberOfErrors() {
    return errors.size();
  }
}
