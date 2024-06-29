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
import com.google.template.soy.data.SoyValue;
import javax.annotation.Nonnull;

/**
 * Abstract superclass for number data (integers and floats).
 *
 * <p>Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 * <p>Important: Even though this class is not marked 'final', do not extend this class.
 */
public abstract class NumberData extends PrimitiveData {

  /**
   * Gets the float value of this number data object. If this object is actually an integer, its
   * value will be converted to a float before being returned.
   *
   * @return The float value of this number data object.
   */
  public abstract double toFloat();

  @Override
  public long coerceToLong() {
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
  public abstract Number javaNumberValue();

  @Override
  public boolean equals(Object other) {
    return other instanceof NumberData && ((NumberData) other).toFloat() == this.toFloat();
  }

  @Override
  public int hashCode() {
    return Longs.hashCode(Double.doubleToLongBits(toFloat()));
  }

  @Override
  public final SoyValue checkNullishNumber() {
    return this;
  }
}
