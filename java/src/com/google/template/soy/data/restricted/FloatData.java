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
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.data.SoyValue;
import javax.annotation.Nonnull;

/** Float data. */
@Immutable
public final class FloatData extends NumberData {

  /** The float value. */
  private final double value;

  private FloatData(double value) {
    this.value = value;
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

  /** Returns the float value. */
  public double getValue() {
    return value;
  }

  @Override
  public double floatValue() {
    return value;
  }

  @Override
  @Nonnull
  public String toString() {
    return BaseUtils.formatDouble(value);
  }

  /**
   * {@inheritDoc}
   *
   * <p>0.0 is falsy as is NaN.
   */
  @Override
  public boolean coerceToBoolean() {
    return value != 0.0 && !Double.isNaN(value);
  }

  @Override
  public String coerceToString() {
    return toString();
  }

  @Override
  public double toFloat() {
    return value;
  }

  @Override
  public Number javaNumberValue() {
    return value;
  }

  @Override
  public SoyValue checkNullishFloat() {
    return this;
  }

  @Override
  public long coerceToIndex() {
    long longVal = (long) value;
    if (value == longVal) {
      return longVal;
    }
    return super.coerceToIndex();
  }

  @Override
  public String getSoyTypeName() {
    return "float";
  }
}
