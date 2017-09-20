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

/**
 * A StreamingEscaper is a decorator for a {@link LoggingAdvisingAppendable} that applies escaping
 * logic to untrusted content.
 */
public abstract class StreamingEscaper extends LoggingAdvisingAppendable {
  
  /**
   * Creates a streaming escaper, or returns the delegate if it is already escaping with the same
   * settings.
   */
  public static StreamingEscaper create(
      LoggingAdvisingAppendable delegate, CrossLanguageStringXform transform) {
    if (delegate instanceof SimpleStreamingEscaper) {
      SimpleStreamingEscaper delegateAsStreamingEscaper = (SimpleStreamingEscaper) delegate;
      if (delegateAsStreamingEscaper.transform == transform) {
        return delegateAsStreamingEscaper;
      }
    }
    return new SimpleStreamingEscaper(delegate, transform);
  }

  /**
   * Creates a streaming escaper, or returns the delegate if it is already escaping with the same
   * settings.
   */
  public static StreamingEscaper create(
      LoggingAdvisingAppendable delegate,
      CrossLanguageStringXform transform,
      ContentKind noOpForKind) {
    if (delegate instanceof ContextSensitiveStreamingEscaper) {
      ContextSensitiveStreamingEscaper delegateAsStreamingEscaper =
          (ContextSensitiveStreamingEscaper) delegate;
      if (delegateAsStreamingEscaper.transform == transform
          && delegateAsStreamingEscaper.noOpForKind == noOpForKind) {
        return delegateAsStreamingEscaper;
      }
    }
    return new ContextSensitiveStreamingEscaper(delegate, transform, noOpForKind);
  }

  protected final LoggingAdvisingAppendable delegate;
  protected final CrossLanguageStringXform transform;

  private StreamingEscaper(LoggingAdvisingAppendable delegate, CrossLanguageStringXform transform) {
    this.delegate = checkNotNull(delegate);
    this.transform = checkNotNull(transform);
  }

  // Note we never propagate calls to enter/exitSanitizedContent to the delegate.  This is because
  // all content is being escaped and thus it is by definition compatible with the surrounding
  // content.

  @Override
  public final LoggingAdvisingAppendable append(CharSequence csq) throws IOException {
    getAppendable().append(csq);
    return this;
  }

  @Override
  public final LoggingAdvisingAppendable append(CharSequence csq, int start, int end)
      throws IOException {
    getAppendable().append(csq, start, end);
    return this;
  }

  @Override
  public final LoggingAdvisingAppendable append(char c) throws IOException {
    getAppendable().append(c);
    return this;
  }

  @Override
  public final boolean softLimitReached() {
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

  abstract Appendable getAppendable();

  private static final class SimpleStreamingEscaper extends StreamingEscaper {
    final Appendable escapedAppendable;

    SimpleStreamingEscaper(LoggingAdvisingAppendable delegate, CrossLanguageStringXform transform) {
      super(delegate, transform);
      this.escapedAppendable = transform.escape(delegate);
    }

    @Override
    Appendable getAppendable() {
      return escapedAppendable;
    }

    @Override
    public LoggingAdvisingAppendable appendLoggingFunctionInvocation(
        LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers)
        throws IOException {
      escapedAppendable.append(escapePlaceholder(funCall.placeholderValue(), escapers));
      return this;
    }
  }

  private static final class ContextSensitiveStreamingEscaper extends StreamingEscaper {
    @LazyInit private Appendable escapedDelegate;
    private final ContentKind noOpForKind;

    /**
     * The current number of calls to {@link #enterSanitizedContent} without a matching {@link
     * #exitSanitizedContent()} after a call with a content kind that matches {@link #noOpForKind}.
     */
    private int noOpDepth;

    /** Subclasses can call this to detect if they are in the logOnly state. */
    private boolean isNoOp() {
      return noOpDepth > 0;
    }

    private ContextSensitiveStreamingEscaper(
        LoggingAdvisingAppendable delegate,
        CrossLanguageStringXform transform,
        ContentKind noOpForKind) {
      super(delegate, transform);
      this.noOpForKind = checkNotNull(noOpForKind);
    }

    @Override
    public LoggingAdvisingAppendable enterSanitizedContent(ContentKind kind) throws IOException {
      int depth = noOpDepth;
      if (depth > 0) {
        depth++;
        if (depth < 0) {
          throw new IllegalStateException("overflowed content kind depth");
        }
        noOpDepth = depth;
      } else if (kind == noOpForKind) {
        depth = 1;
        noOpDepth = 1;
      }
      return this;
    }

    @Override
    public LoggingAdvisingAppendable exitSanitizedContent() throws IOException {
      int depth = noOpDepth;
      if (depth > 0) {
        depth--;
        noOpDepth = depth;
      }
      return this;
    }

    @Override
    public LoggingAdvisingAppendable appendLoggingFunctionInvocation(
        LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers)
        throws IOException {
      if (isNoOp()) {
        delegate.appendLoggingFunctionInvocation(funCall, escapers);
      } else {
        getEscapedDelegate().append(escapePlaceholder(funCall.placeholderValue(), escapers));
      }
      return this;
    }

    // TODO(lukes): We only pass these through if we are in a compatible content type.  This is sort
    // of confusing and may require revisiting in the future once we have more examples of how
    // logging and print directives will interact.

    @Override
    public LoggingAdvisingAppendable enterLoggableElement(LogStatement statement) {
      if (isNoOp()) {
        delegate.enterLoggableElement(statement);
      }
      return this;
    }

    @Override
    public LoggingAdvisingAppendable exitLoggableElement() {
      if (isNoOp()) {
        delegate.exitLoggableElement();
      }
      return this;
    }

    @Override
    Appendable getAppendable() {
      return isNoOp() ? delegate : getEscapedDelegate();
    }

    private Appendable getEscapedDelegate() {
      Appendable local = escapedDelegate;
      if (local == null) {
        local = escapedDelegate = transform.escape(delegate);
      }
      return local;
    }
  }
}
