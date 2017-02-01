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

package com.google.template.soy.jbcsrc.api;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.jbcsrc.api.SoySauce.Continuation;
import com.google.template.soy.jbcsrc.api.SoySauce.WriteContinuation;
import java.io.IOException;

/** A collection of simple {@link Continuation} and {@link WriteContinuation} implementations. */
final class Continuations {
  private Continuations() {}

  /** Return a constant done {@link WriteContinuation} for successfully completed renders. */
  static WriteContinuation done() {
    return FinalContinuation.INSTANCE;
  }

  /**
   * Return a string valued continuation. Rendering logic is delegated to the {@link
   * WriteContinuation}, but it is assumed that the builder is the render target.
   */
  static Continuation<String> stringContinuation(
      WriteContinuation delegate, AdvisingStringBuilder builder) {
    if (delegate.result().isDone()) {
      return new ResultContinuation<>(builder.toString());
    }
    return new AbstractContinuation<String>(delegate, builder) {
      @Override
      Continuation<String> nextContinuation(WriteContinuation next) {
        return stringContinuation(next, builder);
      }
    };
  }

  /**
   * Return a {@link SanitizedContent} valued continuation. Rendering logic is delegated to the
   * {@link WriteContinuation}, but it is assumed that the builder is the render target.
   */
  static Continuation<SanitizedContent> strictContinuation(
      WriteContinuation delegate, AdvisingStringBuilder builder, final ContentKind kind) {
    if (delegate.result().isDone()) {
      return new ResultContinuation<>(
          UnsafeSanitizedContentOrdainer.ordainAsSafe(builder.toString(), kind));
    }
    return new AbstractContinuation<SanitizedContent>(delegate, builder) {
      @Override
      Continuation<SanitizedContent> nextContinuation(WriteContinuation next) {
        return strictContinuation(next, builder, kind);
      }
    };
  }

  /**
   * Base class for logic shared between {@link #strictContinuation} and {@link
   * #stringContinuation}.
   */
  private abstract static class AbstractContinuation<T> implements Continuation<T> {
    final WriteContinuation delegate;
    final AdvisingStringBuilder builder;

    AbstractContinuation(WriteContinuation delegate, AdvisingStringBuilder builder) {
      this.delegate = delegate;
      this.builder = builder;
    }

    @Override
    public final RenderResult result() {
      return delegate.result();
    }

    @Override
    public final T get() {
      throw new IllegalStateException("Rendering is not complete");
    }

    @Override
    public final Continuation<T> continueRender() {
      try {
        return nextContinuation(delegate.continueRender());
      } catch (IOException e) {
        throw new AssertionError("impossible", e);
      }
    }

    abstract Continuation<T> nextContinuation(WriteContinuation next);
  }

  private enum FinalContinuation implements WriteContinuation {
    INSTANCE;

    @Override
    public RenderResult result() {
      return RenderResult.done();
    }

    @Override
    public WriteContinuation continueRender() {
      throw new IllegalStateException("Rendering is already complete and cannot be continued");
    }
  }

  /** A 'done' {@link Continuation} with a non-null value */
  private static final class ResultContinuation<T> implements Continuation<T> {
    final T value;

    ResultContinuation(T value) {
      this.value = checkNotNull(value);
    }

    @Override
    public RenderResult result() {
      return RenderResult.done();
    }

    @Override
    public T get() {
      return value;
    }

    @Override
    public Continuation<T> continueRender() {
      throw new IllegalStateException("Rendering is already complete and cannot be continued");
    }
  }
}
