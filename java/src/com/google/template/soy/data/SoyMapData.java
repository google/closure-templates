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
import java.util.Map;
import java.util.regex.Pattern;


/**
 * A map data node in a Soy data tree.
 *
 * @author Kai Huang
 */
public class SoyMapData extends CollectionData {


  /** Underlying map. */
  private final Map<String, SoyData> map;


  public SoyMapData() {
    map = Maps.newHashMap();
  }


  /**
   * Constructor that initializes this SoyMapData from an existing map.
   * @param data The initial data in an existing map.
   */
  public SoyMapData(Map<String, ?> data) {

    this();

    for (Map.Entry<String, ?> entry : data.entrySet()) {

      String key;
      try {
        key = entry.getKey();
      } catch (ClassCastException cce) {
        throw new SoyDataException(
            "Attempting to convert a map with non-string key to Soy data (key type " +
            ((Map.Entry<?, ?>) entry).getKey().getClass().getSimpleName() + ").");
      }
      checkKey(key);

      Object value = entry.getValue();

      map.put(key, SoyData.createFromExistingData(value));
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

    mapStr.append("}");
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
  // Protected/private helpers.


  /** Pattern for a valid key for SoyMapData (an identifier). */
  private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z_0-9]*");


  @Override protected void checkKey(String key) throws IllegalArgumentException {
    if (!KEY_PATTERN.matcher(key).matches()) {
      throw new IllegalArgumentException("Illegal data key '" + key + "' for map data.");
    }
  }


  @Override protected void putSingle(String key, SoyData value) {
    map.put(key, value);
  }


  @Override protected void removeSingle(String key) {
    map.remove(key);
  }


  @Override protected SoyData getSingle(String key) {
    return map.get(key);
  }

}
