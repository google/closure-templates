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

import com.google.template.soy.base.SourceLocation;

/**
 * Collects errors during parsing.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public interface ErrorReporter {

  /**
   * Reports the given {@code error}, formatted according to {@code args} and associated with the
   * given {@code sourceLocation}.
   */
  void report(SourceLocation sourceLocation, SoyErrorKind error, Object... args);

  /**
   * Returns an opaque token (the checkpoint) that callers can later pass back into {@link
   * #errorsSince} to see if any errors have occurred in the interim.
   */
  Checkpoint checkpoint();

  /**
   * Returns true iff errors have occurred since {@code checkpoint} was obtained from {@link
   * #checkpoint}.
   *
   * <p>Useful for callers whose outputs are dependent on whether some code path resulted in new
   * errors (for example, returning an error node if parsing encountered errors).
   */
  boolean errorsSince(Checkpoint checkpoint);

  /**
   * Opaque token, used by {@link ErrorReporter#checkpoint} and {@link ErrorReporter#errorsSince}.
   */
  public interface Checkpoint {}
}
