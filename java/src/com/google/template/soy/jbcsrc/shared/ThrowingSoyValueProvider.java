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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.GoogleLogger;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.jbcsrc.api.RenderResult;

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

  @VisibleForTesting
  static final class DeferredException extends RuntimeException {
    DeferredException(Throwable t) {
      super(t);
    }
  }

  private final Throwable t;
  private boolean suppressLogging;

  ThrowingSoyValueProvider(Throwable t) {
    this.t = t;
    // If the original exception was a DeferredException, then we don't need to log it again.
    this.suppressLogging = t instanceof DeferredException;
  }

  public void maybeLog() {
    if (!suppressLogging) {
      suppressLogging = true;
      logger.atSevere().withCause(t).log(
          "Failed optimistic evaluation during rendering, this will soon become an error");
    }
  }

  @Override
  public SoyValue resolve() {
    suppressLogging = true;
    throw new DeferredException(t);
  }

  @Override
  public RenderResult status() {
    return RenderResult.done();
  }

  @Override
  public RenderResult renderAndResolve(LoggingAdvisingAppendable appendable) {
    suppressLogging = true;
    throw new DeferredException(t);
  }
}
