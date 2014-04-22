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


/**
 * A SoyValueProvider that lazily computes and caches its value.
 *
 * <p>SoyAbstractCachingValueProvider is thread-safe, but in a race condition, may compute its
 * value twice.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Garrett Boyer
 */
public abstract class SoyAbstractCachingValueProvider implements SoyValueProvider {

  /**
   * The resolved value.
   * <p>
   * This will be set to non-null the first time this is resolved. Note that SoyValue must not be
   * null. This is volatile to indicate it will be tested and set atomically across threads.
   */
  private volatile SoyValue resolvedValue = null;


  @Override public final SoyValue resolve() {
    // NOTE: If this is used across threads, the worst that will happen is two different providers
    // will be constructed, and an arbitrary one will win. Since setting a volatile reference is
    // atomic, however, this is thread-safe. We keep a local cache here to avoid doing a memory
    // read more than once in the cached case.
    SoyValue localResolvedValue = resolvedValue;
    if (localResolvedValue == null) {
      localResolvedValue = compute();
      resolvedValue = localResolvedValue;
    }
    return localResolvedValue;
  }


  @Override public boolean equals(SoyValueProvider other) {
    // NOTE: The identity check is essential. If the underlying SoyValue type requires instance
    // equality, and resolve() is called by two different threads, it's possible that resolve()
    // will return two different instances.
    return this == other || (other != null && resolve().equals(other.resolve()));
  }


  @Override public boolean equals(Object other) {
    if (other instanceof SoyValueProvider) {
      return equals((SoyValueProvider) other);
    } else {
      return false;
    }
  }


  @Override public int hashCode() {
    throw new UnsupportedOperationException(
        "SoyAbstractCachingValueProvider is unsuitable for use as a hash key.");
  }


  /**
   * Implemented by subclasses to do the heavy-lifting for resolving.
   */
  protected abstract SoyValue compute();
}
