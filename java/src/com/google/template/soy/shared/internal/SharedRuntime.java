/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.shared.internal;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.RecordProperty;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.internal.SoyMapImpl;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import java.util.Iterator;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Runtime implementation of common expression operators to be shared between the {@code jbcsrc} and
 * {@code Tofu} backends.
 */
public final class SharedRuntime {
  /**
   * Custom equality operator that smooths out differences between different Soy runtimes.
   *
   * <p>This approximates Javascript's behavior, but is much easier to understand.
   */
  public static boolean equal(SoyValue operand0, SoyValue operand1) {
    // Treat the case where either is a string specially.
    // TODO(gboyer): This should probably handle SanitizedContent == SanitizedContent, even though
    // Javascript doesn't handle that case properly. http://b/21461181
    if (operand0 instanceof StringData) {
      return compareString(operand0.stringValue(), operand1);
    }
    if (operand1 instanceof StringData) {
      return compareString(operand1.stringValue(), operand0);
    }
    if ((operand0 == null || operand0.isNullish()) && (operand1 == null || operand1.isNullish())) {
      return true;
    }
    return Objects.equals(operand0, operand1);
  }

  /**
   * Custom strict equality operator that smooths out differences between different Soy runtimes.
   */
  public static boolean tripleEqual(SoyValue operand0, SoyValue operand1) {
    if (operand0 instanceof BooleanData && operand1 instanceof BooleanData) {
      return operand0.booleanValue() == operand1.booleanValue();
    }
    if (operand0 instanceof NumberData && operand1 instanceof NumberData) {
      return operand0.numberValue() == operand1.numberValue();
    }
    if (operand0 instanceof StringData && operand1 instanceof StringData) {
      return operand0.stringValue().equals(operand1.stringValue());
    }
    return operand0 == operand1;
  }

  /**
   * Same as {@link #tripleEqual} except it has special handling for sanitized types and strings.
   */
  public static boolean switchCaseEqual(SoyValue operand0, SoyValue operand1) {
    if (operand0 instanceof SanitizedContent) {
      operand0 = StringData.forValue(operand0.toString());
    }
    if (operand1 instanceof SanitizedContent) {
      operand1 = StringData.forValue(operand1.toString());
    }
    return tripleEqual(operand0, operand1);
  }

  private static boolean isNullishInteger(SoyValue value) {
    return value instanceof IntegerData || value.isNullish();
  }

  private static boolean isNullishNumber(SoyValue value) {
    return isNullishInteger(value) || value instanceof NumberData;
  }

  /** Performs the {@code +} operator on the two values. */
  @Nonnull
  public static SoyValue plus(SoyValue operand0, SoyValue operand1) {
    if (isNullishInteger(operand0) && isNullishInteger(operand1)) {
      return IntegerData.forValue(toLongForNumericOp(operand0) + toLongForNumericOp(operand1));
    } else if (isNullishNumber(operand0) && isNullishNumber(operand1)) {
      return FloatData.forValue(toDoubleForNumericOp(operand0) + toDoubleForNumericOp(operand1));
    } else {
      // String concatenation is the fallback for other types (like in JS). Use the implemented
      // coerceToString() for the type.
      return StringData.forValue(operand0.coerceToString() + operand1.coerceToString());
    }
  }

