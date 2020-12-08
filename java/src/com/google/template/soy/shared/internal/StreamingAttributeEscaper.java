/*
 * Copyright 2020 Google Inc.
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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingFunctionInvocation;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.shared.internal.EscapingConventions.CrossLanguageStringXform;
import java.io.IOException;

/**
 * A StreamingAttributeEscaper is a decorator for a {@link LoggingAdvisingAppendable} that applies
 * escaping logic to untrusted content. This differs from StreamingEscaper in that it escapes HTML
 * attributes. A key issue for escapeHtmlAttributeValue is that tags may be stripped from the final
 * output since it is expected to be printed in an HTML attribute value. This necessitates a special
 * implementation. TODO: Consider dropping this feature and having users call htmlToText explicitly.
 */
public final class StreamingAttributeEscaper extends LoggingAdvisingAppendable {
  /**
   * Creates a streaming escaper, or returns the delegate if it is already escaping with the same
   * settings.
   */
  public static StreamingAttributeEscaper create(
      LoggingAdvisingAppendable delegate, CrossLanguageStringXform transform) {
    if (delegate instanceof StreamingAttributeEscaper) {
      StreamingAttributeEscaper delegateAsStreamingEscaper = (StreamingAttributeEscaper) delegate;
      if (delegateAsStreamingEscaper.transform == transform) {
        return delegateAsStreamingEscaper;
      }
    }
    return new StreamingAttributeEscaper(delegate, transform);
  }

  private final LoggingAdvisingAppendable delegate;
  private final CrossLanguageStringXform transform;
  private final Appendable escapedAppendable;
  private StringBuilder buffer = null;

  private StreamingAttributeEscaper(
      LoggingAdvisingAppendable delegate, CrossLanguageStringXform transform) {
    this.delegate = checkNotNull(delegate);
    this.transform = transform;
    this.escapedAppendable = transform.escape(delegate);
  }

  private Appendable getAppendable() {
    if (getSanitizedContentKind() == ContentKind.HTML) {
      if (buffer == null) {
        buffer = new StringBuilder();
      }
      return buffer;
    } else {
      return escapedAppendable;
    }
  }

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
  public LoggingAdvisingAppendable appendLoggingFunctionInvocation(
      LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers)
      throws IOException {
    if (getSanitizedContentKind() == ContentKind.HTML) {
      getAppendable().append(escapePlaceholder(funCall.placeholderValue(), escapers));
    } else {
      delegate.appendLoggingFunctionInvocation(
          funCall,
          new ImmutableList.Builder<Function<String, String>>()
              .addAll(escapers)
              .add(transform::escape)
              .build());
    }
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

  @Override
  public void flushBuffers(int depth) throws IOException {
    if (buffer != null) {
      delegate.append(
          Sanitizers.stripHtmlTags(
              /* value=*/ buffer.toString(), /* safeTags=*/ null, /* rawSpacesAllowed=*/ true));
    }
    if (depth > 0) {
      delegate.flushBuffers(depth - 1);
    }
  }
}
