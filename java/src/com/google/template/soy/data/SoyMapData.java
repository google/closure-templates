/*
 * Copyright 2008 Google Inc.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.template.soy.data.restricted.CollectionData;
import com.google.template.soy.data.restricted.StringData;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;

/**
 * A map data node in a Soy data tree.
 *
 * @deprecated Users of this class should use normal {@code java.util.Map}s instead. The Soy
 *     rendering APIs can automatically handle conversion of native Java types and Soy plugin users
 *     can directly use {@link SoyValueConverter#convert(Object)}. This class offers no benefits
 *     over those APIs.
 */
@Deprecated
public final class SoyMapData extends SoyDict implements CollectionData {

  /** Underlying map. */
  private final Map<String, SoyValue> map;

  public SoyMapData() {
    map = Maps.newLinkedHashMap();
  }

  /** Initializes this SoyMapData from an existing map. */
  public SoyMapData(Map<String, ?> data) {
    map = new LinkedHashMap<>(data.size());

    for (Map.Entry<String, ?> entry : data.entrySet()) {
      String key;
      try {
        key = entry.getKey();
      } catch (ClassCastException cce) {
        throw new SoyDataException(
            "Attempting to convert a map with non-string key to Soy data (key type "
                + ((Map.Entry<?, ?>) entry).getKey().getClass().getName()
                + ").",
            cce);
      }

      Object value = entry.getValue();

      try {
        map.put(key, CollectionData.createFromExistingData(value));
      } catch (SoyDataException sde) {
        sde.prependKeyToDataPath(key);
        throw sde;
      }
    }
  }

  /**
   * Constructor that directly takes the keys/values as parameters.
   *
   * @param data The initial data, with alternating keys/values.
   */
  public SoyMapData(Object... data) {
    this();
    put(data);
  }

  /** Returns a view of this SoyMapData object as a Map. */
  public Map<String, SoyValue> asMap() {
    return Collections.unmodifiableMap(map);
  }

  /**
   * Gets the keys in this map data.
   *
   * @return A set containing the keys in this map data.
   */
  public Set<String> getKeys() {
    return Collections.unmodifiableSet(map.keySet());
  }

  // -----------------------------------------------------------------------------------------------
  // Superpackage-private methods.

  /**
   * Puts data into this data object at the specified key.
   *
   * @param key An individual key.
   * @param value The data to put at the specified key.
   */
  @Override
  public void putSingle(String key, SoyValue value) {
    map.put(key, value);
  }

  /**
   * Removes the data at the specified key.
   *
   * @param key An individual key.
   */
  @Override
  public void removeSingle(String key) {
    map.remove(key);
  }

  /**
   * Gets the data at the specified key.
   *
   * @param key An individual key.
   * @return The data at the specified key, or null if the key is not defined.
   */
  @Override
  public SoyValue getSingle(String key) {
    return map.get(key);
  }

  // -----------------------------------------------------------------------------------------------
  // SoyDict.

  @Override
  @Nonnull
  public Map<String, ? extends SoyValueProvider> asJavaStringMap() {
    return asMap();
  }

  @Override
  @Nonnull
  public Map<String, ? extends SoyValue> asResolvedJavaStringMap() {
    return asMap();
  }

  // -----------------------------------------------------------------------------------------------
  // SoyRecord.

  @Override
  public boolean hasField(RecordProperty name) {
    return getSingle(name.getName()) != null;
  }

  @Override
  public SoyValue getField(RecordProperty name) {
    return getSingle(name.getName());
  }

  @Override
  public SoyValueProvider getFieldProvider(RecordProperty name) {
    return getSingle(name.getName());
  }

  @Override
  public ImmutableMap<String, SoyValueProvider> recordAsMap() {
    return ImmutableMap.copyOf(map);
  }

  @Override
  public void forEach(BiConsumer<RecordProperty, ? super SoyValueProvider> action) {
    map.forEach((key, value) -> action.accept(RecordProperty.get(key), value));
  }

  @Override
  public int recordSize() {
    return map.size();
  }

  // -----------------------------------------------------------------------------------------------
  // SoyLegacyObjectMap.

  @Override
  public int getItemCnt() {
    return getKeys().size();
  }

  @Override
  @Nonnull
  public Iterable<StringData> getItemKeys() {
    Set<String> internalKeys = getKeys();
    List<StringData> keys = Lists.newArrayListWithCapacity(internalKeys.size());
    for (String internalKey : internalKeys) {
      keys.add(StringData.forValue(internalKey));
    }
    return keys;
  }

  @Override
  public boolean hasItem(SoyValue key) {
    return getSingle(getStringKey(key)) != null;
  }

  @Override
  public SoyValue getItem(SoyValue key) {
    return getSingle(getStringKey(key));
  }

  @Override
  public SoyValueProvider getItemProvider(SoyValue key) {
    return getSingle(getStringKey(key));
  }

  // -----------------------------------------------------------------------------------------------
  // SoyMap.

  @Override
  public int size() {
    return getKeys().size();
  }

  @Nonnull
  @Override
  public Iterable<? extends SoyValue> keys() {
    return Iterables.transform(map.keySet(), StringData::forValue);
  }

  @Override
  public boolean containsKey(SoyValue key) {
    return getSingle(getStringKey(key)) != null;
  }

  @Override
  public SoyValue get(SoyValue key) {
    return getSingle(getStringKey(key));
  }

  @Override
  public SoyValueProvider getProvider(SoyValue key) {
    return getSingle(getStringKey(key));
  }

  @Nonnull
  @Override
  public Map<? extends SoyValue, ? extends SoyValueProvider> asJavaMap() {
    ImmutableMap.Builder<SoyValue, SoyValueProvider> builder = ImmutableMap.builder();
    for (Map.Entry<String, SoyValue> entry : map.entrySet()) {
      builder.put(StringData.forValue(entry.getKey()), entry.getValue());
    }
    return builder.build();
  }

  /**
   * Gets the string key out of a SoyValue key, or throws SoyDataException if the key is not a
   * string.
   *
   * @param key The SoyValue key.
   * @return The string key.
   */
  private String getStringKey(SoyValue key) {
    try {
      return key.stringValue();
    } catch (ClassCastException e) {
      throw new SoyDataException(
          "SoyDict accessed with non-string key (got key type " + key.getSoyTypeName() + ").", e);
    }
  }

  @Override
  public String getSoyTypeName() {
    return "map";
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public boolean equals(Object other) {
    return other == this;
  }
}
