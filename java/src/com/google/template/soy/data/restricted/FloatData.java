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

import javax.annotation.concurrent.Immutable;


/**
 * Float data.
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 * @author Kai Huang
 */
@Immutable
public class FloatData extends NumberData {


  /** The float value. */
  private final double value;


  /**
   * @param value The float value.
   * @deprecated Use {@link FloatData#forValue}.
   */
  @Deprecated
  public FloatData(double value) {
    this.value = value;
  }


  /**
   * Gets a FloatData instance for the given value.
   * @param value The desired value.
   * @return A FloatData instance with the given value.
   */
  public static FloatData forValue(double value) {
    return new FloatData(value);
  }


  /** Returns the float value. */
  public double getValue() {
    return value;
  }


  @Override public double floatValue() {
    return value;
  }


  @Override public String toString() {
    // Note: This differs from JS in how it rendered values that have a zero fractional component
    // and in how it renders the value -0.0.
    // JavaScript specifies that the string form of -0 is signless, and that the string form of
    // fractionless numeric values has no decimal point.
    return Double.toString(value).replace('E', 'e');
  }


  /**
   * {@inheritDoc}
   *
   * <p> 0.0 is falsy as is NaN.
   */
  @Override public boolean toBoolean() {
    return value != 0.0 && !Double.isNaN(value);
  }


  @Override public double toFloat() {
    return value;
  }

}
