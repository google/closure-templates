/*
 * Copyright 2019 Google Inc.
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
 * An unrecoverable exception in the Soy compiler. Reports parse errors found before the compiler
 * failed.
 */
public final class SoyInternalCompilerException extends RuntimeException {
  private final ImmutableList<SoyError> errors;

  public SoyInternalCompilerException(Iterable<SoyError> errors, Throwable cause) {
    super(cause);
    this.errors = ImmutableList.sortedCopyOf(errors);
    checkArgument(!this.errors.isEmpty());
  }

  @Override
  public String getMessage() {
    return "Unrecoverable internal Soy error. Prior to failure found "
        + SoyErrors.formatErrors(errors);
  }
}
