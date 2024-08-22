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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.template.soy.data.ProtoFieldInterpreter;
import com.google.template.soy.data.RecordProperty;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A SoyMap interface to a native Java Map (from a proto) that lazily converts keys and values to
 * {@link SoyValue}s as they're accessed.
 */
public final class LazyProtoToSoyValueMap<K, V> extends SoyMap {

  private final Map<K, V> rawMap;

  /**
   * A cache of wrapped keys to wrapped values. If a value is missing from this map it means it
   * hasn't been accessed yet.
   */
  private final Map<SoyValue, SoyValue> wrappedValues;

  private final ProtoFieldInterpreter keyFieldInterpreter;
  private final ProtoFieldInterpreter valueFieldInterpreter;
  private boolean fullyResolved;

  @Nonnull
  public static <K, V> LazyProtoToSoyValueMap<K, V> forMap(
      Map<K, V> map,
      ProtoFieldInterpreter keyFieldInterpreter,
      ProtoFieldInterpreter valueFieldInterpreter) {
    return new LazyProtoToSoyValueMap<>(map, keyFieldInterpreter, valueFieldInterpreter);
  }

  private LazyProtoToSoyValueMap(
      Map<K, V> map,
      ProtoFieldInterpreter keyFieldInterpreter,
      ProtoFieldInterpreter valueFieldInterpreter) {
    rawMap = checkNotNull(map);
    wrappedValues = Maps.newLinkedHashMapWithExpectedSize(rawMap.size());
    this.keyFieldInterpreter = keyFieldInterpreter;
    this.valueFieldInterpreter = valueFieldInterpreter;
  }

  @Override
  public int size() {
    return rawMap.size();
  }

  @Nonnull
  @Override
  public Set<SoyValue> keys() {
    return asJavaMap().keySet();
  }

  @Override
  public boolean containsKey(SoyValue key) {
    if (wrappedValues.containsKey(key)) {
      return true;
    }
    Object value = rawMap.get(soyValueToKey(key));
    if (value == null) {
      return false;
    }
    // The most likely case for a containsKey test is just to follow it up with a get, so pre-cache
    // now.
    wrappedValues.put(key, valueFieldInterpreter.soyFromProto(value));
    return true;
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
  public Map<SoyValue, SoyValue> asJavaMap() {
    if (!fullyResolved) {
      for (K key : rawMap.keySet()) {
        var unused = get(keyFieldInterpreter.soyFromProto(key));
      }
      fullyResolved = true;
    }
    return wrappedValues;
  }

  private Object soyValueToKey(SoyValue soyValue) {
    if (soyValue.isNullish()) {
      return null;
    }
    return keyFieldInterpreter.protoFromSoy(soyValue);
  }

  // SoyRecord methods
  @Override
  public int recordSize() {
    return 0;
  }

  @Override
  public ImmutableMap<String, SoyValueProvider> recordAsMap() {
    return ImmutableMap.of();
  }

  @Override
  public boolean hasField(RecordProperty name) {
    return false;
  }

  @Nullable
  @Override
  public SoyValueProvider getFieldProvider(RecordProperty name) {
    return null;
  }

  @Nullable
  @Override
  public SoyValue getField(RecordProperty name) {
    return null;
  }

  @Override
  public void forEach(BiConsumer<RecordProperty, ? super SoyValueProvider> action) {}

  @Override
  public int hashCode() {
    return System.identityHashCode(rawMap);
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof LazyProtoToSoyValueMap) {
      return rawMap == ((LazyProtoToSoyValueMap) other).rawMap;
    }
    return false;
  }
}
