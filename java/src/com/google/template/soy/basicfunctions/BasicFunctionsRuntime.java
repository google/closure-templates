/*
 * Copyright 2017 Google Inc.
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

package com.google.template.soy.basicfunctions;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Longs;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyMaps;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.internal.DictImpl;
import com.google.template.soy.data.internal.RuntimeMapTypeTracker;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NumberData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** static functions for implementing the basic functions for java. */
public final class BasicFunctionsRuntime {
  /**
   * Combine the two maps -- for the JavaSource variant while the function signature is still ?
   * instead of map.
   */
  public static SoyDict augmentMap(SoyValue sv1, SoyValue sv2) {
    SoyDict first = (SoyDict) sv1;
    SoyDict second = (SoyDict) sv2;
    Map<String, SoyValueProvider> map =
        Maps.newHashMapWithExpectedSize(first.getItemCnt() + second.getItemCnt());
    map.putAll(first.asJavaStringMap());
    map.putAll(second.asJavaStringMap());
    return DictImpl.forProviderMap(map, RuntimeMapTypeTracker.Type.LEGACY_OBJECT_MAP_OR_RECORD);
  }

  /**
   * Returns the smallest (closest to negative infinity) integer value that is greater than or equal
   * to the argument.
   */
  public static long ceil(SoyValue arg) {
    if (arg instanceof IntegerData) {
      return ((IntegerData) arg).longValue();
    } else {
      return (long) Math.ceil(arg.floatValue());
    }
  }

  /** Concatenates its arguments. */
  public static List<SoyValueProvider> concatLists(List<SoyList> args) {
    ImmutableList.Builder<SoyValueProvider> flattened = ImmutableList.builder();
    for (SoyList soyList : args) {
      flattened.addAll(soyList.asJavaList());
    }
    return flattened.build();
  }

  /** Checks if list contains a value. */
  public static boolean listContains(SoyList list, SoyValue value) {
    return list.asJavaList().contains(value);
  }

  /** Joins the list elements by a separator. */
  public static String join(SoyList list, String separator) {
    List<String> stringList = new ArrayList<>();
    for (SoyValue value : list.asResolvedJavaList()) {
      stringList.add(value.stringValue());
    }
    return Joiner.on(separator).join(stringList);
  }

  /**
   * Returns the largest (closest to positive infinity) integer value that is less than or equal to
   * the argument.
   */
  public static long floor(SoyValue arg) {
    if (arg instanceof IntegerData) {
      return ((IntegerData) arg).longValue();
    } else {
      return (long) Math.floor(arg.floatValue());
    }
  }

  /**
   * Returns a list of all the keys in the given map. For the JavaSource variant, while the function
   * signature is ? instead of legacy_object_map.
   */
  public static List<SoyValue> keys(SoyValue sv) {
    SoyLegacyObjectMap map = (SoyLegacyObjectMap) sv;
    List<SoyValue> list = new ArrayList<>(map.getItemCnt());
    Iterables.addAll(list, map.getItemKeys());
    return list;
  }

  /** Returns a list of all the keys in the given map. */
  public static List<SoyValue> mapKeys(SoyMap map) {
    return ImmutableList.copyOf(map.keys());
  }

  public static SoyDict mapToLegacyObjectMap(SoyMap map) {
    Map<String, SoyValueProvider> keysCoercedToStrings = new HashMap<>();
    for (Map.Entry<? extends SoyValue, ? extends SoyValueProvider> entry :
        map.asJavaMap().entrySet()) {
      keysCoercedToStrings.put(entry.getKey().coerceToString(), entry.getValue());
    }
    return DictImpl.forProviderMap(
        keysCoercedToStrings, RuntimeMapTypeTracker.Type.LEGACY_OBJECT_MAP_OR_RECORD);
  }

