/*
 * Copyright 2017 Google Inc.
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

package com.google.template.soy.bididirectives;

/**
 * Tracks a value of an enum over SanitizedContent enter calls. If the same enum value is set for
 * all enter calls, then {@link #get} will return that, otherwise it will return null.
 */
public final class EnumTracker<E extends Enum<E>> {

  /** The overall enum value so far. Each new value gets merged into this on an enter call. */
  private E value;
  /**
   * Whether enter has been called yet. This is used to differentiate a null {@link #value} meaning
   * enter hasn't been called yet vs. enter has been called for multiple enum values (so the overall
   * enum value is unknown).
   */
  private boolean enterCalled;

  /** Tracks a single enter call for the given value. */
  public void trackEnter(E e) {
    if (!enterCalled) {
      value = e;
      enterCalled = true;
    } else if (value != e) {
      value = null;
    }
  }

  /** Gets the combined value over all {@link #trackEnter} calls. */
  public E get() {
    return value;
  }
}
