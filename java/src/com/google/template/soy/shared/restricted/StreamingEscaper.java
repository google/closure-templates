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
package com.google.template.soy.shared.restricted;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingFunctionInvocation;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.shared.restricted.EscapingConventions.CrossLanguageStringXform;
import java.io.IOException;
import java.util.ArrayDeque;

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
      LoggingAdvisingAppendable delegate,
      CrossLanguageStringXform transform,
      ContentKind noOpForKind) {
    if (delegate instanceof StreamingEscaper) {
      StreamingEscaper delegateAsStreamingEscaper = (StreamingEscaper) delegate;
      if (delegateAsStreamingEscaper.transform == transform
          && delegateAsStreamingEscaper.noOpForKind == noOpForKind) {
        return delegateAsStreamingEscaper;
      }
    }
    return new StreamingEscaper(delegate, transform, noOpForKind);
  }

  private final LoggingAdvisingAppendable delegate;
  private final CrossLanguageStringXform transform;
  @LazyInit private Appendable escapedDelegate;
  private final ContentKind noOpForKind;
  // TODO(lukes):  this is sort of overkill, all we really need to know is a sequence of integers
  // which count the number of non-compatible 'enter' calls between compatible ones, then enter
  // and exit would be mostly increment and decrement instead of push and pop.  But it is a little
  // tricky to do. For now just use a stack and switch if the allocations/operations show up
  // in a profile.
  private final ArrayDeque<ContentKind> contentKinds = new ArrayDeque<>();
  private boolean noOpMode;

  private StreamingEscaper(
      LoggingAdvisingAppendable delegate,
      CrossLanguageStringXform transform,
      ContentKind noOpForKind) {
    this.delegate = checkNotNull(delegate);
    this.transform = checkNotNull(transform);
    this.noOpForKind = checkNotNull(noOpForKind);
  }

  @Override
  public LoggingAdvisingAppendable enterSanitizedContent(ContentKind kind) {
    contentKinds.push(kind);
    noOpMode = kind == noOpForKind;
    if (noOpMode) {
      // If we are in no-op mode then we should tell the underlying delegate that the given kind
      // is coming.  However, if we are in escaping mode we shouldn't, otherwise we might get double
      // escaped.
      delegate.enterSanitizedContent(kind);
    }
    return this;
  }

  @Override
  public LoggingAdvisingAppendable exitSanitizedContent() {
    if (noOpMode) {
      delegate.exitSanitizedContent();
    }
    contentKinds.pop();
    noOpMode = contentKinds.peek() == noOpForKind;
    return this;
  }

  @Override
  public boolean softLimitReached() {
    return delegate.softLimitReached();
  }

  @Override
  public LoggingAdvisingAppendable append(CharSequence csq) throws IOException {
    getAppendable().append(csq);
    return this;
  }

  @Override
  public LoggingAdvisingAppendable append(CharSequence csq, int start, int end) throws IOException {
    getAppendable().append(csq, start, end);
    return this;
  }

  @Override
  public LoggingAdvisingAppendable append(char c) throws IOException {
    getAppendable().append(c);
    return this;
  }

  @Override
  public LoggingAdvisingAppendable appendLoggingFunctionInvocation(
      LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers)
      throws IOException {
    if (noOpMode) {
      delegate.appendLoggingFunctionInvocation(funCall, escapers);
    } else {
      getEscapedDelegate().append(funCall.placeholderValue());
    }
    return this;
  }

  // TODO(lukes): We only pass these through if we are in a compatible content type.  This is sort
  // of confusing and may require revisiting in the future once we have more examples of how
  // logging and print directives will coincide.

  @Override
  public LoggingAdvisingAppendable enterLoggableElement(LogStatement statement) {
    if (noOpMode) {
      delegate.enterLoggableElement(statement);
    }
    return this;
  }

  @Override
  public LoggingAdvisingAppendable exitLoggableElement() {
    if (noOpMode) {
      delegate.exitLoggableElement();
    }
    return this;
  }

  private Appendable getAppendable() {
    return noOpMode ? delegate : getEscapedDelegate();
  }

  private Appendable getEscapedDelegate() {
    Appendable local = escapedDelegate;
    if (local == null) {
      local = escapedDelegate = transform.escape(delegate);
    }
    return local;
  }
}
