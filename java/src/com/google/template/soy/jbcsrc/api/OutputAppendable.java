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
package com.google.template.soy.jbcsrc.api;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.GoogleLogger;
import com.google.common.html.types.SafeHtml;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingFunctionInvocation;
import com.google.template.soy.logging.SoyLogger;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * The outermost logger used in rendering.
 *
 * <p>This object is for soy internal use only. Do not use.
 */
public final class OutputAppendable extends LoggingAdvisingAppendable {

  public static OutputAppendable create(Appendable outputAppendable, @Nullable SoyLogger logger) {
    return new OutputAppendable(outputAppendable, logger);
  }

  public static OutputAppendable create(StringBuilder sb) {
    return create(sb, null);
  }

  public static OutputAppendable create(StringBuilder sb, @Nullable SoyLogger logger) {
    return new OutputAppendable(sb, logger);
  }

  private static final GoogleLogger googleLogger = GoogleLogger.forEnclosingClass();

  @Nullable private final SoyLogger logger;
  private final Appendable outputAppendable;
  private int logOnlyDepth;

  private OutputAppendable(Appendable outputAppendable, @Nullable SoyLogger logger) {
    this.outputAppendable = checkNotNull(outputAppendable);
    this.logger = logger;
  }

  private boolean isLogOnly() {
    return logOnlyDepth != 0;
  }

  @Override
  public boolean softLimitReached() {
    return outputAppendable instanceof AdvisingAppendable
        && ((AdvisingAppendable) outputAppendable).softLimitReached();
  }

  @CanIgnoreReturnValue
  @Override
  public LoggingAdvisingAppendable append(CharSequence csq) throws IOException {
    if (!isLogOnly()) {
      outputAppendable.append(csq);
    }
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public LoggingAdvisingAppendable append(CharSequence csq, int start, int end) throws IOException {
    if (!isLogOnly()) {
      outputAppendable.append(csq, start, end);
    }
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public LoggingAdvisingAppendable append(char c) throws IOException {
    if (!isLogOnly()) {
      outputAppendable.append(c);
    }
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public LoggingAdvisingAppendable appendLoggingFunctionInvocation(
      LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers)
      throws IOException {
    if (!isLogOnly()) {
      String value =
          logger == null ? funCall.placeholderValue() : logger.evalLoggingFunction(funCall);
      for (Function<String, String> directive : escapers) {
        value = directive.apply(value);
      }
      var consumer = funCall.resultConsumer();
      // When a consumer is present, this means that this logging function is being evaluated to
      // satisfy a call to `splitLogsAndContent`.  In that case we should not render the value to
      // the output, but instead pass it to the consumer.
      if (consumer.isPresent()) {
        consumer.get().accept(value);
      } else {
        outputAppendable.append(value);
      }
    }
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public LoggingAdvisingAppendable enterLoggableElement(LogStatement statement) {
    if (logger == null) {
      if (statement.logOnly()) {
        throw new IllegalStateException(
            "Cannot set logonly=\"true\" unless there is a logger configured");
      }
      return this;
    }
    int depth = logOnlyDepth;
    if (statement.logOnly() || depth > 0) {
      depth++;
      if (depth < 0) {
        throw new IllegalStateException("overflowed logging depth, how you do this?");
      }
      logOnlyDepth = depth;
    }
    appendDebugOutput(logger.enter(statement));
    return this;
  }

  @CanIgnoreReturnValue
  @Override
  public LoggingAdvisingAppendable exitLoggableElement() {
    if (logger == null) {
      return this;
    }
    int depth = logOnlyDepth;
    if (depth > 0) {
      depth--;
      logOnlyDepth = depth;
    }
    // should debug output be guarded by logonly?
    appendDebugOutput(logger.exit());
    return this;
  }

  @Override
  public void flushBuffers(int depth) {
    throw new AssertionError("shouldn't be called");
  }

  private void appendDebugOutput(Optional<SafeHtml> veDebugOutput) {
    if (veDebugOutput.isPresent()) {
      try {
        outputAppendable.append(veDebugOutput.get().getSafeHtmlString());
      } catch (IOException ioException) {
        googleLogger.atWarning().withCause(ioException).log(
            "Something went wrong while outputting VE debug info to the DOM");
      }
    }
  }
}
