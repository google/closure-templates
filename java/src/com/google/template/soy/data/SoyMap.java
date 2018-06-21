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

package com.google.template.soy.data;

import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A map containing key-to-value mappings referred to as items. Each key is a SoyValue (must be
 * already resolved) and each value is a SoyValue (can be unresolved).
 *
 * <p>This is a new interface (compared to {@link SoyLegacyObjectMap}) that was designed for
 * supporting proto map. There are two different Soy map types, one uses ES6 map in JS, and another
 * uses plain object. This interface tries to use the same APIs as regular Java maps.
 *
 * <p>Notably this interface and SoyMap has completely different APIs. In JS backends, these two
 * different Soy map types are not interoperable during the runtime. We need to mimic this behavior
 * in other backends. Therefore, in Java backends, if we are unsure about the runtime type of the
 * map instances (since they are all Java maps), the first time the map is accessed (via one set of
 * APIs), we set the state of map type to the one corresponding to the APIs. If the map is accessed
 * later via a different set of APIs, a run time exception will be thrown.
 */
@ParametersAreNonnullByDefault
public interface SoyMap extends SoyValue {

  /**
   * Gets the number of items in this SoyMap.
   *
   * @return The number of items.
   */
  int size();

  /**
   * Gets an iterable over all item keys in this SoyMap.
   *
   * <p>Important: Iteration order is undefined.
   *
   * @return An iterable over all item keys.
   */
  @Nonnull
  Iterable<? extends SoyValue> keys();

  /**
   * Checks whether this SoyMap has an item with the given key.
   *
   * @param key The item key to check.
   * @return Whether this SoyMap has an item with the given key.
   */
  boolean containsKey(SoyValue key);

  /**
   * Gets an item value of this SoyMap.
   *
   * @param key The item key to get.
   * @return The item value for the given item key, or null if no such item key.
   */
  SoyValue get(SoyValue key);

  /**
   * Gets a provider of an item value of this SoyMap.
   *
   * @param key The item key to get.
   * @return A provider of the item value for the given item key, or null if no such item key.
   */
  SoyValueProvider getProvider(SoyValue key);

  /**
   * Gets a Java map of all items in this SoyMap, where mappings are value to value provider. Note
   * that value providers are often just the values themselves, since all values are also providers.
   *
   * @return A Java map of all items, where mappings are value to value provider.
   */
  @Nonnull
  Map<? extends SoyValue, ? extends SoyValueProvider> asJavaMap();
}
