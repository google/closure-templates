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

import com.google.template.soy.jbcsrc.api.RenderResult;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.Nonnull;

/**
 * Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p>SoyValueProvider implementation that represents a wrapped future.
 *
 */
public final class SoyFutureValueProvider extends SoyAbstractCachingValueProvider {
  private static final FutureBlockCallback NOOP =
      new FutureBlockCallback() {
        @Override
        public void beforeBlock() {}
      };

  /**
   * Allows threads to register a {@link FutureBlockCallback}.
   *
   * <p>When calling {@link #resolve()} on this {@link SoyFutureValueProvider}, if the thread needs
   * to block on the future (because {@link Future#isDone()} is {@code false}), then it will call
   * the currently registered block callback immediately prior blocking. See {@code
   * RenderVisitor.exec} for the motivating usecase for this hook.
   *
   * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
   */
  public static final ThreadLocal<FutureBlockCallback> futureBlockCallback =
      new ThreadLocal<FutureBlockCallback>() {
        @Override
        protected FutureBlockCallback initialValue() {
          return NOOP;
        }
      };


  /** The wrapped Future object that will provide the value, if needed. */
  private final Future<?> future;

  /** A callback that gets fired just before this provider will block on a future. */
  public interface FutureBlockCallback {
    void beforeBlock();
  }

  /**
   * @param valueConverter The instance of SoyValueConverter to use for converting the future value
   *     (after retrieval).
   * @param future The underlying Future object.
   */
  public SoyFutureValueProvider(Future<?> future) {
    this.future = future;
  }

  @Override
  public RenderResult status() {
    return future.isDone() ? RenderResult.done() : RenderResult.continueAfter(future);
  }

  /**
   * Calls Future.get() and then converts the result to SoyValue. Note that this result can never
   * return {@code null}, since null converts to {@code NullData.INSTANCE}.
   */
  @Override
  @Nonnull
  protected final SoyValue compute() {
    try {
      if (!future.isDone()) {
        futureBlockCallback.get().beforeBlock();
      }
      return SoyValueConverter.INSTANCE.convert(future.get()).resolve();
    } catch (ExecutionException e) {
      throw new SoyFutureException(e.getCause());
    } catch (Throwable e) {
      throw new SoyFutureException(e);
    }
  }
}
