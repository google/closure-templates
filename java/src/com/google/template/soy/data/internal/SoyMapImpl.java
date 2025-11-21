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

package com.google.template.soy.data.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.RecordProperty;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A map containing key-to-value mappings referred to as items. Each key is a SoyValue (must be
 * already resolved) and each value is a SoyValue (can be unresolved).
 *
 * <p>This is a new implementation (compared to {@link DictImpl}) that was designed for supporting
 * proto map. There are two different Soy map types, one uses ES6 map in JS, and another uses plain
 * object.
 */
@ParametersAreNonnullByDefault
public final class SoyMapImpl extends SoyMap {
  public static final SoyMapImpl EMPTY = new SoyMapImpl(ImmutableMap.of());

  /** Creates a SoyDict implementation for a particular underlying provider map. */
  @Nonnull
  public static SoyMapImpl forProviderMap(Map<SoyValue, ? extends SoyValueProvider> providerMap) {
    if (!(providerMap instanceof ImmutableMap)) {
      // Since mutable methods exist, we need to guard the underlying data structure to make sure
      // that mutable methods don't get past the type system.
      providerMap = ImmutableMap.copyOf(providerMap);
    }

    // We allow callers to pass a Map<SoyValue, SoyValue> or Map<SoyValue, SoyValueProvider>, but
    // the underlying type allows either value type to be added to the map.
    // In practice, only mutable maps can add values, and those are guaranteed to only add SoyValue
    // (the narrower type), which is always safe; you'll never have a caller passing a map with a
    // narrow type and finding a value with a wider type.
    @SuppressWarnings("unchecked")
    var map = (Map<SoyValue, SoyValueProvider>) providerMap;
    return new SoyMapImpl(map);
  }

  @Nonnull
  public static SoyMapImpl forMutableProviderMap(
      Map<SoyValue, ? extends SoyValueProvider> providerMap) {
    if (providerMap instanceof ImmutableMap) {
      throw new IllegalArgumentException("Immutable map passed to mutable method");
    }
    // See above for rationale.
    @SuppressWarnings("unchecked")
    var map = (Map<SoyValue, SoyValueProvider>) providerMap;
    return new SoyMapImpl(map);
  }

  @Nonnull
  public static SoyMapImpl forProviderMapNoNullKeys(
      Map<SoyValue, ? extends SoyValueProvider> providerMap) {
    assertNoNullKeys(providerMap);
    return forProviderMap(providerMap);
  }

  @Nonnull
  public static SoyMapImpl forMutableProviderMapNoNullKeys(
      Map<SoyValue, ? extends SoyValueProvider> providerMap) {
    assertNoNullKeys(providerMap);
    return forMutableProviderMap(providerMap);
  }

  private static void assertNoNullKeys(Map<SoyValue, ? extends SoyValueProvider> providerMap) {
    for (SoyValue key : providerMap.keySet()) {
      if (key == null || key.isNullish()) {
        throw new IllegalArgumentException(
            String.format("null key in entry: null=%s", providerMap.get(key)));
      }
    }
  }

  private SoyMapImpl(Map<SoyValue, SoyValueProvider> providerMap) {
    checkNotNull(providerMap);
    if (providerMap.containsKey(null)) {
      // This shouldn't happen.
      throw new AssertionError();
    }
    this.providerMap = providerMap;
  }

  /** Map containing each data provider. */
  private final Map<SoyValue, ? extends SoyValueProvider> providerMap;

  @Override
  public int size() {
    return providerMap.size();
  }

  @Override
  @Nonnull
  public Iterable<? extends SoyValue> keys() {
    return providerMap.keySet();
  }

  @Override
  public boolean containsKey(SoyValue key) {
    return providerMap.containsKey(key);
  }

  @Override
  public SoyValue get(SoyValue key) {
    SoyValueProvider provider = getProvider(key);
    return (provider != null) ? provider.resolve() : null;
  }

  @Override
  public SoyValueProvider getProvider(SoyValue key) {
    return providerMap.get(key);
  }

  @Nonnull
  @Override
  public Map<SoyValue, ? extends SoyValueProvider> asJavaMap() {
    return providerMap;
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
    return System.identityHashCode(this);
  }

  @Override
  public boolean equals(Object other) {
    return other == this;
  }
}
