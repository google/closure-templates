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

  private static final long MAX_SAFE_INTEGER = (1L << 53) - 1; // 2^53 - 1
  private static final long MIN_SAFE_INTEGER = -MAX_SAFE_INTEGER;

  private NumericCoercions() {}

  public static double safeDouble(long l) {
    if (l > MAX_SAFE_INTEGER || l < MIN_SAFE_INTEGER) {
      throw new IllegalArgumentException(String.valueOf(l));
    }
    return (double) l;
  }

  public static long safeLong(double d) {
    if (d > MAX_SAFE_INTEGER || d < MIN_SAFE_INTEGER) {
      throw new IllegalArgumentException(String.valueOf(d));
    }
    return (long) d;
  }

  public static int safeInt(long l) {
    if (l > Integer.MAX_VALUE || l < Integer.MIN_VALUE) {
      throw new IllegalArgumentException(String.valueOf(l));
    }
    return (int) l;
  }

  public static int safeInt(double d) {
    return safeInt(safeLong(d));
  }
}
