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

import com.google.template.soy.data.restricted.PrimitiveData;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
public abstract class SoyMap extends SoyRecord {

  /**
   * Gets the number of items in this SoyMap.
   *
   * @return The number of items.
   */
  public abstract int size();

  /**
   * Gets an iterable over all item keys in this SoyMap.
   *
   * <p>Iteration order is undefined.
   *
   * @return An iterable over all item keys.
   */
  @Nonnull
  public Iterable<? extends SoyValue> keys() {
    return asJavaMap().keySet();
  }

  public Collection<? extends SoyValueProvider> values() {
    return asJavaMap().values();
  }

  public Set<? extends Map.Entry<? extends SoyValue, ? extends SoyValueProvider>> entrySet() {
    return asJavaMap().entrySet();
  }

  /**
   * Checks whether this SoyMap has an item with the given key.
   *
   * @param key The item key to check.
   * @return Whether this SoyMap has an item with the given key.
   */
  public abstract boolean containsKey(SoyValue key);

  /**
   * Gets an item value of this SoyMap.
   *
   * @param key The item key to get.
   * @return The item value for the given item key, or null if no such item key.
   */
  public abstract SoyValue get(SoyValue key);

  /**
   * Gets a provider of an item value of this SoyMap.
   *
   * @param key The item key to get.
   * @return A provider of the item value for the given item key, or null if no such item key.
   */
  public abstract SoyValueProvider getProvider(SoyValue key);

  /**
   * Clears all items from this SoyMap.
   *
   * <p>Mutable maps are only allowed in externs.
   *
   * @throws SoyDataException if the map is immutable.
   */
  @Nullable
  public /* void */ Object clear() {
    throw new SoyDataException(
        String.format("Map of type %s does not support clear()", getSoyTypeName()));
  }

  /**
   * Deletes a value from this SoyMap.
   *
   * <p>Mutable maps are only allowed in externs.
   *
   * @param key The item key to delete.
   * @return Whether an item was deleted.
   * @throws SoyDataException if the map is immutable.
   */
  public boolean delete(SoyValue key) {
    throw new SoyDataException(
        String.format("Map of type %s does not support delete()", getSoyTypeName()));
  }

  /**
   * Sets an item value of this SoyMap.
   *
   * <p>Mutable maps are only allowed in externs.
   *
   * @param key The item key to set.
   * @param value The item value to set.
   * @return This SoyMap for chaining.
   * @throws SoyDataException if the map is immutable.
   */
  public SoyMap set(SoyValue key, SoyValue value) {
    return set(key, (SoyValueProvider) value);
  }

  /**
   * Sets an item value of this SoyMap.
   *
   * <p>Mutable maps are only allowed in externs.
   *
   * @param key The item key to set.
   * @param value The item value to set.
   * @return This SoyMap for chaining.
   * @throws SoyDataException if the map is immutable.
   */
  public SoyMap set(SoyValue key, SoyValueProvider value) {
    throw new SoyDataException(
        String.format("Map of type %s does not support set()", getSoyTypeName()));
  }

  @Override
  public final String coerceToString() {
    LoggingAdvisingAppendable mapStr = LoggingAdvisingAppendable.buffering();
    try {
      render(mapStr);
    } catch (IOException e) {
      throw new AssertionError(e); // impossible
    }
    return mapStr.toString();
  }

  @Override
  public void render(LoggingAdvisingAppendable appendable) throws IOException {
    appendable.append('{');

    boolean isFirst = true;
    for (SoyValue key : keys()) {
      SoyValue value = get(key);
      if (isFirst) {
        isFirst = false;
      } else {
        appendable.append(", ");
      }
      key.render(appendable);
      appendable.append(": ");
      value.render(appendable);
    }
    appendable.append('}');
  }

  @Override
  public String toString() {
    return coerceToString();
  }

  @Override
  public String getSoyTypeName() {
    return "map";
  }

  // LINT.IfChange(allowed_soy_map_key_types)
  public static boolean isAllowedKeyType(SoyValue key) {
    return key instanceof PrimitiveData && !key.isNullish();
  }
  // LINT.ThenChange(../types/MapType.java:allowed_soy_map_key_types)
}
