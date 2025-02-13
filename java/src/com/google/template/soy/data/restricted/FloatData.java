/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.data.restricted;

import com.google.errorprone.annotations.Immutable;
import javax.annotation.Nonnull;

/** Float data. */
@Immutable
@Deprecated
public final class FloatData extends NumberData {

  private FloatData(double value) {
    super(value);
  }

  public double getValue() {
    return floatValue();
  }

  /**
   * Gets a FloatData instance for the given value.
   *
   * @param value The desired value.
   * @return A FloatData instance with the given value.
   */
  @Nonnull
  public static FloatData forValue(double value) {
    return new FloatData(value);
  }
}
