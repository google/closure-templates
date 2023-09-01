/*
 * Copyright 2023 Google Inc.
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
package com.google.template.soy.jbcsrc.shared;

import static java.lang.invoke.MethodType.methodType;

import com.google.common.collect.Maps;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;

/**
 * An {@code constantdynamic} bootstrap for hashing constants to switch indexes
 *
 * <p>This generates a (String)->int or a (SoyValue)->int function that maps each string to the
 * index of the string in the set of strings passed to the bootstrap method.
 *
 * <p>The benefit of using invokedynamic is that we can lazily allocate datastructures and store
 * them in the constant pool without needing to allocate fields. Also to the extent we want to
 * leverage more complicated strategies it is easier to implement them here (normal java code) than
 * in the compiler directly.
 */
public final class SwitchFactory {

  private static MethodHandle findLocalStaticOrDie(String name, MethodType type) {
    try {
      return MethodHandles.lookup().findStatic(SwitchFactory.class, name, type);
    } catch (ReflectiveOperationException e) {
      throw new LinkageError(e.getMessage(), e);
    }
  }

  private static final MethodHandle GET_OR_DEFAULT_OBJECT =
      findLocalStaticOrDie("getOrDefault", methodType(int.class, Map.class, Object.class));

  private static final MethodHandle GET_OR_DEFAULT_SOY_VALUE =
      findLocalStaticOrDie("getOrDefault", methodType(int.class, Map.class, SoyValue.class));

  public static CallSite bootstrapSwitch(
      MethodHandles.Lookup lookup, String name, MethodType type, Object... switchCases) {
    if (switchCases.length == 0) {
      throw new AssertionError();
    }
    // For now we just use an Map<String,Integer> to look up case numbers, this is probably
    // close to optimal but comes with slightly higher memory cost (boxing + entry objects).
    // There are a few alternatives we could consider
    // 1. Parallel String[] and int[] arrays that we could binary search on.
    //    This would be close to ideal from a memory perspective at the cost of O(logn) lookup time
    //    In fact, if we sorted the cases at compile time we could drop the int[]
    // 2. Storing the hash codes in an int[] in sorted order with parallel String[] and int[] arrays
    //    Then we could binary search on the hash code which should be very fast but we would need
    //    to deal with hash collisions.
    //    If we sorted the strings by hashcode at compile time we could skip the final int[] array.
    // 3. Use an ImmutableSortedMap<String,Integer>, this would be similar to 1 but with more boxing
    //    but less custom code.
    // 4. Use a simple String[] and a linear probe to find the index of the key
    //    the least possible memory and for small enough keysets is the same as 1
    //
    // Deciding amongst the above would be tricky and likely depend on the cardinality of the
    // switchCases, so for now we stick with the simplest option.
    Map<Object, Integer> map = Maps.newHashMapWithExpectedSize(switchCases.length);
    for (int i = 0; i < switchCases.length; i++) {
      Object caseValue = switchCases[i];
      // Turn ints and doubles into their SoyValue types since those have equals/hashCode methods
      // that make them mutually comparable. i.o.w.
      // FloatData.valueOf(2).equals(IntegerData.valueOf(2))
      if (caseValue instanceof Integer || caseValue instanceof Long) {
        caseValue = IntegerData.forValue(((Number) caseValue).longValue());
      } else if (caseValue instanceof Double) {
        caseValue = FloatData.forValue(((Double) caseValue).doubleValue());
      } else if (caseValue instanceof Boolean) {
        caseValue = BooleanData.forValue(((Boolean) caseValue).booleanValue());
      } else if (caseValue instanceof String) {
        // do nothing
      } else if (caseValue == NullData.INSTANCE || caseValue == UndefinedData.INSTANCE) {
        // do nothing
      } else {
        throw new IllegalArgumentException(
            String.format("Unknown case type: %s", caseValue.getClass()));
      }
      map.putIfAbsent(caseValue, i);
    }
    Class<?> caseType = type.parameterType(0);
    MethodHandle getOrDefaultFn =
        caseType == SoyValue.class ? GET_OR_DEFAULT_SOY_VALUE : GET_OR_DEFAULT_OBJECT;

    return new ConstantCallSite(MethodHandles.insertArguments(getOrDefaultFn, 0, map));
  }

  // Keys are strings or SoyValue objects (or null)
  public static int getOrDefault(Map<?, Integer> map, Object key) {
    Integer v = map.get(key);
    return v == null ? -1 : v.intValue();
  }

  // functions when we have a mix of key types
  public static int getOrDefault(Map<Object, Integer> map, SoyValue key) {
    if (key instanceof StringData || key instanceof SanitizedContent) {
      return getOrDefault(map, key.stringValue());
    }
    // use the soyvalue directly as a key
    return getOrDefault(map, (Object) key);
  }

  private SwitchFactory() {}
}
