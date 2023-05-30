/*
 * Copyright 2023 Google Inc.
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

package com.google.template.soy.base.internal;

import java.util.Optional;

/** A wrapper around a single non-null value that can be set only once. */
public final class SetOnce<T> {

  private Optional<T> value = Optional.empty();

  /**
   * Sets the value. The value can not be null.
   *
   * @throws IllegalStateException if {@code set()} has already been called.
   */
  public void set(T newValue) {
    if (value.isEmpty()) {
      value = Optional.of(newValue);
    } else {
      throw new IllegalStateException(
          "SetOnce object can be only set once. Existing value: "
              + value.get()
              + ". Attempting to set with: "
              + newValue);
    }
  }

  public SetOnce<T> copy() {
    SetOnce<T> copy = new SetOnce<>();
    if (value.isPresent()) {
      copy.set(value.get());
    }
    return copy;
  }

  /**
   * Returns the value if it has been set.
   *
   * @throws IllegalStateException if {@code set()} has not been called.
   */
  public T get() {
    return value.orElseThrow(
        () -> new IllegalStateException("SetOnce object has not been set yet."));
  }

  /** Returns whether the value has been set yet. */
  public boolean isPresent() {
    return value.isPresent();
  }
}
