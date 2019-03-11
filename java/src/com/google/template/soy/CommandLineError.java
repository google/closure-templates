/*
 * Copyright 2017 Google Inc.
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

/**
 * An error that can be thrown to abort compilation.
 *
 * <p>Normal compilation errors should be signaled via the ErrorReporter/SoyCompilationException
 * system. This is for situations where the compilation is invalid due to a bad flag, missing file,
 * or other similar configuration issue.
 */
final class CommandLineError extends Error {
  /** An error to print directly to the user when failing the compile. */
  CommandLineError(String msg) {
    super(msg);
  }

  CommandLineError(String msg, Throwable t) {
    super(msg, t);
  }

  // there is rarely a point in showing the stack trace since this is at the root of the compiler
  // if needed for debugging this can be deleted.
  @Override
  public synchronized Throwable fillInStackTrace() {
    return this; // no stack trace
  }

  @SuppressWarnings("OverrideThrowableToString") // we want to override the default formatting.
  @Override
  public String toString() {
    return "error: " + getMessage();
  }
}
