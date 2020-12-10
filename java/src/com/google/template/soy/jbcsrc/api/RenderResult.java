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

import java.util.Objects;
import java.util.concurrent.Future;
import javax.annotation.Nullable;

/** The result of an asynchronous rendering operation. */
public final class RenderResult {
  private static final RenderResult DONE_RESULT = new RenderResult(Type.DONE);
  private static final RenderResult LIMITED_RESULT = new RenderResult(Type.LIMITED);

  /**
   * Returns a {@link RenderResult} with {@linkplain RenderResult#type type} set to {@linkplain
   * Type#DONE done}.
   */
  public static RenderResult done() {
    return DONE_RESULT;
  }

  /**
   * Returns a {@link RenderResult} with {@linkplain RenderResult#type type} set to {@linkplain
   * Type#LIMITED limited}.
   */
  public static RenderResult limited() {
    return LIMITED_RESULT;
  }

  /**
   * Returns a {@link RenderResult} with {@linkplain RenderResult#type type} set to {@linkplain
   * Type#DETACH detach}.
   */
  public static RenderResult continueAfter(Future<?> future) {
    return new RenderResult(future);
  }

  /** The result type. */
  public enum Type {
    /**
     * The {@link AdvisingAppendable} that is being rendered into has indicated that its {@linkplain
     * AdvisingAppendable#softLimitReached soft limit} has been reached.
     */
    LIMITED,
    /** Rendering has encountered an incomplete future. This future will be provided */
    DETACH,
    /** Rendering has completed successfully. */
    DONE;
  }

  private final Type type;

  @Nullable
  private final Future<?> future;

  private RenderResult(Type type) {
    this.type = type;
    this.future = null;
  }

  private RenderResult(Future<?> future) {
    this.type = Type.DETACH;
    this.future = checkNotNull(future);
  }

  /** Returns the {@link Type} of this result. */
  public Type type() {
    return type;
  }

  /** Returns {@code true} if the result is done. */
  public boolean isDone() {
    return type == Type.DONE;
  }

  /**
   * Returns the future that soy is waiting for.
   *
   * @throws IllegalStateException if {@link #type()} is not {@link Type#DETACH}.
   */
  public Future<?> future() {
    Future<?> f = future;
    if (f == null) {
      throw new IllegalStateException(
          "Result.future() can only be called if type() is DETACH, type was: " + type);
    }
    return f;
  }

  @Override
  public String toString() {
    return "RenderResult{" + type + "}";
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, future);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof RenderResult) {
      RenderResult other = (RenderResult) obj;
      // Use identity matching for the future.
      return other.type.equals(type) && other.future == future;
    }
    return false;
  }
}
