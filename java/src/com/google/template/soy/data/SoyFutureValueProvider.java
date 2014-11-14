/*
 * Copyright 2013 Google Inc.
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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * SoyValueProvider implementation that represents a wrapped future.
 *
 */
public final class SoyFutureValueProvider extends SoyAbstractCachingValueProvider {


  /** The instance of SoyValueHelper to use for converting the future value (after retrieval). */
  private final SoyValueHelper valueHelper;

  /** The wrapped Future object that will provide the value, if needed. */
  private final Future<?> future;

  // Callback that gets fired when the provider is about to block on a future. That is when the
  // future reports false for isDone and we are about to call get() on the future.
  // There is only one of these; adding a second one overrides the first one.
  @Nullable private FutureBlockCallback futureBlockCallback;

  /**
   * A callback that gets fired just before this provider will block on a future.
   */
  public interface FutureBlockCallback {
    void beforeBlock();
  }

  /**
   * Registers a {@link FutureBlockCallback } callback with this future provider.
   * If a callback was already registered it is overridden. The specific use case of this is
   * flushing the output stream just before blocking on a future – and since there is only one
   * output stream that actually represents the outgoing byte only one such callback is needed.
   */
  public void setOrOverrideFutureBlockCallback(FutureBlockCallback callback) {
    futureBlockCallback = callback;
  }

  /**
   * @param valueHelper The instance of SoyValueHelper to use for converting the future value
   *     (after retrieval).
   * @param future The underlying Future object.
   */
  public SoyFutureValueProvider(SoyValueHelper valueHelper, Future<?> future) {
    this.valueHelper = valueHelper;
    this.future = future;
  }

  /** Returns true if the wrapped future is done. */
  public boolean isDone() {
    return future.isDone();
  }


  /**
   * Calls Future.get() and then converts the result to SoyValue. Note that
   * this result can never return {@code null}, since null converts to
   * {@code NullData.INSTANCE}.
   */
  @Override @Nonnull protected final SoyValue compute() {
    try {
      if (!future.isDone() && futureBlockCallback != null) {
        futureBlockCallback.beforeBlock();
      }
      return valueHelper.convert(future.get()).resolve();
    } catch (ExecutionException e) {
      throw new SoyDataException("Error dereferencing future", e.getCause());
    } catch (Exception e) {
      throw new SoyDataException("Error dereferencing future", e);
    }
  }
}
