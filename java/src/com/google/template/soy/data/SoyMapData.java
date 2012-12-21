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

import com.google.common.collect.Maps;
import com.google.template.soy.data.restricted.CollectionData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;


/**
 * A map data node in a Soy data tree.
 *
 * @author Kai Huang
 */
public class SoyMapData extends CollectionData {


  /** Underlying map. */
  private final Map<String, SoyData> map;


  public SoyMapData() {
    map = Maps.newLinkedHashMap();
  }


  /**
   * Constructor that initializes this SoyMapData from an existing map.
   * @param data The initial data in an existing map.
   */
  public SoyMapData(Map<String, ?> data) {

    map = new LinkedHashMap<String, SoyData>(data.size());

    for (Map.Entry<String, ?> entry : data.entrySet()) {

      String key;
      try {
        key = entry.getKey();
      } catch (ClassCastException cce) {
        throw new SoyDataException(
            "Attempting to convert a map with non-string key to Soy data (key type " +
            ((Map.Entry<?, ?>) entry).getKey().getClass().getName() + ").");
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
   * Returns a view of this SoyMapData object as a Map.
   */
  public Map<String, SoyData> asMap() {
    return Collections.unmodifiableMap(map);
  }


  /**
   * Gets the keys in this map data.
   * @return A set containing the keys in this map data.
   */
  public Set<String> getKeys() {
    return Collections.unmodifiableSet(map.keySet());
  }


  /**
   * {@inheritDoc}
   *
   * <p> This method should only be used for debugging purposes.
   */
  @Override public String toString() {
    return toStringHelper(map);
  }


  /**
   * Protected helper for {toString()}. Turns a regular Map into a string.
   * @param map The map to turn into a string.
   * @return The built string.
   */
  protected String toStringHelper(Map<String, SoyData> map) {

    StringBuilder mapStr = new StringBuilder();
    mapStr.append('{');

    boolean isFirst = true;
    for (Map.Entry<String, SoyData> entry : map.entrySet()) {
      if (isFirst) {
        isFirst = false;
      } else {
        mapStr.append(", ");
      }
      mapStr.append(entry.getKey()).append(": ").append(entry.getValue().toString());
    }

    mapStr.append('}');
    return mapStr.toString();
  }


  /**
   * {@inheritDoc}
   *
   * <p> A map is always truthy.
   */
  @Override public boolean toBoolean() {
    return true;
  }


  @Override public boolean equals(Object other) {
    return this == other;  // fall back to object equality
  }


  // -----------------------------------------------------------------------------------------------
  // Superpackage-private methods.


  /**
   * Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * Puts data into this data object at the specified key.
   * @param key An individual key.
   * @param value The data to put at the specified key.
   */
  @Override public void putSingle(String key, SoyData value) {
    map.put(key, value);
  }


  /**
   * Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * Removes the data at the specified key.
   * @param key An individual key.
   */
  @Override public void removeSingle(String key) {
    map.remove(key);
  }


  /**
   * Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * Gets the data at the specified key.
   * @param key An individual key.
   * @return The data at the specified key, or null if the key is not defined.
   */
  @Override public SoyData getSingle(String key) {
    return map.get(key);
  }

}
