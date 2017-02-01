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

import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.StringData;
import java.util.Objects;

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
    return Objects.equals(operand0, operand1);
  }

  /** Performs the {@code +} operator on the two values. */
  public static SoyValue plus(SoyValue operand0, SoyValue operand1) {
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return IntegerData.forValue(operand0.longValue() + operand1.longValue());
    } else if (operand0 instanceof NumberData && operand1 instanceof NumberData) {
      return FloatData.forValue(operand0.numberValue() + operand1.numberValue());
    } else {
      // String concatenation is the fallback for other types (like in JS). Use the implemented
      // coerceToString() for the type.
      return StringData.forValue(operand0.coerceToString() + operand1.coerceToString());
    }
  }

  /** Performs the {@code -} operator on the two values. */
  public static SoyValue minus(SoyValue operand0, SoyValue operand1) {
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return IntegerData.forValue(operand0.longValue() - operand1.longValue());
    } else {
      return FloatData.forValue(operand0.numberValue() - operand1.numberValue());
    }
  }

  /** Performs the {@code *} operator on the two values. */
  public static NumberData times(SoyValue operand0, SoyValue operand1) {
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return IntegerData.forValue(operand0.longValue() * operand1.longValue());
    } else {
      return FloatData.forValue(operand0.numberValue() * operand1.numberValue());
    }
  }

  /** Performs the {@code /} operator on the two values. */
  public static double dividedBy(SoyValue operand0, SoyValue operand1) {
    // Note: Soy always performs floating-point division, even on two integers (like JavaScript).
    // Note that this *will* lose precision for longs.
    return operand0.numberValue() / operand1.numberValue();
  }

  /** Performs the {@code <} operator on the two values. */
  public static boolean lessThan(SoyValue operand0, SoyValue operand1) {
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return operand0.longValue() < operand1.longValue();
    } else {
      return operand0.numberValue() < operand1.numberValue();
    }
  }

  /** Performs the {@code <=} operator on the two values. */
  public static boolean lessThanOrEqual(SoyValue operand0, SoyValue operand1) {
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return operand0.longValue() <= operand1.longValue();
    } else {
      return operand0.numberValue() <= operand1.numberValue();
    }
  }

  /** Performs the unary negation {@code -} operator on the value. */
  public static NumberData negative(SoyValue node) {
    if (node instanceof IntegerData) {
      return IntegerData.forValue(-node.longValue());
    } else {
      return FloatData.forValue(-node.floatValue());
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

  private SharedRuntime() {}
}
