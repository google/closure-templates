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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.Future;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;


/**
 * A provider of a Soy value.
 *
 * <p>This allows for adding providers of late-resolved values (e.g. Futures) to records/maps/lists
 * that are only resolved if the values are actually retrieved. Note that each Soy value object
 * should itself be a provider (of itself).
 *
 * <p>Important: Until this API is more stable and this note is removed, users must not define
 * classes that implement this interface.
 *
 */
@ParametersAreNonnullByDefault
public interface SoyValueProvider {

  /**
   * A value that indicates whether {@link SoyValueProvider#resolve()} can be called without
   * blocking.
   */
  abstract class ResolveStatus {
    private static final ResolveStatus RESOLVABLE = new ResolveStatus() {
      @Override public boolean isReady() {
        return true;
      }

      @Override public Future<?> future() {
        throw new IllegalStateException("Result.future() can only be called if isDone() is false.");
      }
    };

    /**
     * Returns a {@link ResolveStatus} that indicates that {@link SoyValueProvider#resolve()} can be
     * called without blocking.
     */
    public static ResolveStatus ready() {
      return RESOLVABLE;
    }

    /**
     * Returns a {@link ResolveStatus} that indicates that {@link SoyValueProvider#resolve()} cannot
     * be called until the given future is {@link Future#isDone() done}.
     */
    public static ResolveStatus resolveAfter(final Future<?> future) {
      checkNotNull(future);
      return new ResolveStatus() {
        @Override public boolean isReady() {
          return false;
        }

        @Override public Future<?> future() {
          return future;
        }
      };
    }

    private ResolveStatus() {}  // prevent other subclasses

    /**
     * Returns the future that must be done prior to calling resolve.
     *
     * @throws IllegalStateException if {@link #isReady()} returns {@code true}
     */
    public abstract Future<?> future();

    /** Returns {@code true} if the {@link SoyValueProvider} can be resolved without blocking. */
    public abstract boolean isReady();
  }

  /**
   * Usually, this method is a no-op that simply returns this object. However, if this value needs
   * to be resolved at usage time, then this method resolves and returns the resolved value.
   * @return The resolved value.
   */
  @Nonnull public SoyValue resolve();

  /**
   * Returns {@link ResolveStatus#isReady()} if the value provider can be
   * {@link #resolve() resolved} without blocking on a future.  Otherwise, returns a
   * {@link ResolveStatus} that holds the future.
   *
   * <p>Note, once this method returns {@link ResolveStatus#isReady()} all future calls must also
   * return {@link ResolveStatus#isReady()}.
   */
  @Nonnull public ResolveStatus status();

  /**
   * Compares this value against another for equality for the purposes of Soy.
   * @param other The other value to compare against.
   * @return True if the two values are equal.
   */
  public boolean equals(SoyValueProvider other);

}
