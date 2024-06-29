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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;

/**
 * Reports on all Soy compilation errors and allows for programmatic inspection via {@link
 * #getErrors()}.
 */
public final class SoyCompilationException extends RuntimeException {
  private final ImmutableList<SoyError> errors;
  private final ErrorFormatter errorFormatter;

  public SoyCompilationException(Iterable<SoyError> errors, ErrorFormatter errorFormatter) {
    this.errors = ImmutableList.sortedCopyOf(errors);
    this.errorFormatter = errorFormatter;
    checkArgument(!this.errors.isEmpty());
  }

  /** Returns the list of errors in sorted order. */
  public ImmutableList<SoyError> getErrors() {
    return errors;
  }

  @Override
  public String getMessage() {
    return SoyErrors.formatErrors(errors, errorFormatter);
  }
}
