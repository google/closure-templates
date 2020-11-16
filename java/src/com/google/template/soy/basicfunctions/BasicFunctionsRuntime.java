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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
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
import com.google.template.soy.data.internal.ListImpl;
import com.google.template.soy.data.internal.RuntimeMapTypeTracker;
import com.google.template.soy.data.internal.SoyMapImpl;
import com.google.template.soy.data.internal.SoyRecordImpl;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.StringData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** static functions for implementing the basic functions for java. */
public final class BasicFunctionsRuntime {
  private BasicFunctionsRuntime() {}

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

  @SuppressWarnings("unchecked")
  public static SoyMap concatMaps(SoyMap map, SoyMap mapTwo) {
    LinkedHashMap<SoyValue, SoyValueProvider> mapBuilder = new LinkedHashMap<>();
    mapBuilder.putAll((Map<SoyValue, SoyValueProvider>) map.asJavaMap());
    mapBuilder.putAll((Map<SoyValue, SoyValueProvider>) mapTwo.asJavaMap());
    return SoyMapImpl.forProviderMap(mapBuilder);
  }

  /** Checks if list contains a value. */
  public static boolean listContains(SoyList list, SoyValue value) {
    return list.asJavaList().contains(value);
  }

  /** Checks if list contains a value. */
  public static int listIndexOf(SoyList list, SoyValue value) {
    return list.asJavaList().indexOf(value);
  }

  /** Joins the list elements by a separator. */
  public static String join(SoyList list, String separator) {
    List<String> stringList = new ArrayList<>();
    for (SoyValue value : list.asResolvedJavaList()) {
      stringList.add(value.coerceToString());
    }
    return Joiner.on(separator).join(stringList);
  }

  public static String concatAttributeValues(SoyValue l, SoyValue r, String delimiter) {
    if (l == null && r == null) {
      return "";
    }
    if (l == null) {
      return r.coerceToString();
    }
    if (r == null) {
      return l.coerceToString();
    }
    String lValue = l.stringValue();
    String rValue = r.stringValue();
    if (lValue.isEmpty()) {
      return rValue;
    }
    if (rValue.isEmpty()) {
      return lValue;
    }
    if (lValue.isEmpty() && rValue.isEmpty()) {
      return "";
    }
    return lValue + delimiter + rValue;
  }

  /**
   * Implements JavaScript-like Array slice. Negative and out-of-bounds indexes emulate the JS
   * behavior.
   */
  public static SoyList listSlice(SoyList list, int from, IntegerData optionalTo) {
    int length = list.length();
    if (from < 0) {
      from = length + from;
    }
    int to = length;
    if (optionalTo != null) {
      to = optionalTo.integerValue();
      if (to < 0) {
        to = length + to;
      }
    }
    from = Math.max(0, Math.min(from, length));
    to = Math.max(0, Math.min(to, length));
    if (from >= to) {
      return ListImpl.forProviderList(ImmutableList.of());
    }
    return ListImpl.forProviderList(list.asJavaList().subList(from, to));
  }

  /**
   * Sorts a list in numerical order.
   *
   * <p>This should only be called for a list of numbers.
   */
  public static SoyList numberListSort(SoyList list) {
    return ListImpl.forProviderList(
        ImmutableList.sortedCopyOf(
            (a, b) -> Double.compare(a.resolve().numberValue(), b.resolve().numberValue()),
            list.asJavaList()));
  }

  /**
   * Sorts a list in lexicographic order.
   *
   * <p>This should only be called for a list of strings.
   */
  public static SoyList stringListSort(SoyList list) {
    return ListImpl.forProviderList(
        ImmutableList.sortedCopyOf(
            (a, b) -> a.resolve().stringValue().compareTo(b.resolve().stringValue()),
            list.asJavaList()));
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

  public static ImmutableList<SoyValueProvider> mapValues(SoyMap map) {
    return ImmutableList.copyOf(map.values());
  }

  public static ImmutableList<SoyValueProvider> mapEntries(SoyMap map) {
    return map.entrySet().stream()
        .map(e -> new SoyRecordImpl(ImmutableMap.of("key", e.getKey(), "value", e.getValue())))
        .collect(toImmutableList());
  }

  public static int mapSize(SoyMap map) {
    return map.size();
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
  public static long randomInt(double number) {
    return (long) Math.floor(Math.random() * number);
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

  public static ImmutableList<IntegerData> range(int start, int end, int step) {
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
    return ImmutableList.copyOf(list);
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
    String string = str.stringValue();
    int length = string.length();
    start = Math.max(0, Math.min(length, start));
    return string.substring(start);
  }

  public static String strSub(SoyValue str, int start, int end) {
    // TODO(b/74259210) -- Change the first param to String & avoid using stringValue().
    if (start > end) {
      return strSub(str, end, start);
    }
    String string = str.stringValue();
    int length = string.length();
    start = Math.max(0, Math.min(length, start));
    end = Math.max(0, Math.min(length, end));
    return string.substring(start, end);
  }

  public static boolean strStartsWith(String str, String arg) {
    return str.startsWith(arg);
  }

  public static boolean strEndsWith(String str, String arg) {
    return str.endsWith(arg);
  }

  public static SoyList strSplit(String str, String sep) {
    return ListImpl.forProviderList(
        (sep.isEmpty() ? Splitter.fixedLength(1) : Splitter.on(sep))
            .splitToList(str).stream().map(StringData::forValue).collect(toImmutableList()));
  }

  public static String strReplaceAll(String str, String match, String token) {
    return str.replace(match, token);
  }

  public static String strTrim(String str) {
    return str.trim();
  }

  public static int length(List<?> list) {
    return list.size();
  }

  @SuppressWarnings("deprecation")
  public static SoyMap legacyObjectMapToMap(SoyValue value) {
    return SoyMaps.legacyObjectMapToMap((SoyLegacyObjectMap) value);
  }
}
