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

import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SoyValue;
import java.io.IOException;
import java.math.BigInteger;
import javax.annotation.Nonnull;

/**
 * gbigint data.
 *
 * <p>Important: This class may only be used in implementing plugins (e.g. functions, directives).
 */
@Immutable
public final class GbigintData extends PrimitiveData {
  public static final BigInteger LONG_MIN_VALUE = BigInteger.valueOf(Long.MIN_VALUE);
  public static final BigInteger LONG_MAX_VALUE = BigInteger.valueOf(Long.MAX_VALUE);
  public static final BigInteger UINT64_MAX_VALUE = new BigInteger("18446744073709551615", 10);
  public static final BigInteger INT52_MAX_VALUE = BigInteger.valueOf(9007199254740991L);
  public static final BigInteger INT52_MIN_VALUE = BigInteger.valueOf(-9007199254740991L);

  /** The integer value. */
  private final BigInteger value;

  private GbigintData(BigInteger value) {
    this.value = value;
  }

  /**
   * Gets a GbigintData instance for the given value.
   *
   * @param value The desired value.
   * @return A GbigintData instance with the given value.
   */
  @Nonnull
  public static GbigintData forValue(BigInteger value) {
    return new GbigintData(value);
  }

  @Nonnull
  public static GbigintData forValue(long value) {
    return new GbigintData(BigInteger.valueOf(value));
  }

  @Nonnull
  public static GbigintData forUnsignedLongValue(long value) {
    if (value >= 0) {
      return new GbigintData(BigInteger.valueOf(value));
    }
    return new GbigintData(UnsignedLong.fromLongBits(value).bigIntegerValue());
  }

  public static GbigintData forStringValue(String value) {
    return GbigintData.forValue(new BigInteger(value, 10));
  }

  public BigInteger getValue() {
    return value;
  }

  @Override
  public long longValue() {
    return value.longValue();
  }

  public long unsignedLongValue() {
    return UnsignedLong.valueOf(value).longValue();
  }

  @Override
  @Nonnull
  public String toString() {
    return String.valueOf(value);
  }

  /**
   * {@inheritDoc}
   *
   * <p>0 is falsy.
   */
  @Override
  public boolean coerceToBoolean() {
    throw new UnsupportedOperationException(
        "gbigint must be coerced to boolean via gbigintToBoolean.");
  }

  @Override
  public String coerceToString() {
    return toString();
  }

  @Override
  public final SoyValue checkNullishGbigint() {
    return this;
  }

  @Override
  public void render(LoggingAdvisingAppendable appendable) throws IOException {
    appendable.append(coerceToString());
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof GbigintData) {
      return this.value.equals(((GbigintData) other).value);
    }
    return false;
  }

  @Override
  public SoyValue checkNullishInt() {
    return this;
  }

  @Override
  public String getSoyTypeName() {
    return "gbigint";
  }

  public boolean isValidSignedInt64() {
    return LONG_MIN_VALUE.compareTo(value) <= 0 && LONG_MAX_VALUE.compareTo(value) >= 0;
  }

  public boolean isValidUnsignedInt64() {
    return BigInteger.ZERO.compareTo(value) <= 0 && UINT64_MAX_VALUE.compareTo(value) >= 0;
  }

  public boolean isSafeInt52() {
    return INT52_MIN_VALUE.compareTo(value) <= 0 && INT52_MAX_VALUE.compareTo(value) >= 0;
  }
}
