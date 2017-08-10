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
import java.util.ArrayList;
import java.util.List;

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
  public abstract LoggingAdvisingAppendable enterLoggableElement(LogStatement statement);

  /** Called whenever a loggable element is exited. */
  public abstract LoggingAdvisingAppendable exitLoggableElement();

  /** Called whenever a logging function is being rendered. */
  public abstract LoggingAdvisingAppendable appendLoggingFunctionInvocation(
      LoggingFunctionInvocation funCall) throws IOException;

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
    public LoggingAdvisingAppendable appendLoggingFunctionInvocation(
        LoggingFunctionInvocation funCall) throws IOException {
      delegate.append(funCall.placeholderValue());
      return this;
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
    public boolean softLimitReached() {
      return false;
    }
  }

  /** A {@link LoggingAdvisingAppendable} that renders to a string builder. */
  public static final class BufferingAppendable extends DelegatingToAppendable<StringBuilder> {
    private static final Object EXIT_MARKER = new Object();
    // lazily allocated list that contains one of 4 types of objects
    // - String literal string content
    // - LogStatement
    // - EXIT_MARKER
    // - LoggingFunctionInvocation
    private List<Object> commands;

    BufferingAppendable() {
      super(new StringBuilder());
    }

    /**
     * Returns the commands list, allocating it if neccesary and appending any string data to it.
     */
    private List<Object> getCommandsAndAddPendingStringData() {
      if (commands == null) {
        commands = new ArrayList<>();
      }
      if (delegate.length() != 0) {
        commands.add(delegate.toString());
        delegate.setLength(0);
      }
      return commands;
    }

    /** Called whenever a loggable element is entered. */
    @Override
    public BufferingAppendable enterLoggableElement(LogStatement statement) {
      getCommandsAndAddPendingStringData().add(statement);
      return this;
    }

    /** Called whenever a loggable element is exited. */
    @Override
    public BufferingAppendable exitLoggableElement() {
      getCommandsAndAddPendingStringData().add(EXIT_MARKER);
      return this;
    }

    @Override
    public LoggingAdvisingAppendable appendLoggingFunctionInvocation(
        LoggingFunctionInvocation funCall) {
      getCommandsAndAddPendingStringData().add(funCall);
      return this;
    }

    public void clearAndReplayOn(LoggingAdvisingAppendable appendable) throws IOException {
      if (commands != null) {
        for (Object o : getCommandsAndAddPendingStringData()) {
          if (o instanceof String) {
            appendable.append((String) o);
          } else if (o instanceof LoggingFunctionInvocation) {
            appendable.appendLoggingFunctionInvocation((LoggingFunctionInvocation) o);
          } else if (o == EXIT_MARKER) {
            appendable.exitLoggableElement();
          } else {
            appendable.enterLoggableElement((LogStatement) o);
          }
        }
        commands = null;
      } else {
        appendable.append(delegate);
        delegate.setLength(0);
      }
    }

    public String getAndClearBuffer() {
      if (commands != null) {
        // NOTE: this ignores all the logging statements which is as it should be since they don't
        // affect output
        for (Object o : getCommandsAndAddPendingStringData()) {
          if (o instanceof String) {
            delegate.append((String) o);
          } else if (o instanceof LoggingFunctionInvocation) {
            delegate.append(((LoggingFunctionInvocation) o).placeholderValue());
          }
        }
        commands = null;
      }
      String value = delegate.toString();
      delegate.setLength(0);
      return value;
    }

    @Override
    public String toString() {
      if (commands != null) {
        // NOTE: this ignores all the logging statements which is as it should be since they don't
        // affect output
        StringBuilder builder = new StringBuilder();
        for (Object o : commands) {
          if (o instanceof String) {
            builder.append((String) o);
          } else if (o instanceof LoggingFunctionInvocation) {
            builder.append(((LoggingFunctionInvocation) o).placeholderValue());
          }
        }
        builder.append(delegate);
        return builder.toString();
      } else {
        return delegate.toString();
      }
    }
  }
}
