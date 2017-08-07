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

import com.google.template.soy.jbcsrc.api.AdvisingAppendable;
import java.io.IOException;

/**
 * An {@link AdvisingAppendable} that can also process log statements.
 *
 * <p>NOTE: all the logging functionality is currently stubbed out.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public abstract class LoggingAdvisingAppendable implements AdvisingAppendable {
  /**
   * Returns a {@link BufferingAppendable} that renders everything to a buffer that can be accessed
   * via {@link BufferingAppendable#toString()} or {@link BufferingAppendable#getAndClearBuffer()}
   */
  public static BufferingAppendable buffering() {
    return new BufferingAppendable();
  }

  /** Returns a {@link LoggingAdvisingAppendable} that delegates to an {@link AdvisingAppendable} */
  public static LoggingAdvisingAppendable delegating(AdvisingAppendable appendable) {
    return new DelegatingToAdvingingAppendable(appendable);
  }

  /** Returns a {@link LoggingAdvisingAppendable} that delegates to an {@link Appendable} */
  public static LoggingAdvisingAppendable delegating(Appendable appendable) {
    return new DelegatingToAppendable<>(appendable);
  }

  // covariant overrides

  @Override
  public abstract LoggingAdvisingAppendable append(CharSequence csq) throws IOException;

  @Override
  public abstract LoggingAdvisingAppendable append(CharSequence csq, int start, int end)
      throws IOException;

  @Override
  public abstract LoggingAdvisingAppendable append(char c) throws IOException;

  /** Called whenever a loggable element is entered. */
  public LoggingAdvisingAppendable enterLoggableElement(LogStatement statement) {
    throw new UnsupportedOperationException("too soon");
  }

  /** Called whenever a loggable element is exited. */
  public LoggingAdvisingAppendable exitLoggableElement() {
    throw new UnsupportedOperationException("too soon");
  }

  /** Called whenever a logging function is being rendered. */
  public LoggingAdvisingAppendable evalLoggingFunction(LoggingFunctionInvocation funCall) {
    throw new UnsupportedOperationException("too soon");
  }

  /** A {@link LoggingAdvisingAppendable} that renders to an appendable. */
  private static class DelegatingToAppendable<T extends Appendable>
      extends LoggingAdvisingAppendable {
    final T delegate;

    private DelegatingToAppendable(T delegate) {
      this.delegate = checkNotNull(delegate);
    }

    @Override
    public final DelegatingToAppendable append(CharSequence s) throws IOException {
      delegate.append(s);
      return this;
    }

    @Override
    public final DelegatingToAppendable append(CharSequence s, int start, int end)
        throws IOException {
      delegate.append(s, start, end);
      return this;
    }

    @Override
    public final DelegatingToAppendable append(char c) throws IOException {
      delegate.append(c);
      return this;
    }

    @Override
    public boolean softLimitReached() {
      return false;
    }
  }

  /** A {@link LoggingAdvisingAppendable} that renders to a string builder. */
  public static final class BufferingAppendable extends DelegatingToAppendable<StringBuilder> {
    BufferingAppendable() {
      super(new StringBuilder());
    }

    public String getAndClearBuffer() {
      String value = delegate.toString();
      delegate.setLength(0);
      return value;
    }

    @Override
    public String toString() {
      return delegate.toString();
    }
  }

  /** A {@link LoggingAdvisingAppendable} that renders to a string builder. */
  private static final class DelegatingToAdvingingAppendable
      extends DelegatingToAppendable<AdvisingAppendable> {
    private DelegatingToAdvingingAppendable(AdvisingAppendable delegate) {
      super(delegate);
    }

    @Override
    public boolean softLimitReached() {
      return delegate.softLimitReached();
    }
  }
}
