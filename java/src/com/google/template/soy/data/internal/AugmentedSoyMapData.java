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

package com.google.template.soy.data.internal;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.SoyMapData;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Augmented map data combining a base map data object with some additional data that may hide some
 * of the base map data (if they have the same keys).
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class AugmentedSoyMapData extends SoyMapData {


  /** The base map data object. */
  private final SoyMapData baseData;


  /**
   * @param baseData The base map data object.
   */
  public AugmentedSoyMapData(SoyMapData baseData) {
    Preconditions.checkNotNull(baseData);
    this.baseData = baseData;
  }


  @Override public Map<String, SoyData> asMap() {

    SoyMapData combinedMapData = new SoyMapData();
    addMapDataHelper(combinedMapData, "", baseData.asMap());
    addMapDataHelper(combinedMapData, "", super.asMap());
    return Collections.unmodifiableMap(combinedMapData.asMap());
  }


  /**
   * Private helper for {@code asMap()} to add a map (or submap) to the combined map data.
   * @param combinedMapData The combined map data that we're building.
   * @param keyPrefix The key prefix if adding a submap (or empty string if adding one of the
   *     top-level maps: the base data or the augmented data).
   * @param map The map of data to add.
   */
  private static void addMapDataHelper(
      SoyMapData combinedMapData, String keyPrefix, Map<String, SoyData> map) {

    for (Map.Entry<String, SoyData> entry : map.entrySet()) {
      String key = entry.getKey();
      SoyData value = entry.getValue();
      if (value instanceof SoyMapData) {
        addMapDataHelper(combinedMapData, keyPrefix + key + ".", ((SoyMapData) value).asMap());
      } else if (value instanceof SoyListData) {
        addListDataHelper(combinedMapData, keyPrefix + key + ".", ((SoyListData) value).asList());
      } else {
        combinedMapData.put(keyPrefix + key, value);
      }
    }
  }


  /**
   * Private helper for {@code asMap()} to add a sublist to the combined map data.
   * @param combinedMapData The combined map data that we're building.
   * @param keyPrefix The key prefix for this sublist.
   * @param list The list of data to add.
   */
  private static void addListDataHelper(
      SoyMapData combinedMapData, String keyPrefix, List<SoyData> list) {

    for (int i = 0; i < list.size(); ++i) {
      SoyData el = list.get(i);
      if (el instanceof SoyMapData) {
        addMapDataHelper(combinedMapData, keyPrefix + i + ".", ((SoyMapData) el).asMap());
      } else if (el instanceof SoyListData) {
        addListDataHelper(combinedMapData, keyPrefix + i + ".", ((SoyListData) el).asList());
      } else {
        combinedMapData.put(keyPrefix + i, el);
      }
    }
  }


  @Override public Set<String> getKeys() {
    return Collections.unmodifiableSet(Sets.union(super.getKeys(), baseData.getKeys()));
  }


  @Override public String toString() {
    return toStringHelper(asMap());
  }


  @Override public boolean toBoolean() {
    return true;
  }


  @Override public void put(String keyStr, SoyData value) {
    if (keyStr.indexOf('.') >= 0) {
      throw new SoyDataException(
          "Attempted to put multi-part key string into AugmentedSoyMapData. Please ensure that" +
          " all of your 'param' commands only use top-level keys.");
    }
    super.putSingle(keyStr, value);
  }


  // Note: No need to override putSingle since it would do same thing as super method.


  /**
   * Removal of data from AugmentedSoyMapData is not well defined, so it's prohibited.
   */
  @Override public void remove(String keyStr) {
    throw new UnsupportedOperationException();
  }


  /**
   * Removal of data from AugmentedSoyMapData is not well defined, so it's prohibited.
   */
  @Override public void removeSingle(String key) {
    throw new UnsupportedOperationException();
  }


  @Override public SoyData getSingle(String key) {

    SoyData value = super.getSingle(key);
    if (value != null) {
      return value;
    }
    return baseData.getSingle(key);
  }

}
