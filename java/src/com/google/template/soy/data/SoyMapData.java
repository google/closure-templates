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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.template.soy.data.restricted.CollectionData;
import com.google.template.soy.data.restricted.StringData;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * A map data node in a Soy data tree.
 *
 * <p>Important: Even though this class is not marked 'final', do not extend this class.
 *
 */
public class SoyMapData extends CollectionData implements SoyDict {

  /** Underlying map. */
  private final Map<String, SoyData> map;

  public SoyMapData() {
    map = Maps.newLinkedHashMap();
  }

  /**
   * Constructor that initializes this SoyMapData from an existing map.
   *
   * @param data The initial data in an existing map.
   */
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
                + ").");
      }

      Object value = entry.getValue();

      try {
        map.put(key, SoyData.createFromExistingData(value));
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

  /**
   * Important: Please treat this method as superpackage-private. Do not call this method from
   * outside the 'tofu' and 'data' packages.
   *
   * <p>Returns a view of this SoyMapData object as a Map.
   */
  public Map<String, SoyData> asMap() {
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

  /**
   * {@inheritDoc}
   *
   * <p>This method should only be used for debugging purposes.
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    try {
      render(sb);
    } catch (IOException e) {
      throw new RuntimeException(e); // impossible
    }
    return sb.toString();
  }

  @Override
  public void render(Appendable appendable) throws IOException {
    appendable.append('{');
    Iterator<Map.Entry<String, SoyData>> iterator = map.entrySet().iterator();
    if (iterator.hasNext()) {
      Map.Entry<String, SoyData> entry = iterator.next();
      appendable.append(entry.getKey()).append(": ");
      entry.getValue().render(appendable);
      while (iterator.hasNext()) {
        appendable.append(", ");
        entry = iterator.next();
        appendable.append(entry.getKey()).append(": ");
        entry.getValue().render(appendable);
      }
    }
    appendable.append('}');
  }

  /**
   * {@inheritDoc}
   *
   * <p>A map is always truthy.
   */
  @Override
  public boolean coerceToBoolean() {
    return true;
  }

  @Override
  public String coerceToString() {
    return toString();
  }

  @Override
  public final boolean equals(Object other) {
    return this == other; // fall back to object equality
  }

  @Override
  public final int hashCode() {
    return System.identityHashCode(this);
  }

  // -----------------------------------------------------------------------------------------------
  // Superpackage-private methods.

  /**
   * Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * <p>Puts data into this data object at the specified key.
   *
   * @param key An individual key.
   * @param value The data to put at the specified key.
   */
  @Override
  public void putSingle(String key, SoyData value) {
    map.put(key, value);
  }

  /**
   * Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * <p>Removes the data at the specified key.
   *
   * @param key An individual key.
   */
  @Override
  public void removeSingle(String key) {
    map.remove(key);
  }

  /**
   * Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * <p>Gets the data at the specified key.
   *
   * @param key An individual key.
   * @return The data at the specified key, or null if the key is not defined.
   */
  @Override
  public SoyData getSingle(String key) {
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
  public boolean hasField(String name) {
    return getSingle(name) != null;
  }

  @Override
  public SoyValue getField(String name) {
    return getSingle(name);
  }

  @Override
  public SoyValueProvider getFieldProvider(String name) {
    return getSingle(name);
  }

  // -----------------------------------------------------------------------------------------------
  // SoyMap.

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
          "SoyDict accessed with non-string key (got key type " + key.getClass().getName() + ").");
    }
  }
}
