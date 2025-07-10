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
import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.math.DoubleMath;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Longs;
import com.google.protobuf.Message;
import com.google.template.soy.data.RecordProperty;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyMaps;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.SoyVisualElement;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.internal.DictImpl;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.data.internal.RuntimeMapTypeTracker;
import com.google.template.soy.data.internal.SoyMapImpl;
import com.google.template.soy.data.internal.SoyRecordImpl;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.GbigintData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.shared.internal.Sanitizers;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** static functions for implementing the basic functions for java. */
public final class BasicFunctionsRuntime {
  private BasicFunctionsRuntime() {}

  /** Checks if two Visual Elements ids are equivalent */
  public static boolean veHasSameId(SoyVisualElement ve1, SoyVisualElement ve2) {
    return ve1.hasSameId(ve2);
  }

  /**
   * Returns the smallest (closest to negative infinity) integer value that is greater than or equal
   * to the argument.
   */
  public static long ceil(SoyValue arg) {
    if (arg instanceof IntegerData) {
      return arg.longValue();
    } else {
      return (long) Math.ceil(arg.floatValue());
    }
  }

  /** Returns the magnitude of the number value that is provided. */
  public static NumberData abs(SoyValue arg) {
    if (arg instanceof IntegerData) {
      return IntegerData.forValue(Math.abs(arg.longValue()));
    } else {
      return FloatData.forValue(Math.abs(arg.floatValue()));
    }
  }

  /** Returns the sign of the number value that is provided. */
  public static NumberData sign(SoyValue arg) {
    return FloatData.forValue(Math.signum(arg.numberValue()));
  }

  /** Concatenates its arguments. */
  @Nonnull
  public static ImmutableList<SoyValueProvider> concatLists(SoyList list1, SoyList list2) {
    ImmutableList.Builder<SoyValueProvider> flattened = ImmutableList.builder();
    return flattened.addAll(list1.asJavaList()).addAll(list2.asJavaList()).build();
  }

  /** Concatenates its arguments. */
  @Nonnull
  public static ImmutableList<SoyValueProvider> concatLists(
      SoyList list1, SoyList list2, SoyList list3) {
    ImmutableList.Builder<SoyValueProvider> flattened = ImmutableList.builder();
    return flattened
        .addAll(list1.asJavaList())
        .addAll(list2.asJavaList())
        .addAll(list3.asJavaList())
        .build();
  }

  /** Concatenates its arguments. */
  @Nonnull
  public static ImmutableList<SoyValueProvider> concatLists(SoyList list1, List<SoyList> lists) {
    ImmutableList.Builder<SoyValueProvider> flattened = ImmutableList.builder();
    flattened.addAll(list1.asJavaList());
    for (var list : lists) {
      flattened.addAll(list.asJavaList());
    }
    return flattened.build();
  }

  @Nonnull
  public static SoyMap concatMaps(SoyMap map, SoyMap mapTwo) {
    LinkedHashMap<SoyValue, SoyValueProvider> mapBuilder = new LinkedHashMap<>();
    mapBuilder.putAll(map.asJavaMap());
    mapBuilder.putAll(mapTwo.asJavaMap());
    return SoyMapImpl.forProviderMap(mapBuilder);
  }

  /** Checks if list contains a value. */
  public static boolean listContains(List<? extends SoyValueProvider> list, SoyValue value) {
    for (SoyValueProvider valueProvider : list) {
      if (valueProvider.resolve().equals(value)) {
        return true;
      }
    }
    return false;
  }

  /** Checks if list contains a value. */
  public static int listIndexOf(
      List<? extends SoyValueProvider> list, SoyValue value, NumberData startIndex) {
    int clampedStartIndex = clampListIndex(list, startIndex);
    if (clampedStartIndex >= list.size()) {
      return -1;
    }
    int indexInSubList = list.subList(clampedStartIndex, list.size()).indexOf(value);
    return indexInSubList == -1 ? -1 : indexInSubList + clampedStartIndex;
  }

