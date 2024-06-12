/*
 * Copyright 2024 Google Inc.
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
package com.google.template.soy.jbcsrc.shared;

import com.google.common.flogger.GoogleLogger;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.jbcsrc.api.RenderResult;
import java.util.Set;

/**
 * A provider that always throws an exception.
 *
 * <p>This is constructed when an optimistic evaluation fails with an exception. By capturing the
 * exception and wrapping it here we can preserve the same error semantics as when we were always
 * lazily evaluating.
 *
 * <p>TODO(b/289390227): This is a temporary hack to preserve the error semantics. We should
 * probably just throw a SoyDataException directly in the evaluator.
 */
final class ThrowingSoyValueProvider implements SoyValueProvider {
  private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

  private static final class SuppressedDeferredException extends RuntimeException {
    SuppressedDeferredException(Throwable t) {
      super(
          "Failed optimistic evaluation during rendering, this will soon become an error",
          t,
          /* enableSuppression= */ false,
          // This means there will be no stack trace for this throwable.
          /* writableStackTrace= */ false);
      ;
    }
  }

  private final Throwable deferredError;

  ThrowingSoyValueProvider(Throwable t) {
    this.deferredError = t;
  }

  /** Logs the exception if it hasn't already been logged. */
  void maybeLog(Set<Throwable> alreadyLogged) {
    if (alreadyLogged.add(deferredError)) {
      logger.atSevere().withCause(deferredError).log(
          "Failed optimistic evaluation during rendering, this will soon become an error");
    }
  }

  /** Adds the original exception as a suppressed exception if it hasn't already been suppressed. */
  void maybeSuppressOnto(Throwable t, Set<Throwable> alreadySuppressed) {
    if (alreadySuppressed.add(deferredError)) {
      t.addSuppressed(new SuppressedDeferredException(this.deferredError));
    }
  }

  @Override
  public SoyValue resolve() {
    throw sneakyDropCheckedCast(deferredError);
  }

  @Override
  public RenderResult status() {
    return RenderResult.done();
  }

  @Override
  public RenderResult renderAndResolve(LoggingAdvisingAppendable appendable) {
    throw sneakyDropCheckedCast(deferredError);
  }

  // The `throws` class tricks Java type inference into deciding that E must be some subtype of
  // RuntimeException but because the cast is unchecked it doesn't check.  So the compiler cannot
  // tell that this might be a checked exception.
  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals", "CheckedExceptionNotThrown"})
  private static <E extends Throwable> E sneakyDropCheckedCast(Throwable e) throws E {
    return (E) e;
  }
}
