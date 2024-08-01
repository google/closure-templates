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

import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.jbcsrc.api.RenderResult;
import java.io.IOException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A provider of a Soy value.
 *
 * <p>This allows for adding providers of late-resolved values (e.g. Futures) to records/maps/lists
 * that are only resolved if the values are actually retrieved. Note that each Soy value object
 * should itself be a provider (of itself).
 */
@ParametersAreNonnullByDefault
public interface SoyValueProvider {

  /**
   * Usually, this method is a no-op that simply returns this object. However, if this value needs
   * to be resolved at usage time, then this method resolves and returns the resolved value.
   *
   * @return The resolved value.
   */
  @Nonnull
  SoyValue resolve();

  /**
   * Returns {@link RenderResult#done()} if the value provider can be {@link #resolve() resolved}
   * without blocking on a future. Otherwise, returns a {@link RenderResult} that holds the future.
   *
   * <p>Note, once this method returns {@link RenderResult#done()} all future calls must also return
   * {@link RenderResult#done()}.
   *
   * <p>This method will <em>never</em> return a {@link RenderResult.Type#LIMITED limited} {@link
   * RenderResult}
   */
  @Nonnull
  RenderResult status();

  /**
   * Renders this value to the given {@link com.google.template.soy.data.LoggingAdvisingAppendable},
   * possibly partially.
   *
   * <p>This should render the exact same content as {@code resolve().render(Appendable)} but may
   * optionally detach part of the way through rendering. Note, this means that this method is
   * <em>stateful</em> and if it returns something besides {@link RenderResult#done()} then the next
   * call to this method will resume rendering from the previous point.
   *
   * @param appendable The appendable to render to.
   * @return A {@link RenderResult} that describes whether or not rendering completed. If the
   *     returned result is not {@link RenderResult#done() done}, then to complete rendering you
   *     must call this method again.
   * @throws IOException If the appendable throws an IOException
   */
  @Nonnull
  RenderResult renderAndResolve(LoggingAdvisingAppendable appendable) throws IOException;

  /**
   * Returns a SoyValueProvider whose resolved value will be {@code defaultValue} if {@code
   * delegate} is `null` or resolves to {@link UndefinedData}.
   */
  @Nonnull
  static SoyValueProvider withDefault(@Nullable SoyValueProvider delegate, SoyValue defaultValue) {
    // Allow null so callers don't have to check if, e.g., they get delegate out of a map.
    if (delegate == null) {
      return defaultValue;
    }

    if (delegate instanceof SoyValue) {
      return delegate == UndefinedData.INSTANCE ? defaultValue : delegate;
    }
    // N.B. We could eagerly call `status` here to test for being resolved but we do not because
    // 1. The jbcsrc gencode is already 'optimistically evaluating' most parameters, so it would be
    // mostly redundant.
    // 2. In the few cases we are not optimistically evaluating, This is because we are maintaining
    // compatibility with existing patterns that break under optimistic evaluation.
    return new SoyAbstractCachingValueProvider() {
      @Override
      protected SoyValue compute() {
        SoyValue value = delegate.resolve();
        return value == UndefinedData.INSTANCE ? defaultValue : value;
      }

      @Nonnull
      @Override
      public RenderResult status() {
        return delegate.status();
      }
    };
  }
}