  /** Performs the {@code -} operator on the two values. */
  @Nonnull
  public static SoyValue minus(SoyValue operand0, SoyValue operand1) {
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return IntegerData.forValue(toLongForNumericOp(operand0) - toLongForNumericOp(operand1));
    } else {
      return FloatData.forValue(toDoubleForNumericOp(operand0) - toDoubleForNumericOp(operand1));
    }
  }

  /** Performs the {@code *} operator on the two values. */
  @Nonnull
  public static NumberData times(SoyValue operand0, SoyValue operand1) {
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return IntegerData.forValue(toLongForNumericOp(operand0) * toLongForNumericOp(operand1));
    } else {
      return FloatData.forValue(toDoubleForNumericOp(operand0) * toDoubleForNumericOp(operand1));
    }
  }

  /** Performs the {@code /} operator on the two values. */
  public static NumberData dividedBy(SoyValue operand0, SoyValue operand1) {
    // Note: Soy always performs floating-point division, even on two integers (like JavaScript).
    // Note that this *will* lose precision for longs.
    return FloatData.forValue(toDoubleForNumericOp(operand0) / toDoubleForNumericOp(operand1));
  }

  /** Performs the {@code %} operator on the two values. */
  @Nonnull
  public static NumberData mod(SoyValue operand0, SoyValue operand1) {
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return IntegerData.forValue(toLongForNumericOp(operand0) % toLongForNumericOp(operand1));
    } else {
      return FloatData.forValue(toDoubleForNumericOp(operand0) % toDoubleForNumericOp(operand1));
    }
  }

  @Nonnull
  public static NumberData shiftRight(SoyValue operand0, SoyValue operand1) {
    return IntegerData.forValue(toLongForNumericOp(operand0) >> (int) toLongForNumericOp(operand1));
  }

  @Nonnull
  public static NumberData shiftLeft(SoyValue operand0, SoyValue operand1) {
    return IntegerData.forValue(toLongForNumericOp(operand0) << (int) toLongForNumericOp(operand1));
  }

  @Nonnull
  public static NumberData bitwiseOr(SoyValue operand0, SoyValue operand1) {
    return IntegerData.forValue(toLongForNumericOp(operand0) | toLongForNumericOp(operand1));
  }

  @Nonnull
  public static NumberData bitwiseXor(SoyValue operand0, SoyValue operand1) {
    return IntegerData.forValue(toLongForNumericOp(operand0) ^ toLongForNumericOp(operand1));
  }

  @Nonnull
  public static NumberData bitwiseAnd(SoyValue operand0, SoyValue operand1) {
    return IntegerData.forValue(toLongForNumericOp(operand0) & toLongForNumericOp(operand1));
  }

  /** Performs the {@code <} operator on the two values. */
  public static boolean lessThan(SoyValue left, SoyValue right) {
    if (left instanceof StringData && right instanceof StringData) {
      return left.stringValue().compareTo(right.stringValue()) < 0;
    } else if (left instanceof IntegerData && right instanceof IntegerData) {
      return left.longValue() < right.longValue();
    } else if (left instanceof UndefinedData || right instanceof UndefinedData) {
      return false;
    } else {
      return toDoubleForNumericOp(left) < toDoubleForNumericOp(right);
    }
  }

  private static double toDoubleForNumericOp(SoyValue value) {
    if (value instanceof NullData) {
      return 0;
    }
    if (value instanceof UndefinedData) {
      throw new SoyDataException("'undefined' cannot be coerced to float");
    }
    return value.numberValue();
  }

  private static long toLongForNumericOp(SoyValue value) {
    if (value instanceof NullData) {
      return 0;
    }
    if (value instanceof UndefinedData) {
      throw new SoyDataException("'undefined' cannot be coerced to long");
    }
    if (value instanceof FloatData) {
      return (long) value.floatValue();
    }
    return value.longValue();
  }

  /** Performs the {@code <=} operator on the two values. */
  public static boolean lessThanOrEqual(SoyValue left, SoyValue right) {
    if (left instanceof StringData && right instanceof StringData) {
      return left.stringValue().compareTo(right.stringValue()) <= 0;
    } else if (left instanceof IntegerData && right instanceof IntegerData) {
      return left.longValue() <= right.longValue();
    } else if (left instanceof UndefinedData || right instanceof UndefinedData) {
      return false;
    } else {
      return toDoubleForNumericOp(left) <= toDoubleForNumericOp(right);
    }
  }

  /** Performs the unary negation {@code -} operator on the value. */
  @Nonnull
  public static NumberData negative(SoyValue node) {
    if (node instanceof IntegerData) {
      return IntegerData.forValue(node instanceof NullData ? 0 : -node.longValue());
    } else {
      return FloatData.forValue(node instanceof NullData ? 0 : -node.floatValue());
    }
  }

  /** Determines if the operand's string form can be equality-compared with a string. */
  public static boolean compareString(String string, SoyValue other) {
    // This follows similarly to the Javascript specification, to ensure similar operation
    // over Javascript and Java: http://www.ecma-international.org/ecma-262/5.1/#sec-11.9.3
    if (other instanceof StringData || other instanceof SanitizedContent) {
      return string.equals(other.toString());
    }
    if (other instanceof NumberData) {
      try {
        // Parse the string as a number.
        return Double.parseDouble(string) == other.numberValue();
      } catch (NumberFormatException nfe) {
        // Didn't parse as a number.
        return false;
      }
    }
    return false;
  }

  /** calculates a $soyServerKey value. This should be compatible with the JS implementation in */
  @Nonnull
  public static String soyServerKey(SoyValue key) {
    if (key instanceof NumberData) {
      return serialize(key.coerceToString(), "#");
    }
    if (key == null) {
      return serialize("null", "_");
    }
    return serialize(key.coerceToString(), ":");
  }

  @Nonnull
  public static SoyMap constructMapFromIterator(Iterator<? extends SoyValueProvider> iterator) {
    ImmutableMap.Builder<SoyValue, SoyValueProvider> map = ImmutableMap.builder();
    int i = 0;
    while (iterator.hasNext()) {
      SoyValueProvider item = iterator.next();
      SoyValue recordEntry = item.resolve();
      checkMapFromListConstructorCondition(recordEntry instanceof SoyRecord, recordEntry, i);
      SoyRecord record = (SoyRecord) recordEntry;
      SoyValue key = record.getField(RecordProperty.KEY);
      SoyValueProvider valueProvider = record.getFieldProvider(RecordProperty.VALUE);
      checkMapFromListConstructorCondition(
          SoyMap.isAllowedKeyType(key) && valueProvider != null, recordEntry, i);
      map.put(key, valueProvider);
      i++;
    }

    return SoyMapImpl.forProviderMap(map.buildKeepingLast());
  }

  public static void checkMapFromListConstructorCondition(
      boolean condition, SoyValue value, int index) {
    if (!condition) {
      String exceptionString =
          String.format(
              "Error constructing map. Expected a list where each item is a record of 'key',"
                  + " 'value' pairs, with the 'key' fields holding primitive values. Found: %s at"
                  + " index %d",
              value, index);

      // TODO: throw a RenderException here
      throw new IllegalArgumentException(exceptionString);
    }
  }

  private static String serialize(String key, String delimiter) {
    return key.length() + delimiter + key;
  }

  private SharedRuntime() {}
}
