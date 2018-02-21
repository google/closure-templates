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

package com.google.template.soy.data;

import static com.google.template.soy.data.SoyValueConverter.INSTANCE;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.internal.ListImpl;
import java.util.HashMap;
import java.util.Map;

/** Shared utilities for converting Java objects to SoyValues in a convenient way. */
public final class SoyValueConverterUtility {

  /**
   * Creates a new SoyDict initialized from the given keys and values. Values are converted eagerly.
   * Recognizes dotted-name syntax: adding {@code ("foo.goo", value)} will automatically create
   * {@code ['foo': ['goo': value]]}.
   *
   * @param alternatingKeysAndValues An alternating list of keys and values.
   * @return A new SoyDict initialized from the given keys and values.
   */
  public static SoyDict newDict(Object... alternatingKeysAndValues) {
    Preconditions.checkArgument(alternatingKeysAndValues.length % 2 == 0);

    Map<String, Object> map = new HashMap<>();
    for (int i = 0, n = alternatingKeysAndValues.length / 2; i < n; i++) {
      String key = (String) alternatingKeysAndValues[2 * i];
      SoyValueProvider value =
          INSTANCE.convert(alternatingKeysAndValues[2 * i + 1]); // convert eagerly
      insertIntoNestedMap(map, key, value);
    }
    return INSTANCE.newDictFromMap(map);
  }

  /**
   * Creates a new SoyList initialized from the given values. Values are converted eagerly.
   *
   * @param items A list of values.
   * @return A new SoyEasyList initialized from the given values.
   */
  public static SoyList newList(Object... items) {
    ImmutableList.Builder<SoyValueProvider> builder = ImmutableList.builder();
    for (Object o : items) {
      builder.add(INSTANCE.convert(o));
    }
    return ListImpl.forProviderList(builder.build());
  }

  /**
   * Private helper to create nested maps based off of a given string of dot-separated field names,
   * then insert the value into the innermost map.
   *
   * <p>For example, {@code insertIntoNestedMap(new HashMap<>(), "foo.bar.baz", val)} will return a
   * map of {@code {"foo": {"bar": {"baz": val}}}}.
   *
   * @param map Top-level map to insert into
   * @param dottedName One or more field names, dot-separated.
   * @param value Value to insert
   */
  private static void insertIntoNestedMap(
      Map<String, Object> map, String dottedName, SoyValueProvider value) {

    String[] names = dottedName.split("[.]");
    int n = names.length;

    String lastName = names[n - 1];

    Map<String, Object> lastMap;
    if (n == 1) {
      lastMap = map;
    } else {
      lastMap = map;
      for (int i = 0; i <= n - 2; i++) {
        Object o = lastMap.get(names[i]);
        if (o instanceof Map) {
          @SuppressWarnings("unchecked")
          Map<String, Object> m = (Map<String, Object>) o;
          lastMap = m;
        } else if (o == null) {
          Map<String, Object> newMap = new HashMap<>();
          lastMap.put(names[i], newMap);
          lastMap = newMap;
        } else {
          throw new AssertionError("should not happen");
        }
      }
    }

    lastMap.put(lastName, value);
  }
}
