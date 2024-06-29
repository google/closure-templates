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
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.ForOverride;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.api.AdvisingAppendable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * An {@link AdvisingAppendable} that can also process log statements.
 *
 * <p>NOTE: all the logging functionality is currently stubbed out.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public abstract class LoggingAdvisingAppendable implements AdvisingAppendable {

  /** The kind of the content appended to this appendable. */
  @Nullable private ContentKind kind;

  /** The directionality of the content appended to this appendable. */
  @Nullable private Dir contentDir;

  /**
   * Returns a {@link BufferingAppendable} that renders everything to a buffer that can be accessed
   * via {@link BufferingAppendable#toString()} or {@link BufferingAppendable#getAndClearBuffer()}
   */
  @Nonnull
  public static BufferingAppendable buffering() {
    return new BufferingAppendable();
  }

  /**
   * Returns a {@link BufferingAppendable} that renders everything to a buffer that can be accessed
   * via {@link BufferingAppendable#toString()} or {@link BufferingAppendable#getAndClearBuffer()}
   */
  @Nonnull
  public static BufferingAppendable buffering(ContentKind kind) {
    return new BufferingAppendable(kind);
  }

  /** Returns a {@link LoggingAdvisingAppendable} that delegates to an {@link Appendable} */
  public static LoggingAdvisingAppendable delegating(Appendable appendable) {
    return new DelegatingToAppendable<>(appendable);
  }

  /**
   * An implementation that only delegates {@link #append} calls. This has the effect of coercing
   * the content to a string by dropping all the strict content directives.
   */
  @Nonnull
  public static LoggingAdvisingAppendable stringCoercing(LoggingAdvisingAppendable delegate) {
    return new StringCoercingAppendable(delegate);
  }

  private static final class StringCoercingAppendable extends ForwardingLoggingAdvisingAppendable {
    StringCoercingAppendable(LoggingAdvisingAppendable delegate) {
      super(delegate);
    }

    @Override
    protected LoggingAdvisingAppendable notifyKindAndDirectionality(
        ContentKind kind, @Nullable Dir direction) {
      delegate.setKindAndDirectionality(kind, direction);
      if (kind == ContentKind.TEXT) {
        return delegate;
      }
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
  }

  // covariant overrides

  @Override
  @Nonnull
  public abstract LoggingAdvisingAppendable append(CharSequence csq) throws IOException;

  @Override
  @Nonnull
  public abstract LoggingAdvisingAppendable append(CharSequence csq, int start, int end)
      throws IOException;

  @Override
  @Nonnull
  public abstract LoggingAdvisingAppendable append(char c) throws IOException;

  /** Called whenever a loggable element is entered. */
  @Nonnull
  public abstract LoggingAdvisingAppendable enterLoggableElement(LogStatement statement);

  /** Called whenever a loggable element is exited. */
  @Nonnull
  public abstract LoggingAdvisingAppendable exitLoggableElement();

  /**
   * Flushes all intermediate buffers stored within the appendable.
   *
   * @param depth If this is a delegating appendable, the flushBuffers command should only be
   *     delegated if {@code depth > 0} and depth should be decremented when delegating.
   */
  public abstract void flushBuffers(int depth) throws IOException;

  /**
   * Marks the content kind of this appendable. All the {@link #append} commands should be
   * considered to be content of the given kind that has already been sanitized.
   *
   * <p>Calls after the first call to this are ignored. In the case of an appendable's content kind
   * changing, we trust that the autoescaper has inserted the correct escape directives, so we do
   * not need to track this in the appendable.
   */
  @Nonnull
  @CanIgnoreReturnValue
  public LoggingAdvisingAppendable setKindAndDirectionality(ContentKind kind) {
    if (this.kind == null) {
      this.kind = kind;
      var direction = this.contentDir = kind.getDefaultDir();
      return notifyKindAndDirectionality(kind, direction);
    }
    return this;
  }

  /**
   * Marks the content kind of this appendable. All the {@link #append} commands should be
   * considered to be content of the given kind that has already been sanitized.
   *
   * <p>Calls after the first call to this are ignored. In the case of an appendable's content kind
   * changing, we trust that the autoescaper has inserted the correct escape directives, so we do
   * not need to track this in the appendable.
   */
  @CanIgnoreReturnValue
  @Nonnull
  public final LoggingAdvisingAppendable setKindAndDirectionality(
      ContentKind kind, @Nullable Dir direction) {
    checkNotNull(kind);
    if (this.kind == null) {
      this.kind = kind;
      this.contentDir = direction;
      return notifyKindAndDirectionality(kind, direction);
    }
    return this;
  }

  /**
   * Called when the content kind is initially set. Override this be notified when the content kind
   * is set.
   *
   * @see #setKindAndDirectionality(ContentKind, Dir)
   */
  @ForOverride
  protected LoggingAdvisingAppendable notifyKindAndDirectionality(
      ContentKind kind, @Nullable Dir direction) {
    return this;
  }

  /**
   * Returns the content kind of this appendable.
   *
   * @see #setSanitizedContentKind(ContentKind)
   */
  @Nullable
  public final ContentKind getSanitizedContentKind() {
    return kind;
  }
  /**
   * Returns the directionality of this appendable.
   *
   * @see #setSanitizedContentDirectionality(Dir)
   */
  @Nullable
  public final Dir getSanitizedContentDirectionality() {
    return contentDir;
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
  @Nonnull
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
    protected void doAppend(CharSequence s) throws IOException {
      delegate.append(s);
    }

    @Override
    protected void doAppend(CharSequence s, int start, int end) throws IOException {
      delegate.append(s, start, end);
    }

    @Override
    protected void doAppend(char c) throws IOException {
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

    @Override
    public void flushBuffers(int depth) throws IOException {
      throw new AssertionError("should not be called");
    }
  }

  /** A buffer of commands that can be replayed on a {@link LoggingAdvisingAppendable}. */
  @Immutable
  public static final class CommandBuffer {
    // There is no common supertype of all the commands but they are all deeply immutable.
    @SuppressWarnings("Immutable")
    private final ImmutableList<Object> commands;

    private CommandBuffer(ImmutableList<Object> commands) {
      this.commands = commands;
    }

    public void replayOn(LoggingAdvisingAppendable appendable) throws IOException {
      BufferingAppendable.replayOn(commands, appendable);
    }

    boolean hasContent() {
      for (var command : commands) {
        // BufferingAppendable only adds non-empty strings to the command list.
        if (command instanceof String) {
          return true;
        }
        // NOTE: we don't need to check logging functions, because CommandBuffers are only created
        // for HTML and ATTRIBUTES, and logging functions are only used for attribute_values.  So to
        // see one we must have already seen at least an `=` character and returned above.
      }
      return false;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      BufferingAppendable.appendCommandsToBuilder(commands, builder);
      return builder.toString();
    }
  }

  /** A {@link LoggingAdvisingAppendable} that renders to a string builder. */
  public static class BufferingAppendable extends DelegatingToAppendable<StringBuilder> {

    private static final Object EXIT_LOG_STATEMENT_MARKER = new Object();
    // lazily allocated list that contains one of 7 types of objects, each which corresponds to one
    // of the callback methods.
    // - String literal string content -> corresponds to a contiguous sequence of append calls
    // - LogStatement -> corresponds to enterLoggableElement
    // - EXIT_LOG_STATEMENT_MARKER -> corresponds to exitLoggableElement
    // - LoggingFunctionInvocation -> corresponds to appendLoggingFunctionInvocation
    // - Dir -> corresponds to setSanitizedContentDirectionality
    // - SET_SANITIZED_CONTENT_DIRECTIONALITY_NULL_MARKER -> corresponds to
    //   setSanitizedContentDirectionality with a null parameter
    private List<Object> commands;

    protected BufferingAppendable() {
      super(new StringBuilder());
    }

    protected BufferingAppendable(SanitizedContent.ContentKind kind) {
      this();
      setKindAndDirectionality(kind);
    }

    /**
     * Returns the commands list, allocating it if necessary and appending any string data to it.
     */
    private List<Object> getCommandsAndAddPendingStringData() {
      var commands = this.commands;
      if (commands == null) {
        this.commands = commands = new ArrayList<>();
      }
      var delegate = this.delegate;
      if (delegate.length() != 0) {
        commands.add(delegate.toString());
        delegate.setLength(0);
      }
      return commands;
    }

    /** Called whenever a loggable element is entered. */
    @Override
    protected void doEnterLoggableElement(LogStatement statement) {
      getCommandsAndAddPendingStringData().add(statement);
    }

    /** Called whenever a loggable element is exited. */
    @Override
    protected void doExitLoggableElement() {
      getCommandsAndAddPendingStringData().add(EXIT_LOG_STATEMENT_MARKER);
    }

    @Override
    protected void doAppendLoggingFunctionInvocation(
        LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers)
        throws IOException {
      getCommandsAndAddPendingStringData().add(LoggingFunctionCommand.create(funCall, escapers));
    }

    public void replayOn(LoggingAdvisingAppendable appendable) throws IOException {
      if (getSanitizedContentKind() != null) {
        appendable =
            appendable.setKindAndDirectionality(
                getSanitizedContentKind(), getSanitizedContentDirectionality());
      }
      var commands = this.commands;
      if (commands != null) {
        replayOn(commands, appendable);
      }
      var delegate = this.delegate;
      if (delegate.length() != 0) {
        appendable.append(delegate);
      }
    }

    private static void replayOn(List<Object> commands, LoggingAdvisingAppendable appendable)
        throws IOException {
      for (Object o : commands) {
        if (o instanceof String) {
          appendable.append((String) o);
        } else if (o instanceof LoggingFunctionCommand) {
          ((LoggingFunctionCommand) o).replayOn(appendable);
        } else if (o == EXIT_LOG_STATEMENT_MARKER) {
          appendable.exitLoggableElement();
        } else if (o instanceof LogStatement) {
          appendable.enterLoggableElement((LogStatement) o);
        } else {
          throw new AssertionError("unexpected command object: " + o);
        }
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

    /**
     * Returns the content as a {@link SoyValue}. Callers should prefer calling {@code
     * getAsSanitizedContent} or {@code getAsStringData} when they can.
     */
    @Nonnull
    public SoyValue getAsSoyValue() {
      var kind = getSanitizedContentKind();
      // Null will happen for default empty deltemplates.
      if (kind == null || kind == ContentKind.TEXT) {
        return getAsStringData();
      } else {
        return getAsSanitizedContent();
      }
    }

    @Nonnull
    public SanitizedContent getAsSanitizedContent() {
      var kind = getSanitizedContentKind();
      if (kind == null || kind == ContentKind.TEXT) {
        throw new IllegalStateException("not a sanitized content kind: " + kind);
      }
      var dir = getSanitizedContentDirectionality();
      if (kind == ContentKind.HTML || kind == ContentKind.ATTRIBUTES) {
        var commands = this.commands;
        if (commands == null) {
          return SanitizedContent.create(delegate.toString(), kind, dir);
        }
        return SanitizedContent.create(
            new CommandBuffer(ImmutableList.copyOf(getCommandsAndAddPendingStringData())),
            kind,
            dir);
      } else {
        return SanitizedContent.create(toString(), kind, dir);
      }
    }

    @Nonnull
    public StringData getAsStringData() {
      // all kinds can be coerced to string data, no need to check
      var commands = this.commands;
      if (commands == null) {
        return StringData.forValue(delegate.toString());
      }
      // This case is entirely about soy element style calls passing logging functions to
      // callees.  Possibly we should disallow that?
      return StringData.forValue(
          new CommandBuffer(ImmutableList.copyOf(getCommandsAndAddPendingStringData())));
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
        // ignore the logging statements
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
