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
 * Boolean data.
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 * @author Kai Huang
 */
@Immutable
public class BooleanData extends PrimitiveData {


  /** Static instance of BooleanData with value 'true'. */
  public static final BooleanData TRUE = new BooleanData(true);

  /** Static instance of BooleanData with value 'false'. */
  public static final BooleanData FALSE = new BooleanData(false);


  /** The boolean value. */
  private final boolean value;


  /**
   * @param value The boolean value.
   * @deprecated Use {@link BooleanData#TRUE}, {@link BooleanData#FALSE}, or
   *     {@link BooleanData#forValue}.
   */
  @Deprecated
  public BooleanData(boolean value) {
    this.value = value;
  }


  /**
   * Gets a BooleanData instance for the given value.
   * @param value The desired value.
   * @return A BooleanData instance with the given value.
   */
  public static BooleanData forValue(boolean value) {
    return value ? TRUE : FALSE;
  }


  /** Returns the boolean value. */
  public boolean getValue() {
    return value;
  }


  @Override public boolean booleanValue() {
    return value;
  }


  @Override public String toString() {
    return Boolean.toString(value);
  }


  @Override public boolean toBoolean() {
    return value;
  }


  @Override public boolean equals(Object other) {
    return other != null && other.getClass() == BooleanData.class &&
           ((BooleanData) other).getValue() == value;
  }


  @Override public int hashCode() {
    return (new Boolean(value)).hashCode();
  }

}
