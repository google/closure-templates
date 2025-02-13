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

import com.google.common.primitives.Longs;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.data.SoyValue;
import javax.annotation.Nonnull;

/** Abstract superclass for number data (integers and floats). */
public class NumberData extends PrimitiveData {

  /** The float value. */
  private final double value;

  protected NumberData(double value) {
    this.value = value;
  }

  /**
   * Gets a FloatData instance for the given value.
   *
   * @param value The desired value.
   * @return A FloatData instance with the given value.
   */
  @Nonnull
  public static NumberData forValue(double value) {
    return new NumberData(value);
  }

  public static NumberData forValue(Number value) {
    return new NumberData(value.doubleValue());
  }

  @Override
  public double floatValue() {
    return value;
  }

  public int integerValue() {
    return coerceToInt();
  }

  /**
   * Gets the float value of this number data object. If this object is actually an integer, its
   * value will be converted to a float before being returned.
   *
   * @return The float value of this number data object.
   */
  public double toFloat() {
    return value;
  }

  @Override
  public long coerceToLong() {
    return longValue();
  }

  @Override
  public long longValue() {
    return javaNumberValue().longValue();
  }

  public int coerceToInt() {
    long l = coerceToLong();
    if (l > Integer.MAX_VALUE || l < Integer.MIN_VALUE) {
      throw new IllegalArgumentException();
    }
    return (int) l;
  }

  @Override
  public double numberValue() {
    return toFloat();
  }

  @Nonnull
  public Number javaNumberValue() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof NumberData && ((NumberData) other).value == this.value;
  }

  @Override
  public int hashCode() {
    return Longs.hashCode(Double.doubleToLongBits(toFloat()));
  }

  @Override
  public SoyValue checkNullishNumber() {
    return this;
  }

  @Override
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
  public String getSoyTypeName() {
    return "number";
  }
}