  /** Returns the numeric maximum of the two arguments. */
  public static NumberData max(SoyValue arg0, SoyValue arg1) {
    if (arg0 instanceof IntegerData && arg1 instanceof IntegerData) {
      return IntegerData.forValue(Math.max(arg0.longValue(), arg1.longValue()));
    } else {
      return FloatData.forValue(Math.max(arg0.numberValue(), arg1.numberValue()));
    }
  }
  /** Returns the numeric minimum of the two arguments. */
  public static NumberData min(SoyValue arg0, SoyValue arg1) {
    if (arg0 instanceof IntegerData && arg1 instanceof IntegerData) {
      return IntegerData.forValue(Math.min(arg0.longValue(), arg1.longValue()));
    } else {
      return FloatData.forValue(Math.min(arg0.numberValue(), arg1.numberValue()));
    }
  }

  public static FloatData parseFloat(String str) {
    Double d = Doubles.tryParse(str);
    return (d == null || d.isNaN()) ? null : FloatData.forValue(d);
  }

  public static IntegerData parseInt(String str) {
    Long l = Longs.tryParse(str);
    return (l == null) ? null : IntegerData.forValue(l);
  }

  /** Returns a random integer between {@code 0} and the provided argument. */
  public static long randomInt(long longValue) {
    return (long) Math.floor(Math.random() * longValue);
  }

  /**
   * Rounds the given value to the closest decimal point left (negative numbers) or right (positive
   * numbers) of the decimal point
   */
  public static NumberData round(SoyValue value, int numDigitsAfterPoint) {
    // NOTE: for more accurate rounding, this should really be using BigDecimal which can do correct
    // decimal arithmetic.  However, for compatibility with js, that probably isn't an option.
    if (numDigitsAfterPoint == 0) {
      return IntegerData.forValue(round(value));
    } else if (numDigitsAfterPoint > 0) {
      double valueDouble = value.numberValue();
      double shift = Math.pow(10, numDigitsAfterPoint);
      return FloatData.forValue(Math.round(valueDouble * shift) / shift);
    } else {
      double valueDouble = value.numberValue();
      double shift = Math.pow(10, -numDigitsAfterPoint);
      return IntegerData.forValue((int) (Math.round(valueDouble / shift) * shift));
    }
  }

  /** Rounds the given value to the closest integer. */
  public static long round(SoyValue value) {
    if (value instanceof IntegerData) {
      return value.longValue();
    } else {
      return Math.round(value.numberValue());
    }
  }

  public static List<IntegerData> range(int start, int end, int step) {
    if (step == 0) {
      throw new IllegalArgumentException(String.format("step must be non-zero: %d", step));
    }
    int length = end - start;
    if ((length ^ step) < 0) {
      // sign mismatch, step will never cause start to reach end
      return ImmutableList.of();
    }
    // if step does not evenly divide length add +1 to account for the fact that we always add start
    int size = length / step + (length % step == 0 ? 0 : 1);
    List<IntegerData> list = new ArrayList<>(size);
    if (step > 0) {
      for (int i = start; i < end; i += step) {
        list.add(IntegerData.forValue(i));
      }
    } else {
      for (int i = start; i > end; i += step) {
        list.add(IntegerData.forValue(i));
      }
    }
    return list;
  }

  public static boolean strContains(SoyValue left, String right) {
    // TODO(b/74259210) -- Change the first param to String & avoid using stringValue().
    return left.stringValue().contains(right);
  }

  public static int strIndexOf(SoyValue left, SoyValue right) {
    // TODO(b/74259210) -- Change the params to String & avoid using stringValue().
    return left.stringValue().indexOf(right.stringValue());
  }

  public static int strLen(SoyValue str) {
    // TODO(b/74259210) -- Change the param to String & avoid using stringValue().
    return str.stringValue().length();
  }

  public static String strSub(SoyValue str, int start) {
    // TODO(b/74259210) -- Change the first param to String & avoid using stringValue().
    return str.stringValue().substring(start);
  }

  public static String strSub(SoyValue str, int start, int end) {
    // TODO(b/74259210) -- Change the first param to String & avoid using stringValue().
    return str.stringValue().substring(start, end);
  }

  public static int length(List<?> list) {
    return list.size();
  }

  @SuppressWarnings("deprecation")
  public static SoyMap legacyObjectMapToMap(SoyValue value) {
    return SoyMaps.legacyObjectMapToMap((SoyLegacyObjectMap) value);
  }
}
