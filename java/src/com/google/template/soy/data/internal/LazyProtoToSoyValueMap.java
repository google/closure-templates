/*
 * Copyright 2020 Google Inc.
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

package com.google.template.soy.data.internal;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.template.soy.data.ProtoFieldInterpreter;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.NullData;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * A SoyMap interface to a native Java Map (from a proto) that lazily converts keys and values to
 * {@link SoyValue}s as they're accessed.
 */
public final class LazyProtoToSoyValueMap<K, V> extends AbstractSoyMap {

  private final ImmutableMap<K, V> rawMap;

  /**
   * A cache of wrapped keys to wrapped values. If a value is missing from this map it means it
   * hasn't been accessed yet.
   */
  private final Map<SoyValue, SoyValue> wrappedValues;
  /**
   * A cached of wrapped keys to their raw key value. If a wrapped key is missing from this map it
   * means it hasn't been accessed yet.
   */
  private final Map<SoyValue, K> rawKeys;
  /**
   * A cached of raw keys to their wrapped key value. If a wrapped key is missing from this map it
   * means it hasn't been accessed yet.
   */
  private final Map<K, SoyValue> wrappedKeys;

  private final ProtoFieldInterpreter keyFieldInterpreter;
  private final ProtoFieldInterpreter valueFieldInterpreter;
  private final Class<K> keyClass;

  public static <K, V> LazyProtoToSoyValueMap<K, V> forMap(
      Map<K, V> map,
      ProtoFieldInterpreter keyFieldInterpreter,
      ProtoFieldInterpreter valueFieldInterpreter,
      Class<K> keyClass) {
    return new LazyProtoToSoyValueMap<>(map, keyFieldInterpreter, valueFieldInterpreter, keyClass);
  }

  private LazyProtoToSoyValueMap(
      Map<K, V> map,
      ProtoFieldInterpreter keyFieldInterpreter,
      ProtoFieldInterpreter valueFieldInterpreter,
      Class<K> keyClass) {
    rawMap = ImmutableMap.copyOf(map);
    wrappedValues = new HashMap<>();
    BiMap<SoyValue, K> keys = Maps.synchronizedBiMap(HashBiMap.create());
    rawKeys = keys;
    wrappedKeys = keys.inverse();
    this.keyFieldInterpreter = keyFieldInterpreter;
    this.valueFieldInterpreter = valueFieldInterpreter;
    this.keyClass = keyClass;
  }

  @Override
  public int size() {
    return rawMap.size();
  }

  @Override
  public ImmutableSet<SoyValue> keys() {
    ImmutableSet.Builder<SoyValue> keys = ImmutableSet.builder();
    for (K key : rawMap.keySet()) {
      SoyValue wrappedKey = wrappedKeys.computeIfAbsent(key, keyFieldInterpreter::soyFromProto);
      keys.add(wrappedKey);
    }
    return keys.build();
  }

  @Override
  public boolean containsKey(SoyValue key) {
    if (rawKeys.containsKey(key)) {
      return true;
    }
    return rawMap.containsKey(soyValueToKey(key));
  }

  @Override
  public SoyValue get(SoyValue key) {
    return wrappedValues.computeIfAbsent(
        key,
        k -> {
          V value = rawMap.get(soyValueToKey(k));
          if (value == null) {
            return null;
          }
          return valueFieldInterpreter.soyFromProto(value);
        });
  }

  @Override
  public SoyValue getProvider(SoyValue key) {
    return get(key);
  }

  @Override
  @Nonnull
  public ImmutableMap<SoyValue, SoyValue> asJavaMap() {
    ImmutableMap.Builder<SoyValue, SoyValue> map = ImmutableMap.builder();
    for (SoyValue key : keys()) {
      map.put(key, get(key));
    }
    return map.build();
  }

  private K soyValueToKey(SoyValue soyValue) {
    if (NullData.INSTANCE.equals(soyValue)) {
      return null;
    }
    return rawKeys.computeIfAbsent(
        soyValue, k -> keyClass.cast(keyFieldInterpreter.protoFromSoy(k)));
  }
}
