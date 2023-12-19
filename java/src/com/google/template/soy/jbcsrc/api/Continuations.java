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
import com.google.template.soy.jbcsrc.api.SoySauce.Continuation;
import com.google.template.soy.jbcsrc.api.SoySauce.WriteContinuation;

/** A collection of simple {@link Continuation} and {@link WriteContinuation} implementations. */
public final class Continuations {
  private Continuations() {}

  /** Return a constant done {@link WriteContinuation} for successfully completed renders. */
  public static WriteContinuation done() {
    return FinalContinuation.INSTANCE;
  }

  public static Continuation<String> done(String value) {
    return new ResultContinuation<>(value);
  }

  public static Continuation<SanitizedContent> done(SanitizedContent value) {
    return new ResultContinuation<>(value);
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
  private static final class ResultContinuation<
          T>
      implements Continuation<T> {
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
