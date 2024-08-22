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

import com.google.common.collect.ImmutableList;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A map containing key-to-value mappings referred to as items. Each key is a SoyValue (must be
 * already resolved) and each value is a SoyValue (can be unresolved).
 *
 * @deprecated This interface is the Java representation of the Soy type {@code legacy_object_map}.
 *     The modern Soy type is {@code map}, and its Java representation is {@link SoyMap}. Use those
 *     instead.
 */
@Deprecated
@ParametersAreNonnullByDefault
public abstract class SoyLegacyObjectMap extends SoyValue {

  /**
   * Gets the number of items in this SoyMap.
   *
   * @return The number of items.
   */
  public int getItemCnt() {
    return 0;
  }

  /**
   * Gets an iterable over all item keys in this SoyMap.
   *
   * <p>Important: Iteration order is undefined.
   *
   * @return An iterable over all item keys.
   */
  public Iterable<? extends SoyValue> getItemKeys() {
    return ImmutableList.of();
  }

  /**
   * Checks whether this SoyMap has an item with the given key.
   *
   * @param key The item key to check.
   * @return Whether this SoyMap has an item with the given key.
   */
  public boolean hasItem(SoyValue key) {
    return false;
  }

  /**
   * Gets an item value of this SoyMap.
   *
   * @param key The item key to get.
   * @return The item value for the given item key, or null if no such item key.
   */
  public SoyValue getItem(SoyValue key) {
    return null;
  }

  /**
   * Gets a provider of an item value of this SoyMap.
   *
   * @param key The item key to get.
   * @return A provider of the item value for the given item key, or null if no such item key.
   */
  public SoyValueProvider getItemProvider(SoyValue key) {
    return null;
  }

  @Override
  public final boolean coerceToBoolean() {
    return true;
  }
}
