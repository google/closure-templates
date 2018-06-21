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

import com.google.common.base.Preconditions;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.api.RenderResult.Type;
import java.io.IOException;
import javax.annotation.Nullable;

/**
 * A SoyValueProvider that lazily computes and caches its value.
 *
 * <p>SoyAbstractCachingValueProvider is thread-safe, but in a race condition, may compute its value
 * twice.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public abstract class SoyAbstractCachingValueProvider implements SoyValueProvider {

  /**
   * A mechanism to plug in assertions on the computed value that will be run the first time the
   * value is {@link SoyAbstractCachingValueProvider#compute() computed}.
   */
  public abstract static class ValueAssertion {
    private ValueAssertion next;

    public abstract void check(SoyValue value);
  }

  /**
   * The resolved value.
   *
   * <p>This will be set to non-null the first time this is resolved. Note that SoyValue must not be
   * null. This is volatile to indicate it will be tested and set atomically across threads.
   */
  private volatile SoyValue resolvedValue = null;

  // We thread a simple linked list through this field to eliminate the cost of allocating a
  // collection
  @Nullable private ValueAssertion valueAssertion;

  @Override
  public final SoyValue resolve() {
    // NOTE: If this is used across threads, the worst that will happen is two different providers
    // will be constructed, and an arbitrary one will win. Since setting a volatile reference is
    // atomic, however, this is thread-safe. We keep a local cache here to avoid doing a memory
    // read more than once in the cached case.
    SoyValue localResolvedValue = resolvedValue;
    if (localResolvedValue == null) {
      localResolvedValue = compute();
      for (ValueAssertion curr = valueAssertion; curr != null; curr = curr.next) {
        curr.check(localResolvedValue);
      }
      resolvedValue = localResolvedValue;
      valueAssertion = null;
    }
    return localResolvedValue;
  }

  @Override
  public RenderResult renderAndResolve(LoggingAdvisingAppendable appendable, boolean isLast)
      throws IOException {
    // Gives a reasonable default implementation, if subclasses can do better they can override.
    RenderResult result = status();
    if (result.type() == Type.DONE) {
      resolve().render(appendable);
    }
    return result;
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException(
        "SoyAbstractCachingValueProvider is unsuitable for use as a hash key.");
  }

  /** Returns {@code true} if the caching provider has already been calculated. */
  public final boolean isComputed() {
    return resolvedValue != null;
  }

  /** Registers a {@link ValueAssertion} callback with this caching provider. */
  public void addValueAssertion(ValueAssertion assertion) {
    Preconditions.checkState(
        resolvedValue == null,
        "ValueAssertions should only be registered if the value is not yet computed.");
    assertion.next = valueAssertion;
    valueAssertion = assertion;
  }

  /** Implemented by subclasses to do the heavy-lifting for resolving. */
  protected abstract SoyValue compute();
}
