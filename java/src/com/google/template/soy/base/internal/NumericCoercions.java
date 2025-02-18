/*
 * Copyright 2025 Google Inc.
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

/** Numeric coercions compatible with JavaScript number. */
public final class NumericCoercions {

  // JavaScript Number.MAX_SAFE_INTEGER:
  // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/MAX_SAFE_INTEGER
  private static final long JS_MAX_SAFE_INTEGER = (1L << 53) - 1;
  private static final long JS_MIN_SAFE_INTEGER = -1 * JS_MAX_SAFE_INTEGER;

  private NumericCoercions() {}

  /**
   * Converts a double to a long by truncating. Throws an exception if the double is out of the safe
   * JavaScript integer range.
   */
  public static long safeLong(double d) {
    if (d > JS_MAX_SAFE_INTEGER || d < JS_MIN_SAFE_INTEGER) {
      throw new IllegalArgumentException(String.valueOf(d));
    }
    return (long) d;
  }

  /**
   * Converts a long to a int. Throws an exception if the long is out of the 32-bit integer range.
   */
  public static int safeInt(long l) {
    if (l > Integer.MAX_VALUE || l < Integer.MIN_VALUE) {
      throw new IllegalArgumentException(String.valueOf(l));
    }
    return (int) l;
  }

  /**
   * Converts a double to a int. Throws an exception if the double is out of the 32-bit integer
   * range.
   */
  public static int safeInt(double d) {
    return safeInt(safeLong(d));
  }

  /** Returns true if {@code value} is within JavaScript safe range. */
  public static boolean isInRange(long value) {
    return JS_MIN_SAFE_INTEGER <= value && value <= JS_MAX_SAFE_INTEGER;
  }
}
