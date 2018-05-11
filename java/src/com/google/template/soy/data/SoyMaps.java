/*
 * Copyright 2018 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.template.soy.data.internal.DictImpl;
import com.google.template.soy.data.internal.RuntimeMapTypeTracker;
import com.google.template.soy.data.internal.SoyMapImpl;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility methods for dealing with Soy maps.
 *
 * <p>These are useful for dealing with maps during the migration from {@link SoyLegacyObjectMap} to
 * {@link SoyMap}.
 */
// TODO(b/74360679): Delete this once all Soy plugins are passed SoyMaps.
public final class SoyMaps {

  /**
   * Returns {@code true} if this is a map (either a {@link SoyMap} or a {@link
   * SoyLegacyObjectMap}).
   */
  public static boolean isMapOrLegacyObjectMap(SoyValue value) {
    return value instanceof SoyMap || value instanceof SoyLegacyObjectMap;
  }

  /**
   * Returns the parameter as a {@link SoyMap}.
   *
   * <p>Instead of writing separate implementations for {@link SoyMap} and {@link
   * SoyLegacyObjectMap}, callers can pass this a {@link SoyMap} or {@link SoyLegacyObjectMap} and
   * use the return value to only write one implementation that uses {@link SoyMap}.
   */
  public static SoyMap asSoyMap(SoyValue map) {
    Preconditions.checkArgument(isMapOrLegacyObjectMap(map));
    if (map instanceof SoyMapImpl
        || (map instanceof DictImpl
            && ((DictImpl) map).getMapType() == RuntimeMapTypeTracker.Type.MAP)) {
      // These types can already be accessed as a SoyMap, so just return the map directly.
      return (SoyMap) map;
    } else {
      // For other implementations of SoyLegacyObjectMap, convert to a SoyMap. For DictImpl with a
      // runtime type of LEGACY_OBJECT_MAP_OR_RECORD, we must convert the map to a SoyMap so
      // DictImpl doesn't throw an exception when using SoyMap methods on a map marked with the
      // runtime type of LEGACY_OBJECT_MAP_OR_RECORD. Similarly, for DictImpl with a runtime type of
      // UNKNOWN, we also treat this as a SoyLegacyObjectMap to retain the existing behavior in
      // plugins (which check for "instanceof SoyLegacyObjectMap" then use SoyLegacyObjectMap's
      // methods).
      return legacyObjectMapToMap((SoyLegacyObjectMap) map);
    }
  }

  /** Converts the parameter to a {@link SoyMap}. */
  public static SoyMap legacyObjectMapToMap(SoyLegacyObjectMap map) {
    Map<SoyValue, SoyValueProvider> newMap = new HashMap<>();
    for (SoyValue key : map.getItemKeys()) {
      newMap.put(key, map.getItemProvider(key));
    }
    return SoyMapImpl.forProviderMap(newMap);
  }
}
