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
 * Integer data.
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 */
@Immutable
public class IntegerData extends NumberData {


  // Note: ZERO, ONE, and MINUS_ONE are public. The rest are private.

  /** Static instance of BooleanData with value 0. */
  public static final IntegerData ZERO = new IntegerData(0);

  /** Static instance of BooleanData with value 1. */
  public static final IntegerData ONE = new IntegerData(1);

  /** Static instance of BooleanData with value -1. */
  public static final IntegerData MINUS_ONE = new IntegerData(-1);

  /** Static instance of BooleanData with value 2. */
  private static final IntegerData TWO = new IntegerData(2);

  /** Static instance of BooleanData with value 3. */
  private static final IntegerData THREE = new IntegerData(3);

  /** Static instance of BooleanData with value 4. */
  private static final IntegerData FOUR = new IntegerData(4);

  /** Static instance of BooleanData with value 5. */
  private static final IntegerData FIVE = new IntegerData(5);

  /** Static instance of BooleanData with value 6. */
  private static final IntegerData SIX = new IntegerData(6);

  /** Static instance of BooleanData with value 7. */
  private static final IntegerData SEVEN = new IntegerData(7);

  /** Static instance of BooleanData with value 8. */
  private static final IntegerData EIGHT = new IntegerData(8);

  /** Static instance of BooleanData with value 9. */
  private static final IntegerData NINE = new IntegerData(9);

  /** Static instance of BooleanData with value 10. */
  private static final IntegerData TEN = new IntegerData(10);


  /** The integer value. */
  private final int value;


  /**
   * @param value The integer value.
   * @deprecated Use {@link IntegerData#ZERO}, {@link IntegerData#ONE},
   *     {@link IntegerData#MINUS_ONE}, or {@link IntegerData#forValue}.
   */
  @Deprecated
  public IntegerData(int value) {
    this.value = value;
  }


  /**
   * Gets a IntegerData instance for the given value.
   * @param value The desired value.
   * @return A IntegerData instance with the given value.
   */
  public static IntegerData forValue(int value) {
    switch (value) {
      case -1: return MINUS_ONE;
      case 0: return ZERO;
      case 1: return ONE;
      case 2: return TWO;
      case 3: return THREE;
      case 4: return FOUR;
      case 5: return FIVE;
      case 6: return SIX;
      case 7: return SEVEN;
      case 8: return EIGHT;
      case 9: return NINE;
      case 10: return TEN;
      default: return new IntegerData(value);
    }
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
    return value;
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
