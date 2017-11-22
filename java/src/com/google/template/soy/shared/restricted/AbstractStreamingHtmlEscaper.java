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

import com.google.template.soy.data.Dir;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import java.io.IOException;
import javax.annotation.Nullable;

/**
 * A delegating {@link LoggingAdvisingAppendable} that can detect when the appendable is in an html
 * context or not.
 */
public abstract class AbstractStreamingHtmlEscaper extends IsInHtmlAppendable {

  protected final LoggingAdvisingAppendable delegate;

  protected AbstractStreamingHtmlEscaper(LoggingAdvisingAppendable delegate) {
    this.delegate = checkNotNull(delegate);
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

  @Override
  public LoggingAdvisingAppendable enterSanitizedContentDirectionality(@Nullable Dir contentDir)
      throws IOException {
    delegate.enterSanitizedContentDirectionality(contentDir);
    return this;
  }

  @Override
  public LoggingAdvisingAppendable exitSanitizedContentDirectionality() throws IOException {
    delegate.exitSanitizedContentDirectionality();
    return this;
  }

  /**
   * Template method for subtypes to select the appendable for the {@code append} methods to
   * delegate to.
   */
  protected abstract Appendable getAppendable() throws IOException;
}
