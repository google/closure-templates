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


/**
 * Float data.
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 * @author Kai Huang
 */
public class FloatData extends NumberData {


  /** The float value. */
  private final double value;


  /**
   * @param value The float value.
   */
  public FloatData(double value) {
    this.value = value;
  }


  /** Returns the float value. */
  public double getValue() {
    return value;
  }


  @Override public double floatValue() {
    return value;
  }


  @Override public String toString() {
    return Double.toString(value).replace('E', 'e');
  }


  /**
   * {@inheritDoc}
   *
   * <p> 0.0 is falsy.
   */
  @Override public boolean toBoolean() {
    return value != 0.0;
  }


  @Override public double toFloat() {
    return value;
  }

}
