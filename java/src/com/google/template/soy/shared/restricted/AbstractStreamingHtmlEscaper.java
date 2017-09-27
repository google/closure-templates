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

import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import java.io.IOException;

/**
 * A delegating {@link LoggingAdvisingAppendable} that can detect when the appendable is in an html
 * context or not.
 */
public abstract class AbstractStreamingHtmlEscaper extends LoggingAdvisingAppendable {

  /**
   * The current number of calls to {@link #enterSanitizedContent} without a matching {@link
   * #exitSanitizedContent()} after a call with a content kind that matches {@link
   * ContentKind#HTML}.
   */
  private int htmlDepth;

  protected final LoggingAdvisingAppendable delegate;

  protected AbstractStreamingHtmlEscaper(LoggingAdvisingAppendable delegate) {
    this.delegate = checkNotNull(delegate);
  }

  @Override
  public final LoggingAdvisingAppendable enterSanitizedContent(ContentKind kind)
      throws IOException {
    int depth = htmlDepth;
    if (depth > 0) {
      depth++;
      if (depth < 0) {
        throw new IllegalStateException("overflowed content kind depth");
      }
      htmlDepth = depth;
    } else if (kind == ContentKind.HTML) {
      depth = 1;
      htmlDepth = 1;
    }
    return this;
  }

  @Override
  public final LoggingAdvisingAppendable exitSanitizedContent() throws IOException {
    int depth = htmlDepth;
    if (depth > 0) {
      depth--;
      htmlDepth = depth;
    }
    return this;
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
  public final boolean softLimitReached() {
    return delegate.softLimitReached();
  }

  protected final boolean isInHtml() {
    return htmlDepth > 0;
  }

  /**
   * Template method for subtypes to select the appendable for the {@code append} methods to
   * delegate to.
   */
  protected abstract Appendable getAppendable();
}
