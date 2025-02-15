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

package com.google.template.soy.shared.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingFunctionInvocation;
import com.google.template.soy.shared.internal.EscapingConventions.CrossLanguageStringXform;
import java.io.IOException;
import java.util.function.Function;

/**
 * A StreamingEscaper is a decorator for a {@link LoggingAdvisingAppendable} that applies escaping
 * logic to untrusted content.
 */
public final class StreamingEscaper extends LoggingAdvisingAppendable {
  /**
   * Creates a streaming escaper, or returns the delegate if it is already escaping with the same
   * settings.
   */
  public static StreamingEscaper create(
      LoggingAdvisingAppendable delegate, CrossLanguageStringXform transform) {
    if (delegate instanceof StreamingEscaper) {
      StreamingEscaper delegateAsStreamingEscaper = (StreamingEscaper) delegate;
      if (delegateAsStreamingEscaper.transform == transform) {
        return delegateAsStreamingEscaper;
      }
    }
    return new StreamingEscaper(delegate, transform);
  }

  private final LoggingAdvisingAppendable delegate;
  private final CrossLanguageStringXform transform;

  private StreamingEscaper(LoggingAdvisingAppendable delegate, CrossLanguageStringXform transform) {
    this.delegate = checkNotNull(delegate);
    this.transform = checkNotNull(transform);
  }

  // Note we never propagate calls to setSanitizedContentKind to the delegate.  This is because
  // all content is being escaped and thus it is by definition compatible with the surrounding
  // content.

  @CanIgnoreReturnValue
  @Override
  public LoggingAdvisingAppendable append(CharSequence csq) throws IOException {
    transform.escapeOnto(csq, delegate);
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public LoggingAdvisingAppendable append(CharSequence csq, int start, int end) throws IOException {
    transform.escapeOnto(csq, delegate, start, end);
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public LoggingAdvisingAppendable append(char c) throws IOException {
    transform.escapeOnto(c, delegate);
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public LoggingAdvisingAppendable appendLoggingFunctionInvocation(
      LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers)
      throws IOException {
    return append(escapePlaceholder(funCall.placeholderValue(), escapers));
  }

  @Override
  public boolean softLimitReached() {
    return delegate.softLimitReached();
  }

  @Override
  public LoggingAdvisingAppendable enterLoggableElement(LogStatement statement) {
    return this;
  }

  @Override
  public LoggingAdvisingAppendable exitLoggableElement() {
    return this;
  }

  @Override
  public void flushBuffers(int depth) throws IOException {
    if (depth > 0) {
      delegate.flushBuffers(depth - 1);
    }
  }
}
