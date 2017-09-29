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

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SanitizedContent.ContentKind;
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

  /**
   * An implementation that only delegates {@link #append} calls {@link #enterSanitizedContent} and
   * {@link #exitSanitizedContent}. This has the effect of coercing the content to a string by
   * dropping all the strict content directives.
   */
  public static LoggingAdvisingAppendable stringCoercing(LoggingAdvisingAppendable delegate) {
    return new ForwardingLoggingAdvisingAppendable(delegate) {
      @Override
      public LoggingAdvisingAppendable enterSanitizedContent(ContentKind kind) {
        return this;
      }

      @Override
      public LoggingAdvisingAppendable exitSanitizedContent() {
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
      public LoggingAdvisingAppendable appendLoggingFunctionInvocation(
          LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers)
          throws IOException {
        return append(escapePlaceholder(funCall.placeholderValue(), escapers));
      }
    };
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

  /**
   * Marks the beginning of a sequence of sanitized content in the appendable. All the {@link
   * #append} commands until a matching {@link #exitSanitizedContent()} should be considered to be
   * content of the given kind that has already been sanitized.
   *
   * <p>The default implementation does nothing.
   *
   * <p>NOTE: if you are implementing a transforming appendable that applies some sort of escaping
   * logic you probably do not want to propagate these calls to your delegate. Doing so can create
   * confusion about which bit of code is responsible for performing escaping. For example, consider
   * a sequence of calls like {@code enterSanitizedContent(HTML)... append(XXX) ....
   * exitSanitizedContent()}, if the escaper is {@code |escapeHtml} then the underlying appendable
   * already expects that will receive all html content and thus the enter/exit calls are
   * meaningless.
   *
   * @param kind The kind of content that we are entering.
   * @throws IOException
   */
  public LoggingAdvisingAppendable enterSanitizedContent(ContentKind kind) throws IOException {
    return this;
  }

  /**
   * Marks the end of a sequence of sanitized content.
   *
   * <p>The default implementation does nothing.
   *
   * @throws IOException
   */
  public LoggingAdvisingAppendable exitSanitizedContent() throws IOException {
    return this;
  }

  /**
   * Called whenever a logging function is being rendered.
   *
   * <p>TODO(lukes): come up with a better interface than Function. Maybe Escaper?
   *
   * @param funCall The function invocation
   * @param escapers The escapers to apply to the result. NOTE: this should be SoyJavaPrintDirective
   *     or similar but that would cause cycles between soy.data and soy.shared.restricted
   * @return this
   */
  public abstract LoggingAdvisingAppendable appendLoggingFunctionInvocation(
      LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers)
      throws IOException;

  /** A {@link LoggingAdvisingAppendable} that renders to an appendable. */
  private static class DelegatingToAppendable<T extends Appendable>
      extends AbstractLoggingAdvisingAppendable {
    final T delegate;

    private DelegatingToAppendable(T delegate) {
      this.delegate = checkNotNull(delegate);
    }

    @Override
    protected final void doAppend(CharSequence s) throws IOException {
      delegate.append(s);
    }

    @Override
    protected final void doAppend(CharSequence s, int start, int end) throws IOException {
      delegate.append(s, start, end);
    }

    @Override
    protected final void doAppend(char c) throws IOException {
      delegate.append(c);
    }

    @Override
    protected void doAppendLoggingFunctionInvocation(
        LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers)
        throws IOException {
      escapePlaceholder(funCall.placeholderValue(), escapers);
    }

    @Override
    protected void doEnterLoggableElement(LogStatement statement) {}

    @Override
    protected void doExitLoggableElement() {}

    @Override
    public boolean softLimitReached() {
      return false;
    }
  }

  /** A {@link LoggingAdvisingAppendable} that renders to a string builder. */
  public static final class BufferingAppendable extends DelegatingToAppendable<StringBuilder> {
    private static final Object EXIT_LOG_STATEMENT_MARKER = new Object();
    private static final Object EXIT_SANITIZED_CONTENT_MARKER = new Object();
    // lazily allocated list that contains one of 6 types of objects, each which corresponds to one
    // of the callback methods.
    // - String literal string content -> corresponds to a contiguous sequence of append calls
    // - LogStatement -> corresponds to enterLoggableElement
    // - EXIT_LOG_STATEMENT_MARKER -> corresponds to exitLoggableElement
    // - ContentKind -> corresponds to enterSanitizedContent
    // - EXIT_SANITIZED_CONTENT_MARKER -> corresponds to exitSanitizedContent call
    // - LoggingFunctionInvocation -> corresponds to appendLoggingFunctionInvocation
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

    @Override
    public LoggingAdvisingAppendable enterSanitizedContent(ContentKind kind) throws IOException {
      getCommandsAndAddPendingStringData().add(kind);
      return this;
    }

    @Override
    public LoggingAdvisingAppendable exitSanitizedContent() throws IOException {
      getCommandsAndAddPendingStringData().add(EXIT_SANITIZED_CONTENT_MARKER);
      return this;
    }

    /** Called whenever a loggable element is entered. */
    @Override
    protected final void doEnterLoggableElement(LogStatement statement) {
      getCommandsAndAddPendingStringData().add(statement);
    }

    /** Called whenever a loggable element is exited. */
    @Override
    protected final void doExitLoggableElement() {
      getCommandsAndAddPendingStringData().add(EXIT_LOG_STATEMENT_MARKER);
    }

    @Override
    protected void doAppendLoggingFunctionInvocation(
        LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers)
        throws IOException {
      getCommandsAndAddPendingStringData().add(LoggingFunctionCommand.create(funCall, escapers));
    }

    public void replayOn(LoggingAdvisingAppendable appendable) throws IOException {
      if (commands != null) {
        for (Object o : getCommandsAndAddPendingStringData()) {
          if (o instanceof String) {
            appendable.append((String) o);
          } else if (o instanceof LoggingFunctionCommand) {
            ((LoggingFunctionCommand) o).replayOn(appendable);
          } else if (o == EXIT_LOG_STATEMENT_MARKER) {
            appendable.exitLoggableElement();
          } else if (o == EXIT_SANITIZED_CONTENT_MARKER) {
            appendable.exitSanitizedContent();
          } else if (o instanceof LogStatement) {
            appendable.enterLoggableElement((LogStatement) o);
          } else if (o instanceof ContentKind) {
            appendable.enterSanitizedContent((ContentKind) o);
          } else {
            throw new AssertionError("unexpected command object: " + o);
          }
        }
      } else {
        appendable.append(delegate);
      }
    }

    @VisibleForTesting
    public String getAndClearBuffer() {
      if (commands != null) {
        // NOTE: this ignores all the logging statements which is as it should be since they don't
        // affect output
        appendCommandsToBuilder(getCommandsAndAddPendingStringData(), delegate);
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
        appendCommandsToBuilder(commands, builder);
        builder.append(delegate);
        return builder.toString();
      } else {
        return delegate.toString();
      }
    }

    private static void appendCommandsToBuilder(List<Object> commands, StringBuilder builder) {
      for (Object o : commands) {
        if (o instanceof String) {
          builder.append((String) o);
        } else if (o instanceof LoggingFunctionCommand) {
          LoggingFunctionCommand command = (LoggingFunctionCommand) o;
          builder.append(escapePlaceholder(command.fn().placeholderValue(), command.escapers()));
        }
      }
    }

    @AutoValue
    abstract static class LoggingFunctionCommand {
      static LoggingFunctionCommand create(
          LoggingFunctionInvocation fn, ImmutableList<Function<String, String>> escapers) {
        return new AutoValue_LoggingAdvisingAppendable_BufferingAppendable_LoggingFunctionCommand(
            fn, escapers);
      }

      abstract LoggingFunctionInvocation fn();

      abstract ImmutableList<Function<String, String>> escapers();

      LoggingAdvisingAppendable replayOn(LoggingAdvisingAppendable appendable) throws IOException {
        return appendable.appendLoggingFunctionInvocation(fn(), escapers());
      }
    }
  }

  protected static String escapePlaceholder(
      String placeholder, List<Function<String, String>> escapers) {
    // TODO(lukes): we should be able to do this at compile time
    for (Function<String, String> escaper : escapers) {
      placeholder = escaper.apply(placeholder);
    }
    return placeholder;
  }
}
