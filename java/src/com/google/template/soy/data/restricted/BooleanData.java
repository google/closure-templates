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

import com.google.common.primitives.Booleans;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import javax.annotation.Nonnull;

/** Boolean data. */
@Immutable
public final class BooleanData extends PrimitiveData {

  /** Static instance of BooleanData with value 'true'. */
  public static final BooleanData TRUE = new BooleanData(true);

  /** Static instance of BooleanData with value 'false'. */
  public static final BooleanData FALSE = new BooleanData(false);

  /** The boolean value. */
  private final boolean value;

  /**
   * @param value The boolean value.
   */
  private BooleanData(boolean value) {
    this.value = value;
  }

  /**
   * Gets a BooleanData instance for the given value.
   *
   * @param value The desired value.
   * @return A BooleanData instance with the given value.
   */
  @Nonnull
  public static BooleanData forValue(boolean value) {
    return value ? TRUE : FALSE;
  }

  @Override
  public SoyValueProvider coerceToBooleanProvider() {
    return this;
  }

  /** Returns the boolean value. */
  public boolean getValue() {
    return value;
  }

  @Override
  public boolean booleanValue() {
    return value;
  }

  @Override
  public String toString() {
    return Boolean.toString(value);
  }

  @Override
  public boolean coerceToBoolean() {
    return value;
  }

  @Override
  public String coerceToString() {
    return toString();
  }

  @Override
  public boolean equals(Object other) {
    return this == other;
  }

  @Override
  public int hashCode() {
    return Booleans.hashCode(value);
  }

  @Override
  public SoyValue checkNullishBoolean() {
    return this;
  }

  @Override
  public String getSoyTypeName() {
    return "bool";
  }
}
