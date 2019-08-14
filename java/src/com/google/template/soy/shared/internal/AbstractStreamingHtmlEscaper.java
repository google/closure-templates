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

import com.google.template.soy.data.Dir;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import java.io.IOException;
import javax.annotation.Nullable;

/**
 * A delegating {@link LoggingAdvisingAppendable} that can detect when the appendable is in an html
 * context or not.
 *
 * <p>This tracks the {@code activeAppendable}, or the appendable {@code append} calls should be
 * sent to, based on the content kind. The active appendable starts out as the appendable that does
 * the escaping (the {@code escapingAppendable} constructor param). Subclasses can overwrite the
 * {@code activeAppendable} field in the {@link #notifyContentKind(ContentKind)} method based on the
 * content kind.
 */
public abstract class AbstractStreamingHtmlEscaper extends LoggingAdvisingAppendable {

  protected final LoggingAdvisingAppendable delegate;
  protected Appendable activeAppendable;

  protected AbstractStreamingHtmlEscaper(
      LoggingAdvisingAppendable delegate, Appendable escapingAppendable) {
    this.delegate = checkNotNull(delegate);
    activeAppendable = checkNotNull(escapingAppendable);
  }

  @Override
  public final LoggingAdvisingAppendable append(CharSequence csq) throws IOException {
    activeAppendable.append(csq);
    return this;
  }

  @Override
  public final LoggingAdvisingAppendable append(CharSequence csq, int start, int end)
      throws IOException {
    activeAppendable.append(csq, start, end);
    return this;
  }

  @Override
  public final LoggingAdvisingAppendable append(char c) throws IOException {
    activeAppendable.append(c);
    return this;
  }

  @Override
  public final boolean softLimitReached() {
    return delegate.softLimitReached();
  }

  /**
   * Override this to set the appendable for the {@code append} methods to delegate to, based on the
   * content kind.
   */
  @Override
  protected abstract void notifyContentKind(ContentKind kind) throws IOException;

  @Override
  protected void notifyContentDirectionality(@Nullable Dir contentDir) throws IOException {
    delegate.setSanitizedContentDirectionality(contentDir);
  }

  protected final boolean isInHtml() {
    return getSanitizedContentKind() == ContentKind.HTML;
  }
}
