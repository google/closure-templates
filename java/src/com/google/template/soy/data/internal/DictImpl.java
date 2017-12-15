/*
 * Copyright 2013 Google Inc.
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

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SoyAbstractValue;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyNewMap;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.StringData;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Internal implementation of SoyDict in terms of a map. Do not use directly; instead, use {@link
 * com.google.template.soy.data.SoyValueConverter#convert}.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
@ParametersAreNonnullByDefault
public final class DictImpl extends SoyAbstractValue implements SoyDict, SoyNewMap {

  /**
   * An enum representing the runtime map type of this particular map implementation. The runtime
   * type is initiliazed as UNKNOWN, and can only be set once. Changing the state more than once
   * will throw an exception.
   */
  private enum RuntimeMapType {
    /**
     * UNKNOWN means we don't know about the type. This is the initial value, and will be assigned
     * to other values when a particular set of APIs are invoked.
     */
    UNKNOWN,
    /** LEGACY_OBJECT_MAP represents the legacy_object_map type. */
    LEGACY_OBJECT_MAP,
    /** MAP represents the map type. */
    MAP,
  }

  /**
   * Creates a SoyDict implementation for a particular underlying provider map.
   *
   * <p>The map may be mutable, but will not be mutated by the DictImpl.
   */
  public static DictImpl forProviderMap(Map<String, ? extends SoyValueProvider> providerMap) {
    return new DictImpl(providerMap);
  }

  private DictImpl(Map<String, ? extends SoyValueProvider> providerMap) {
    this.providerMap = checkNotNull(providerMap);
    this.mapType = RuntimeMapType.UNKNOWN;
  }

  /** Map containing each data provider. */
  protected final Map<String, ? extends SoyValueProvider> providerMap;
  /**
   * An internal state that indicates whether this map implementation is intended for legacy map or
   * new map that supports ES6 and proto map.
   */
  private RuntimeMapType mapType;

  @Override
  public final boolean hasField(String name) {
    return providerMap.containsKey(name);
  }

  @Override
  public final SoyValue getField(String name) {
    SoyValueProvider provider = providerMap.get(name);
    return (provider != null) ? provider.resolve() : null;
  }

  @Override
  public final SoyValueProvider getFieldProvider(String name) {
    return providerMap.get(name);
  }

  @Override
  public final int getItemCnt() {
    maybeSetLegacyObjectMapType();
    return providerMap.size();
  }

  @Override
  public int size() {
    maybeSetMapType();
    return providerMap.size();
  }

  @Override
  @Nonnull
  public final Iterable<? extends SoyValue> getItemKeys() {
    maybeSetLegacyObjectMapType();
    return Iterables.transform(
        providerMap.keySet(),
        new Function<String, SoyValue>() {
          @Override
          public SoyValue apply(String key) {
            return StringData.forValue(key);
          }
        });
  }

  @Nonnull
  @Override
  public Iterable<? extends SoyValue> keys() {
    maybeSetMapType();
    return Iterables.transform(
        providerMap.keySet(),
        new Function<String, SoyValue>() {
          @Override
          public SoyValue apply(String key) {
            return StringData.forValue(key);
          }
        });
  }

  @Override
  public final boolean hasItem(SoyValue key) {
    maybeSetLegacyObjectMapType();
    return providerMap.containsKey(getStringKey(key));
  }

  @Override
  public boolean containsKey(SoyValue key) {
    maybeSetMapType();
    return providerMap.containsKey(getStringKey(key));
  }

  @Override
  public final SoyValue getItem(SoyValue key) {
    maybeSetLegacyObjectMapType();
    return getField(getStringKey(key));
  }

  @Override
  public SoyValue get(SoyValue key) {
    maybeSetMapType();
    return getField(getStringKey(key));
  }

  @Override
  public final SoyValueProvider getItemProvider(SoyValue key) {
    maybeSetLegacyObjectMapType();
    return providerMap.get(getStringKey(key));
  }

  @Override
  public SoyValueProvider getProvider(SoyValue key) {
    maybeSetMapType();
    return providerMap.get(getStringKey(key));
  }

  @Override
  @Nonnull
  public final Map<String, ? extends SoyValueProvider> asJavaStringMap() {
    return Collections.unmodifiableMap(providerMap);
  }

  @Override
  @Nonnull
  public final Map<String, ? extends SoyValue> asResolvedJavaStringMap() {
    return Maps.transformValues(asJavaStringMap(), Transforms.RESOLVE_FUNCTION);
  }

  protected final String getStringKey(SoyValue key) {
    try {
      return key.stringValue();
    } catch (SoyDataException e) {
      throw new SoyDataException(
          "SoyDict accessed with non-string key (got key type " + key.getClass().getName() + ").");
    }
  }

  @Override
  public final boolean coerceToBoolean() {
    return true;
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
    boolean useNewSoyMap = mapType == RuntimeMapType.MAP;
    for (SoyValue key : (useNewSoyMap ? keys() : getItemKeys())) {
      SoyValue value = useNewSoyMap ? get(key) : getItem(key);
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
  public final boolean equals(Object other) {
    // Instance equality, to match Javascript behavior.
    return this == other;
  }

  @Override
  public final int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public String toString() {
    // TODO(gboyer): Remove this override, and instead change RenderVisitor to use coerceToString()
    // instead of simply toString().  Alternately, have SoyAbstractValue ensure that toString()
    // always matchse coerceToString().
    return coerceToString();
  }

  /**
   * Set the internal state to legacy object map. If the state has already been set, throw an
   * exception.
   */
  private void maybeSetLegacyObjectMapType() {
    if (mapType == RuntimeMapType.UNKNOWN) {
      mapType = RuntimeMapType.LEGACY_OBJECT_MAP;
    } else if (mapType == RuntimeMapType.MAP) {
      throw new IllegalStateException(
          "Expected a value of type `map`, got `legacy_object_map`. "
              + "These two map types are not interoperable.");
    }
  }

  /** Set the internal state to map. If the state has already been set, throw an exception. */
  private void maybeSetMapType() {
    if (mapType == RuntimeMapType.UNKNOWN) {
      mapType = RuntimeMapType.MAP;
    } else if (mapType == RuntimeMapType.LEGACY_OBJECT_MAP) {
      throw new IllegalStateException(
          "Expected a value of type `legacy_object_map`, got `map`. "
              + "These two map types are not interoperable.");
    }
  }
}
