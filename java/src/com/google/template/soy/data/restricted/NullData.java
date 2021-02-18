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
import com.google.template.soy.data.SoyDataException;

/**
 * Null data.
 *
 * <p>Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 * <p>NullData is only used by Tofu, jbcsrc internally represents null as {@code null}. Ideally,
 * Tofu would switch to using {@code null} also, but that may be infeasible.
 *
 */
@Immutable
public final class NullData extends PrimitiveData {

  /** Static singleton instance of NullData. */
  public static final NullData INSTANCE = new NullData();

  private NullData() {}

  @Override
  public String toString() {
    return "null";
  }

  /**
   * {@inheritDoc}
   *
   * <p>Null is falsy.
   */
  @Override
  public boolean coerceToBoolean() {
    return false;
  }

  @Override
  public String coerceToString() {
    return toString();
  }

  @Override
  public int integerValue() {
    throw new SoyDataException("'null' cannot be coerced to integer");
  }

  @Override
  public long longValue() {
    throw new SoyDataException("'null' cannot be coerced to long");
  }

  @Override
  public double floatValue() {
    throw new SoyDataException("'null' cannot be coerced to float");
  }

  @Override
  public double numberValue() {
    throw new SoyDataException("'null' cannot be coerced to number");
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof PrimitiveData) {
      return other == UndefinedData.INSTANCE || other == NullData.INSTANCE;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
