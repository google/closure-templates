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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SoyAbstractValue;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.StringData;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Frankenstein class that can represent a Soy legacy object map {@code legacy_object_map<K,V>}, a
 * Soy map {@code map<K, V>}, or a Soy record {@code [field1: type1, ...]} at runtime.
 *
 * <p>This class exists because the Java rendering APIs do not have enough structure. When a user
 * requests a template to be rendered with a {@link java.util.Map} as part of its data (either
 * {@link com.google.template.soy.jbcsrc.api.SoySauce.Renderer#setData regular data} or {@link
 * com.google.template.soy.jbcsrc.api.SoySauce.Renderer#setIj injected data}), it is generally not
 * possible to determine whether the {@code java.util.Map} is meant to represent a legacy object
 * map, a map, or a record. (Data can be passed transitively down a template call chain, so
 * inspecting the immediate callee's signature is not sufficient.)
 *
 * <p>This class implements all three interfaces ({@link
 * com.google.template.soy.data.SoyLegacyObjectMap}, {@link SoyMap}, and {@link
 * com.google.template.soy.data.SoyRecord} respectively). It would be easy to allow a DictImpl
 * instance to behave as one type (say, a legacy object map) and then another (say, a map) at
 * different points during a single rendering. But this would break Soy's cross-platform
 * compatibility. In JavaScript, legacy object maps and maps have different calling conventions
 * (plain JS objects and ES6 Maps, respectively). Indexing into a plain JS object as though it were
 * an ES6 Map is a runtime error. Soy's Java runtime must preserve this behavior.
 *
 * <p>{@link RuntimeType} exists to provide this non-compatibility. DictImpl instances begin life in
 * an {@link RuntimeType#UNKNOWN} state. If a SoyMap method is first invoked on it, it transitions
 * to the {@link RuntimeType#MAP} state, and later invocations of SoyMap or SoyRecord methods cause
 * runtime errors. Likewise, if a SoyMap or SoyRecord method is first invoked on the DictImpl
 * instance, it transitions to the {@link RuntimeType#LEGACY_OBJECT_MAP_OR_RECORD} state, and later
 * invocations of SoyMap methods cause runtime errors.
 *
 * every {@code legacy_object_map} to {@code map} and delete {@code legacy_object_map}. This will
 * require changing every plain JS object passed in to Soy to be an ES6 Map with the same entries.
 * It should not require any changes to Java renderers: every java.util.Map becomes a DictImpl
 * instance that can act as a {@code map}. After the migration, Java renderers that want to take
 * advantage of nonstring keys must wrap their java.util.Maps in {@link
 * com.google.template.soy.data.SoyValueConverter#markAsSoyMap}.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
@ParametersAreNonnullByDefault
public final class DictImpl extends SoyAbstractValue implements SoyDict, SoyMap {

  /**
   * Represents the runtime type of this DictImpl. The runtime type can only be set once. Changing
   * the state more than once will throw an exception. See discussion above.
   */
  public enum RuntimeType {
    /**
     * This DictImpl could represent a Soy {@code legacy_object_map}, a Soy {@code map}, or a Soy
     * record ({@code [field1: type1, ...]}). The first {@link SoyDict} or {@link
     * com.google.template.soy.data.SoyRecord} method invoked on this object will transition the
     * state to {@link #LEGACY_OBJECT_MAP_OR_RECORD}, while the first {@link SoyMap} method invoked
     * on this object will transition the state to {@link #MAP}.
     */
    UNKNOWN,
    /** This DictImpl represents a Soy {@code legacy_object_map} or record at runtime. */
    LEGACY_OBJECT_MAP_OR_RECORD,
    /** This DictImpl represents a Soy {@code map} at runtime. */
    MAP,
  }

  /**
   * Creates a SoyDict implementation for a particular underlying provider map.
   *
   * <p>The map may be mutable, but will not be mutated by the DictImpl.
   */
  public static DictImpl forProviderMap(
      Map<String, ? extends SoyValueProvider> providerMap, RuntimeType mapType) {
    return new DictImpl(providerMap, mapType);
  }

  private DictImpl(Map<String, ? extends SoyValueProvider> providerMap, RuntimeType mapType) {
    this.providerMap = checkNotNull(providerMap);
    this.mapType = checkNotNull(mapType);
  }

  /** Map containing each data provider. */
  private final Map<String, ? extends SoyValueProvider> providerMap;
  /**
   * An internal state that indicates whether this map implementation is intended for legacy map or
   * new map that supports ES6 and proto map.
   */
  private RuntimeType mapType;

  @Override
  public final boolean hasField(String name) {
    maybeSetLegacyObjectMapOrRecordType();
    return providerMap.containsKey(name);
  }

  @Override
  public final SoyValue getField(String name) {
    maybeSetLegacyObjectMapOrRecordType();
    return getFieldInternal(name);
  }

  private SoyValue getFieldInternal(String name) {
    SoyValueProvider provider = providerMap.get(name);
    return (provider != null) ? provider.resolve() : null;
  }

  @Override
  public final SoyValueProvider getFieldProvider(String name) {
    maybeSetLegacyObjectMapOrRecordType();
    return providerMap.get(name);
  }

  @Override
  public final int getItemCnt() {
    maybeSetLegacyObjectMapOrRecordType();
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
    maybeSetLegacyObjectMapOrRecordType();
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
    maybeSetLegacyObjectMapOrRecordType();
    return providerMap.containsKey(getStringKey(key));
  }

  @Override
  public boolean containsKey(SoyValue key) {
    maybeSetMapType();
    return providerMap.containsKey(getStringKey(key));
  }

  @Override
  public final SoyValue getItem(SoyValue key) {
    maybeSetLegacyObjectMapOrRecordType();
    return getFieldInternal(getStringKey(key));
  }

  @Override
  public SoyValue get(SoyValue key) {
    maybeSetMapType();
    return getFieldInternal(getStringKey(key));
  }

  @Override
  public final SoyValueProvider getItemProvider(SoyValue key) {
    maybeSetLegacyObjectMapOrRecordType();
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

  @Nonnull
  @Override
  public Map<? extends SoyValue, ? extends SoyValueProvider> asJavaMap() {
    ImmutableMap.Builder<SoyValue, SoyValueProvider> builder = ImmutableMap.builder();
    for (Map.Entry<String, ? extends SoyValueProvider> entry : providerMap.entrySet()) {
      builder.put(StringData.forValue(entry.getKey()), entry.getValue());
    }
    return builder.build();
  }

  private String getStringKey(SoyValue key) {
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
    boolean useNewSoyMap = mapType == RuntimeType.MAP;
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
   * Sets the internal state to {@link RuntimeType#LEGACY_OBJECT_MAP_OR_RECORD}. If the state has
   * already been set, throws an exception.
   */
  private void maybeSetLegacyObjectMapOrRecordType() {
    if (mapType == RuntimeType.UNKNOWN) {
      mapType = RuntimeType.LEGACY_OBJECT_MAP_OR_RECORD;
    } else if (mapType == RuntimeType.MAP) {
      throw new IllegalStateException(
          "Expected a value of type `map`, got `legacy_object_map`. "
              + "These two map types are not interoperable. "
              + "Use `map_to_legacy_object_map()` and `legacy_object_map_to_map()` "
              + "to convert explicitly.");
    }
  }

  /**
   * Sets the internal state to {@link RuntimeType#MAP}. If the state has already been set, throws
   * an exception.
   */
  private void maybeSetMapType() {
    if (mapType == RuntimeType.UNKNOWN) {
      mapType = RuntimeType.MAP;
    } else if (mapType == RuntimeType.LEGACY_OBJECT_MAP_OR_RECORD) {
      throw new IllegalStateException(
          "Expected a value of type `map`, got `legacy_object_map`. "
              + "These two map types are not interoperable. "
              + "Use `map_to_legacy_object_map()` and `legacy_object_map_to_map()` "
              + "to convert explicitly.");
    }
  }
}