  /** Joins the list elements by a separator. */
  @Nonnull
  public static String join(List<? extends SoyValueProvider> list, String separator) {
    return list.stream().map(v -> v.resolve().coerceToString()).collect(joining(separator));
  }

  @Nonnull
  public static String concatAttributeValues(SoyValue l, SoyValue r, String delimiter) {
    boolean lnull = l == null || l.isNullish();
    boolean rnull = r == null || r.isNullish();

    if (lnull && rnull) {
      return "";
    }
    if (lnull) {
      return r.coerceToString();
    }
    if (rnull) {
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
    return lValue + delimiter + rValue;
  }

  @Nonnull
  public static SanitizedContent concatCssValues(SoyValue l, SoyValue r) {
    return UnsafeSanitizedContentOrdainer.ordainAsSafe(
        concatAttributeValues(l, r, ";"), ContentKind.CSS);
  }

  /**
   * Implements JavaScript-like Array slice. Negative and out-of-bounds indexes emulate the JS
   * behavior.
   */
  @Nonnull
  public static ImmutableList<? extends SoyValueProvider> listSlice(
      List<? extends SoyValueProvider> list, NumberData from, NumberData optionalTo) {
    int length = list.size();
    int intFrom = clampListIndex(list, from);
    if (optionalTo == null) {
      return ImmutableList.copyOf(list.subList(intFrom, length));
    }
    int to = clampListIndex(list, optionalTo);
    if (to < intFrom) {
      return ImmutableList.of();
    }
    return ImmutableList.copyOf(list.subList(intFrom, to));
  }

  /** Reverses an array. The original list passed is not modified. */
  @Nonnull
  public static ImmutableList<? extends SoyValueProvider> listReverse(
      List<? extends SoyValueProvider> list) {
    return ImmutableList.copyOf(list).reverse();
  }

  /** Removes all duplicates from a list. The original list passed is not modified. */
  @Nonnull
  public static ImmutableList<? extends SoyValueProvider> listUniq(
      List<? extends SoyValueProvider> list) {
    return list.stream().distinct().collect(toImmutableList());
  }

  @Nonnull
  public static ImmutableList<? extends SoyValueProvider> listFlat(
      List<? extends SoyValueProvider> list) {
    return listFlatImpl(list, 1);
  }

  @Nonnull
  public static ImmutableList<? extends SoyValueProvider> listFlat(
      List<? extends SoyValueProvider> list, IntegerData data) {
    return listFlatImpl(list, (int) data.getValue());
  }

  @Nonnull
  private static ImmutableList<? extends SoyValueProvider> listFlatImpl(
      List<? extends SoyValueProvider> list, int maxDepth) {
    ImmutableList.Builder<SoyValueProvider> builder = ImmutableList.builder();
    listFlatImpl(list, builder, maxDepth);
    return builder.build();
  }

  private static void listFlatImpl(
      List<? extends SoyValueProvider> list,
      ImmutableList.Builder<SoyValueProvider> builder,
      int maxDepth) {
    for (SoyValueProvider value : list) {
      if (maxDepth > 0 && value.resolve() instanceof SoyList) {
        listFlatImpl(((SoyList) value.resolve()).asResolvedJavaList(), builder, maxDepth - 1);
      } else {
        builder.add(value);
      }
    }
  }

  /**
   * Sorts a list in numerical order.
   *
   * <p>This should only be called for a list of numbers.
   */
  @Nonnull
  public static ImmutableList<SoyValueProvider> numberListSort(
      List<? extends SoyValueProvider> list) {
    return ImmutableList.sortedCopyOf(
        comparingDouble((SoyValueProvider arg) -> arg.resolve().floatValue()), list);
  }

  @Nonnull
  public static <T extends SoyValueProvider> ImmutableList<T> comparatorListSort(
      List<T> list, Comparator<? super T> comparator) {
    return ImmutableList.sortedCopyOf(comparator, list);
  }

  /** Sorts a list in numerical order. */
  @Nonnull
  public static ImmutableList<GbigintData> gbigintListSort(List<GbigintData> list) {
    return ImmutableList.sortedCopyOf(comparing(GbigintData::getValue), list);
  }

  /**
   * Sorts a list in lexicographic order.
   *
   * <p>This should only be called for a list of strings.
   */
  @Nonnull
  public static ImmutableList<SoyValueProvider> stringListSort(
      List<? extends SoyValueProvider> list) {
    return ImmutableList.sortedCopyOf(
        comparing((SoyValueProvider arg) -> arg.resolve().stringValue()), list);
  }

  /**
   * Returns the largest (closest to positive infinity) integer value that is less than or equal to
   * the argument.
   */
  public static long floor(SoyValue arg) {
    if (arg instanceof IntegerData) {
      return arg.longValue();
    } else {
      return (long) Math.floor(arg.floatValue());
    }
  }

  /**
   * Returns a list of all the keys in the given map. For the JavaSource variant, while the function
   * signature is ? instead of legacy_object_map.
   */
  @Nonnull
  public static List<SoyValue> keys(SoyValue sv) {
    SoyLegacyObjectMap map = (SoyLegacyObjectMap) sv;
    List<SoyValue> list = new ArrayList<>(map.getItemCnt());
    Iterables.addAll(list, map.getItemKeys());
    return list;
  }

  /** Returns a list of all the keys in the given map. */
  @Nonnull
  public static ImmutableList<SoyValue> mapKeys(SoyMap map) {
    return ImmutableList.copyOf(map.keys());
  }

  @Nonnull
  public static ImmutableList<SoyValueProvider> mapValues(SoyMap map) {
    return ImmutableList.copyOf(map.values());
  }

  @Nonnull
  public static ImmutableList<SoyValueProvider> mapEntries(SoyMap map) {
    return map.entrySet().stream()
        .map(
            e ->
                new SoyRecordImpl(
                    new ParamStore(2)
                        .setField(RecordProperty.KEY, e.getKey())
                        .setField(RecordProperty.VALUE, e.getValue())))
        .collect(toImmutableList());
  }

  @Nonnull
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
  public static NumberData max(List<? extends SoyValue> args) {
    if (args.isEmpty()) {
      return FloatData.forValue(Double.NEGATIVE_INFINITY);
    }

    Optional<Double> maxVal = args.stream().map(SoyValue::numberValue).max(Double::compare);
    // Return IntegerData if all arguments are IntegerData.
    if (args.stream().filter(v -> v instanceof IntegerData).count() == args.size()) {
      return IntegerData.forValue(maxVal.get().longValue());
    }
    return maxVal
        .map(FloatData::forValue)
        .orElseThrow(() -> new IllegalArgumentException("Invalid arguments for max function."));
  }

  /** Returns the numeric minimum of the two arguments. */
  public static NumberData min(SoyValue arg0, SoyValue arg1) {
    if (arg0 instanceof IntegerData && arg1 instanceof IntegerData) {
      return IntegerData.forValue(Math.min(arg0.longValue(), arg1.longValue()));
    } else {
      return FloatData.forValue(Math.min(arg0.floatValue(), arg1.floatValue()));
    }
  }

  @Nullable
  public static FloatData parseFloat(String str) {
    Double d = Doubles.tryParse(str);
    return (d == null || d.isNaN()) ? null : FloatData.forValue(d);
  }

  @Nullable
  public static IntegerData parseInt(String str, SoyValue radixVal) {
    int radix = SoyValue.isNullish(radixVal) ? 10 : (int) radixVal.floatValue();
    if (radix < 2 || radix > 36) {
      return null;
    }
    Long l = Longs.tryParse(str, radix);
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
  @Nonnull
  public static NumberData round(SoyValue value, int numDigitsAfterPoint) {
    // NOTE: for more accurate rounding, this should really be using BigDecimal which can do correct
    // decimal arithmetic.  However, for compatibility with js, that probably isn't an option.
    if (numDigitsAfterPoint == 0) {
      return IntegerData.forValue(round(value));
    } else if (numDigitsAfterPoint > 0) {
      double valueDouble = value.floatValue();
      double shift = Math.pow(10, numDigitsAfterPoint);
      return FloatData.forValue(Math.round(valueDouble * shift) / shift);
    } else {
      double valueDouble = value.floatValue();
      double shift = Math.pow(10, -numDigitsAfterPoint);
      return IntegerData.forValue((int) (Math.round(valueDouble / shift) * shift));
    }
  }

  /** Rounds the given value to the closest integer. */
  public static long round(SoyValue value) {
    if (value instanceof IntegerData) {
      return value.longValue();
    } else {
      return Math.round(value.floatValue());
    }
  }

  @Nonnull
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

    return new AbstractList<>() {
      @Override
      public IntegerData get(int index) {
        return IntegerData.forValue(start + step * index);
      }

      @Override
      public int size() {
        return size;
      }
    };
  }

  public static boolean strContainsFromIndex(String left, String right, NumberData index) {
    int clampedStart = clampStrIndex(left, index);
    return left.substring(clampedStart).contains(right);
  }

  public static boolean strContains(SoyValue left, String right) {
    // TODO(b/74259210) -- Change the first param to String & avoid using stringValue().
    return left.stringValue().contains(right);
  }

  public static int strIndexOf(SoyValue str, SoyValue searchStr, NumberData start) {
    // TODO(b/74259210) -- Change the params to String & avoid using stringValue().
    // Add clamping behavior for start index to match js implementation
    String strValue = str.stringValue();
    int clampedStart = clampStrIndex(strValue, start);
    return strValue.indexOf(searchStr.stringValue(), clampedStart);
  }

  public static int strLen(SoyValue str) {
    // TODO(b/74259210) -- Change the param to String & avoid using stringValue().
    return str.stringValue().length();
  }

  public static String strSub(SoyValue str, NumberData start) {
    // TODO(b/74259210) -- Change the first param to String & avoid using stringValue().
    String string = str.stringValue();
    return string.substring(clampStrIndex(string, start));
  }

  public static String strSub(SoyValue str, NumberData start, NumberData end) {
    // TODO(b/74259210) -- Change the first param to String & avoid using stringValue().
    if (start.floatValue() > end.floatValue()) {
      return strSub(str, end, start);
    }
    String string = str.stringValue();
    return string.substring(clampStrIndex(string, start), clampStrIndex(string, end));
  }

  public static boolean strStartsWith(String str, String arg, NumberData start) {
    int clampedStart = clampStrIndex(str, start);
    if (clampedStart + arg.length() > str.length()) {
      return false;
    }
    return str.substring(clampedStart).startsWith(arg);
  }

  public static boolean strEndsWith(String str, String arg, NumberData length) {
    if (length == null) {
      return str.endsWith(arg);
    }
    int clampedLength = clampStrIndex(str, length);
    if (clampedLength - arg.length() < 0) {
      return false;
    }
    return str.substring(0, clampedLength).endsWith(arg);
  }

  @Nonnull
  public static ImmutableList<StringData> strSplit(String str, String sep, NumberData limit) {
    ImmutableList.Builder<StringData> builder = ImmutableList.builder();
    int truncLimit = -1;
    if (limit != null) {
      truncLimit = (int) limit.floatValue();
    }
    if (truncLimit == 0) {
      return builder.build();
    }
    int count = 0;
    for (String string : (sep.isEmpty() ? Splitter.fixedLength(1) : Splitter.on(sep)).split(str)) {
      if (count == truncLimit) {
        return builder.build();
      }
      builder.add(StringData.forValue(string));
      count++;
    }
    return builder.build();
  }

  @Nonnull
  public static String strReplaceAll(String str, String match, String token) {
    return str.replace(match, token);
  }

  @Nonnull
  public static String strTrim(String str) {
    return str.trim();
  }

  public static int length(List<?> list) {
    return list.size();
  }

  public static int length(SoyValue value) {
    if (value instanceof SoyList) {
      return ((SoyList) value).length();
    }
    return value.asJavaList().size();
  }

  @SuppressWarnings("deprecation") // we cannot do anything so go away
  @Nonnull
  public static SoyMap legacyObjectMapToMap(SoyValue value) {
    return SoyMaps.legacyObjectMapToMap((SoyLegacyObjectMap) value);
  }

  public static boolean isDefault(Message proto) {
    return proto.equals(proto.getDefaultInstanceForType());
  }

  public static boolean protoEquals(Message proto1, Message proto2) {
    return proto1.equals(proto2);
  }

  private static int clampListIndex(List<?> list, NumberData index) {
    int truncIndex = (int) index.floatValue();
    int size = list.size();
    int clampLowerBound = Math.max(0, truncIndex >= 0 ? truncIndex : size + truncIndex);
    // Clamp upper bound
    return Math.min(size, clampLowerBound);
  }

  private static int clampStrIndex(String str, NumberData position) {
    int clampLowerBound = Math.max(0, (int) position.floatValue());
    // Clamp upper bound
    return Math.min(str.length(), clampLowerBound);
  }

  /** Returns whether the argument is a finite value (not NaN or Infinity). */
  public static boolean isFinite(SoyValue arg) {
    return arg instanceof NumberData && Double.isFinite(arg.floatValue());
  }

  private static String joinHelper(List<SoyValue> values, String delimiter) {
    return values.stream()
        .filter(v -> v != null)
        .flatMap(
            v -> v instanceof SoyList ? ((SoyList) v).asResolvedJavaList().stream() : Stream.of(v))
        .filter(SoyValue::coerceToBoolean)
        .map(SoyValue::coerceToString)
        .collect(joining(delimiter));
  }

  /** Joins items with a semicolon, filtering out falsey values. */
  @Nonnull
  public static String buildAttrValue(List<SoyValue> values) {
    return joinHelper(values, ";");
  }

  /** Joins items with a space, filtering out falsey values. */
  @Nonnull
  public static String buildClassValue(List<SoyValue> values) {
    return joinHelper(values, " ");
  }

  private static final Pattern CSS_NAME_REGEX = Pattern.compile("^\\s*[\\w-]+\\s*$");

  /** Joins items with a semicolon, filtering out falsey values. */
  @Nonnull
  public static SanitizedContent buildStyleValue(List<SoyValue> values) {
    return UnsafeSanitizedContentOrdainer.ordainAsSafe(
        values.stream()
            .filter(v -> v != null)
            .filter(SoyValue::coerceToBoolean)
            .map(
                v -> {
                  if (v instanceof StringData) {
                    String str = v.coerceToString();
                    if (str.indexOf(':') != -1) {
                      String name = str.substring(0, str.indexOf(':'));
                      if (CSS_NAME_REGEX.matcher(name).matches()) {
                        String value = str.substring(str.indexOf(':') + 1);
                        return String.format(
                            "%s:%s",
                            name.trim(),
                            Sanitizers.filterCssValue(value.trim().replaceAll(";$", "")));
                      }
                    }
                  }
                  return Sanitizers.filterCssValue(v);
                })
            .collect(joining(";")),
        ContentKind.CSS);
  }

  /**
   * Joins items with the correct delimiter, filtering out falsey values and returns an attribute
   * key/value pair.
   */
  @Nonnull
  public static SanitizedContent buildAttr(String attrName, List<SoyValue> values) {
    String attrValue = attrName.equals("class") ? buildClassValue(values) : buildAttrValue(values);
    return UnsafeSanitizedContentOrdainer.ordainAsSafe(
        attrValue.isEmpty()
            ? ""
            : String.format("%s=\"%s\"", attrName, Sanitizers.escapeHtmlAttribute(attrValue)),
        ContentKind.ATTRIBUTES);
  }

  public static SoyValue throwException(String message) {
    throw new RuntimeException(message);
  }

  public static boolean isNaN(SoyValue value) {
    return value instanceof FloatData && Double.isNaN(value.floatValue());
  }

  public static boolean isInteger(double value) {
    return DoubleMath.isMathematicalInteger(value);
  }

  public static boolean isInteger(SoyValue value) {
    return value instanceof IntegerData
        || (value instanceof FloatData && isInteger(value.floatValue()));
  }
}
