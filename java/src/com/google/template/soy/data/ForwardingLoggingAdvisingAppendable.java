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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import java.io.IOException;

/** A simple forwarding implementation, forwards all calls to a delegate. */
public abstract class ForwardingLoggingAdvisingAppendable extends LoggingAdvisingAppendable {
  protected final LoggingAdvisingAppendable delegate;

  protected ForwardingLoggingAdvisingAppendable(LoggingAdvisingAppendable delegate) {
    this.delegate = checkNotNull(delegate);
  }

  @Override
  public boolean softLimitReached() {
    return delegate.softLimitReached();
  }

  @Override
  public LoggingAdvisingAppendable append(CharSequence csq) throws IOException {
    delegate.append(csq);
    return this;
  }

  @Override
  public LoggingAdvisingAppendable append(CharSequence csq, int start, int end) throws IOException {
    delegate.append(csq, start, end);
    return this;
  }

  @Override
  public LoggingAdvisingAppendable append(char c) throws IOException {
    delegate.append(c);
    return this;
  }

  @Override
  public LoggingAdvisingAppendable enterLoggableElement(LogStatement statement) {
    delegate.enterLoggableElement(statement);
    return this;
  }

  @Override
  public LoggingAdvisingAppendable exitLoggableElement() {
    delegate.exitLoggableElement();
    return this;
  }

  @Override
  protected void notifyKindAndDirectionality(ContentKind kind, Dir dir) throws IOException {
    delegate.setKindAndDirectionality(kind, dir);
  }

  @Override
  public LoggingAdvisingAppendable appendLoggingFunctionInvocation(
      LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers)
      throws IOException {
    delegate.appendLoggingFunctionInvocation(funCall, escapers);
    return this;
  }

  @Override
  public void flushBuffers(int depth) throws IOException {
    if (depth > 0) {
      delegate.flushBuffers(depth - 1);
    }
  }
}
