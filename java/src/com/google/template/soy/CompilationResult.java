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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.error.ErrorPrettyPrinter;

import java.io.PrintStream;

/**
 * Container for results associated with a Soy compilation.
 *
 * TODO(brndn): consider adding state for the compiled files.
 * Currently, {@link SoyFileSet#compileToJsSrcFiles} compiles but doesn't return them.
 *
 * @author brndn@google.com (Brendan Linn)
 */
final class CompilationResult {

  private final ImmutableCollection<? extends SoySyntaxException> errors;
  private final ErrorPrettyPrinter prettyPrinter;

  CompilationResult(
      ImmutableCollection<? extends SoySyntaxException> errors,
      ErrorPrettyPrinter prettyPrinter) {
    this.errors = errors;
    this.prettyPrinter = prettyPrinter;
  }

  boolean isSuccess() {
    return errors.isEmpty();
  }
  
  void checkSuccessful() {
    if (!errors.isEmpty()) {
      SoySyntaxException combined = new SoySyntaxException("errors during Soy compilation");
      for (SoySyntaxException e : errors) {
        combined.addSuppressed(e);
      }
      throw combined;
    }
  }

  void printErrors(PrintStream out) {
    Preconditions.checkState(!isSuccess());
    for (SoySyntaxException e : errors) {
      prettyPrinter.print(e, out);
    }
  }
}
