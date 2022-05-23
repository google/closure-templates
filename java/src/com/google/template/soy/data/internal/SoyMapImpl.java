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
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import java.util.Map;
import javax.annotation.Nonnull;
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
public final class SoyMapImpl extends AbstractSoyMap {
  public static final SoyMapImpl EMPTY = new SoyMapImpl(ImmutableMap.of());

  /** Creates a SoyDict implementation for a particular underlying provider map. */
  public static SoyMapImpl forProviderMap(
      Map<? extends SoyValue, ? extends SoyValueProvider> providerMap) {
    return new SoyMapImpl(providerMap);
  }

  private SoyMapImpl(Map<? extends SoyValue, ? extends SoyValueProvider> providerMap) {
    checkNotNull(providerMap);
    if (providerMap.containsKey(null)) {
      throw new IllegalArgumentException(
          String.format("null key in entry: null=%s", providerMap.get(null)));
    }
    this.providerMap = providerMap;
  }

  /** Map containing each data provider. */
  private final Map<? extends SoyValue, ? extends SoyValueProvider> providerMap;

  @Override
  public int size() {
    return providerMap.size();
  }

  @Override
  @Nonnull
  public final Iterable<? extends SoyValue> keys() {
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
  public Map<? extends SoyValue, ? extends SoyValueProvider> asJavaMap() {
    return providerMap;
  }
}
