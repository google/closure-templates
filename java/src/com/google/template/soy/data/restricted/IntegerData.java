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
 * Integer data.
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 * @author Kai Huang
 */
public class IntegerData extends NumberData {


  /** The integer value. */
  private final int value;


  /**
   * @param value The integer value.
   */
  public IntegerData(int value) {
    this.value = value;
  }


  /** Returns the integer value. */
  public int getValue() {
    return value;
  }


  @Override public int integerValue() {
    return value;
  }


  @Override public String toString() {
    return Integer.toString(value);
  }


  /**
   * {@inheritDoc}
   *
   * <p> 0 is falsy.
   */
  @Override public boolean toBoolean() {
    return value != 0;
  }


  @Override public double toFloat() {
    return (double) value;
  }


  @Override public boolean equals(Object other) {

    if (other == null || !(other instanceof NumberData)) {
      return false;
    }
    if (other instanceof IntegerData) {
      return value == ((IntegerData) other).getValue();
    } else {
      return super.equals(other);
    }
  }

}
