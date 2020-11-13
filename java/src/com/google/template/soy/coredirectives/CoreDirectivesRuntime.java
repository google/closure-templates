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
package com.google.template.soy.coredirectives;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.Dir;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingFunctionInvocation;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.shared.internal.AbstractStreamingHtmlEscaper;
import com.google.template.soy.shared.internal.EscapingConventions;
import java.io.IOException;

/** Runtime functions for implementing the directives in this package. */
public final class CoreDirectivesRuntime {
  public static SanitizedContent escapeHtml(SoyValue value) {
    if (value == null) {
      // jbcsrc uses null as null.
      value = NullData.INSTANCE;
    }
    Dir valueDir = null;
    if (value instanceof SanitizedContent) {
      SanitizedContent sanitizedContent = (SanitizedContent) value;
      if (sanitizedContent.getContentKind() == SanitizedContent.ContentKind.HTML) {
        return (SanitizedContent) value;
      }
      valueDir = sanitizedContent.getContentDirection();
    }
    return UnsafeSanitizedContentOrdainer.ordainAsSafe(
        EscapingConventions.EscapeHtml.INSTANCE.escape(value.coerceToString()),
        SanitizedContent.ContentKind.HTML,
        valueDir);
  }

  public static SanitizedContent escapeHtml(String value) {
    return UnsafeSanitizedContentOrdainer.ordainAsSafe(
        EscapingConventions.EscapeHtml.INSTANCE.escape(value), SanitizedContent.ContentKind.HTML);
  }

  public static LoggingAdvisingAppendable streamingEscapeHtml(
      final LoggingAdvisingAppendable delegate) {
    return new StreamingHtmlEscaper(delegate);
  }

  private static final class StreamingHtmlEscaper extends AbstractStreamingHtmlEscaper {
    private StreamingHtmlEscaper(LoggingAdvisingAppendable delegate) {
      super(delegate, EscapingConventions.EscapeHtml.INSTANCE.escape(delegate));
    }

    @Override
    protected void notifyContentKind(ContentKind kind) throws IOException {
      if (isInHtml()) {
        activeAppendable = delegate;
      }
    }

    @Override
    public LoggingAdvisingAppendable appendLoggingFunctionInvocation(
        LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers)
        throws IOException {
      if (isInHtml()) {
        delegate.appendLoggingFunctionInvocation(funCall, escapers);
      } else {
        activeAppendable.append(escapePlaceholder(funCall.placeholderValue(), escapers));
      }
      return this;
    }

    // TODO(lukes): We only pass these through if we are in HTML.  This is sort
    // of confusing and may require revisiting in the future once we have more examples of how
    // logging and print directives will interact.

    @Override
    public LoggingAdvisingAppendable enterLoggableElement(LogStatement statement) {
      if (isInHtml()) {
        delegate.enterLoggableElement(statement);
      }
      return this;
    }

    @Override
    public LoggingAdvisingAppendable exitLoggableElement() {
      if (isInHtml()) {
        delegate.exitLoggableElement();
      }
      return this;
    }
  }
}
