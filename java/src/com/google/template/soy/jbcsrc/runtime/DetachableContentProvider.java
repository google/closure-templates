/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.jbcsrc.runtime;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.Dir;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingAdvisingAppendable.BufferingAppendable;
import com.google.template.soy.data.LoggingFunctionInvocation;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.api.AdvisingAppendable;
import com.google.template.soy.jbcsrc.api.RenderResult;
import java.io.IOException;
import javax.annotation.Nullable;

/**
 * A special implementation of {@link SoyValueProvider} to use as a shared base class for the {@code
 * jbcsrc} implementations of the generated {@code LetContentNode} and {@code CallParamContentNode}
 * implementations.
 */
public abstract class DetachableContentProvider implements SoyValueProvider {
  @Nullable private final ContentKind contentKind;

  // Will be either a SanitizedContent or a StringData.
  private SoyValue resolvedValue;
  private BufferingAppendable buffer;

  // Will be either an LoggingAdvisingAppendable.BufferingAppendable or a TeeAdvisingAppendable
  // depending on whether we are being resolved via 'status()' or via 'renderAndResolve()'
  private LoggingAdvisingAppendable builder;

  protected DetachableContentProvider(@Nullable ContentKind contentKind) {
    this.contentKind = contentKind;
  }

  @Override
  public final SoyValue resolve() {
    checkState(isDone(), "called resolve() before status() returned ready.");
    SoyValue local = getResolvedValue();
    checkState(
        local != TombstoneValue.INSTANCE,
        "called resolve() after calling renderAndResolve with isLast == true");
    return local;
  }

  @Override
  public final RenderResult status() {
    if (isDone()) {
      return RenderResult.done();
    }
    LoggingAdvisingAppendable.BufferingAppendable currentBuilder =
        (LoggingAdvisingAppendable.BufferingAppendable) builder;
    if (currentBuilder == null) {
      builder = currentBuilder = LoggingAdvisingAppendable.buffering();
    }
    RenderResult result;
    try {
      result = doRender(currentBuilder);
    } catch (IOException ioe) {
      throw new AssertionError("impossible", ioe);
    }
    if (result.isDone()) {
      buffer = currentBuilder;
      builder = null;
    }
    return result;
  }

  @Override
  public RenderResult renderAndResolve(LoggingAdvisingAppendable appendable, boolean isLast)
      throws IOException {
    if (isDone()) {
      if (buffer == null && resolvedValue == TombstoneValue.INSTANCE) {
        throw new IllegalStateException(
            "calling renderAndResolve after setting isLast = true is not supported");
      }
      buffer.replayOn(appendable);
      return RenderResult.done();
    }
    if (isLast) {
      RenderResult result = doRender(appendable);
      if (result.isDone()) {
        resolvedValue = TombstoneValue.INSTANCE;
      }
      return result;
    }

    TeeAdvisingAppendable currentBuilder = (TeeAdvisingAppendable) builder;
    if (currentBuilder == null) {
      builder = currentBuilder = new TeeAdvisingAppendable(appendable);
    }
    RenderResult result = doRender(currentBuilder);
    if (result.isDone()) {
      buffer = currentBuilder.buffer;
      builder = null;
    }
    return result;
  }

  private boolean isDone() {
    return resolvedValue != null || buffer != null;
  }

  private SoyValue getResolvedValue() {
    SoyValue local = resolvedValue;
    if (local == null) {
      if (buffer != null) {
        String string = buffer.toString();
        // This drops logs, but that is sometimes necessary.  We should make sure this only happens
        // when it has to by making sure that renderAndResolve is used for all printing usecases
        if (contentKind != null) {
          local = UnsafeSanitizedContentOrdainer.ordainAsSafe(string, contentKind);
        } else {
          local = StringData.forValue(string);
        }
        resolvedValue = local;
      } else {
        throw new AssertionError("getResolvedValue() should only be called if the value isDone.");
      }
    }
    return local;
  }

  /** Overridden by generated subclasses to implement lazy detachable resolution. */
  protected abstract RenderResult doRender(LoggingAdvisingAppendable appendable) throws IOException;

  /**
   * An {@link AdvisingAppendable} that forwards to a delegate appendable but also saves all the
   * same forwarded content into a buffer.
   *
   * <p>See: <a href="http://en.wikipedia.org/wiki/Tee_%28command%29">Tee command for the unix
   * command on which this is based.
   */
  private static final class TeeAdvisingAppendable extends LoggingAdvisingAppendable {
    final BufferingAppendable buffer = LoggingAdvisingAppendable.buffering();
    final LoggingAdvisingAppendable delegate;

    TeeAdvisingAppendable(LoggingAdvisingAppendable delegate) {
      this.delegate = delegate;
    }

    @Override
    protected void notifyContentKind(ContentKind kind) throws IOException {
      delegate.setSanitizedContentKind(kind);
      buffer.setSanitizedContentKind(kind);
    }

    @Override
    protected void notifyContentDirectionality(@Nullable Dir contentDir) throws IOException {
      delegate.setSanitizedContentDirectionality(contentDir);
      buffer.setSanitizedContentDirectionality(contentDir);
    }

    @Override
    public TeeAdvisingAppendable append(CharSequence csq) throws IOException {
      delegate.append(csq);
      buffer.append(csq);
      return this;
    }

    @Override
    public TeeAdvisingAppendable append(CharSequence csq, int start, int end) throws IOException {
      delegate.append(csq, start, end);
      buffer.append(csq, start, end);
      return this;
    }

    @Override
    public TeeAdvisingAppendable append(char c) throws IOException {
      delegate.append(c);
      buffer.append(c);
      return this;
    }

    @Override
    public boolean softLimitReached() {
      return delegate.softLimitReached();
    }

    @Override
    public String toString() {
      return buffer.toString();
    }

    @Override
    public LoggingAdvisingAppendable enterLoggableElement(LogStatement statement) {
      delegate.enterLoggableElement(statement);
      buffer.enterLoggableElement(statement);
      return this;
    }

    @Override
    public LoggingAdvisingAppendable exitLoggableElement() {
      delegate.exitLoggableElement();
      buffer.exitLoggableElement();
      return this;
    }

    @Override
    public LoggingAdvisingAppendable appendLoggingFunctionInvocation(
        LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers)
        throws IOException {
      delegate.appendLoggingFunctionInvocation(funCall, escapers);
      buffer.appendLoggingFunctionInvocation(funCall, escapers);
      return this;
    }
  }
}
