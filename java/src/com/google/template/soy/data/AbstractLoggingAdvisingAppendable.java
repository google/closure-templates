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
package com.google.template.soy.data;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.ForOverride;
import java.io.IOException;

/**
 * A {@link LoggingAdvisingAppendable} that implements {@code logonly} behavior in response to log
 * statements
 *
 * <p>If a logging statement enables {@code logonly} it means that we will only collect logging
 * statements
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public abstract class AbstractLoggingAdvisingAppendable extends LoggingAdvisingAppendable {
  /**
   * The current number of calls to {@link #enterLoggableElement(LogStatement)} without a matching
   * {@link #exitLoggableElement()}
   */
  private int logElementDepth;

  /**
   * The {@link #logElementDepth} of the first {@link LogStatement#logOnly()} loggable element that
   * is currently active, or {@code -1} if there isn't one active.
   */
  private int firstLogonlyNode = -1;

  /** Subclasses can call this to detect if they are in the logOnly state. */
  private boolean isLogOnly() {
    return firstLogonlyNode != -1;
  }

  // covariant overrides

  @Override
  public final AbstractLoggingAdvisingAppendable append(CharSequence csq) throws IOException {
    if (!isLogOnly()) {
      doAppend(csq);
    }
    return this;
  }

  @Override
  public final AbstractLoggingAdvisingAppendable append(CharSequence csq, int start, int end)
      throws IOException {
    if (!isLogOnly()) {
      doAppend(csq, start, end);
    }
    return this;
  }

  @Override
  public final AbstractLoggingAdvisingAppendable append(char c) throws IOException {
    if (!isLogOnly()) {
      doAppend(c);
    }
    return this;
  }

  /** Called whenever a logging function is being rendered. */
  @Override
  public final AbstractLoggingAdvisingAppendable appendLoggingFunctionInvocation(
      LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers)
      throws IOException {
    if (!isLogOnly()) {
      doAppendLoggingFunctionInvocation(funCall, escapers);
    }
    return this;
  }

  @Override
  public final AbstractLoggingAdvisingAppendable enterLoggableElement(LogStatement statement) {
    int depth = logElementDepth;
    if (statement.logOnly() && !isLogOnly()) {
      firstLogonlyNode = depth;
    }
    depth++;
    if (depth < 0) {
      throw new IllegalStateException("overflowed logging depth");
    }
    logElementDepth = depth;
    doEnterLoggableElement(statement);
    return this;
  }

  @Override
  public final AbstractLoggingAdvisingAppendable exitLoggableElement() {
    int currentElementDepth = logElementDepth - 1;
    if (currentElementDepth < 0) {
      throw new IllegalStateException("log statements are unbalanced!");
    }
    // This means that we are exiting the node that first enabled logonly mode
    if (currentElementDepth == firstLogonlyNode) {
      firstLogonlyNode = -1;
    }
    logElementDepth = currentElementDepth;
    doExitLoggableElement();
    return this;
  }

  @ForOverride
  protected abstract void doAppend(CharSequence csq) throws IOException;

  @ForOverride
  protected abstract void doAppend(CharSequence csq, int start, int end) throws IOException;

  @ForOverride
  protected abstract void doAppend(char c) throws IOException;

  @ForOverride
  protected abstract void doEnterLoggableElement(LogStatement statement);

  @ForOverride
  protected abstract void doExitLoggableElement();

  @ForOverride
  protected abstract void doAppendLoggingFunctionInvocation(
      LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers)
      throws IOException;
}
