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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.RecordProperty;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.StringData;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiConsumer;
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
 * <p>{@link RuntimeMapTypeTracker} exists to provide this non-compatibility. DictImpl instances
 * begin life in an {@link RuntimeMapTypeTracker.Type#UNKNOWN} state. If a SoyMap method is first
 * invoked on it, it transitions to the {@link RuntimeMapTypeTracker.Type#MAP} state, and later
 * invocations of SoyMap or SoyRecord methods cause runtime errors. Likewise, if a SoyMap or
 * SoyRecord method is first invoked on the DictImpl instance, it transitions to the {@link
 * RuntimeMapTypeTracker.Type#LEGACY_OBJECT_MAP_OR_RECORD} state, and later invocations of SoyMap
 * methods cause runtime errors.
 *
 * <p>
 * We want to migrate every {@code legacy_object_map} to {@code map} and delete {@code
 * legacy_object_map}. This will require changing every plain JS object passed in to Soy to be an
 * ES6 Map with the same entries. It should not require any changes to Java renderers: every
 * java.util.Map becomes a DictImpl instance that can act as a {@code map}. After the migration,
 * Java renderers that want to take advantage of nonstring keys must wrap their java.util.Maps in
 * {@link com.google.template.soy.data.SoyValueConverter#markAsSoyMap}.
 */
@ParametersAreNonnullByDefault
public final class DictImpl extends SoyDict {

  /**
   * Creates a SoyDict implementation for a particular underlying provider map.
   *
   * <p>The map may be mutable, but will not be mutated by the DictImpl.
   */
  @Nonnull
  public static DictImpl forProviderMap(
      Map<String, ? extends SoyValueProvider> providerMap, RuntimeMapTypeTracker.Type mapType) {
    return new DictImpl(providerMap, mapType);
  }

  private DictImpl(
      Map<String, ? extends SoyValueProvider> providerMap, RuntimeMapTypeTracker.Type typeTracker) {
    this.providerMap = checkNotNull(providerMap);
    this.typeTracker = new RuntimeMapTypeTracker(checkNotNull(typeTracker));
  }

  /** Map containing each data provider. */
  private final Map<String, ? extends SoyValueProvider> providerMap;

  /**
   * Tracks whether this map implementation is intended for legacy map or new map that supports ES6
   * and proto map.
   */
  private final RuntimeMapTypeTracker typeTracker;

  @Override
  public boolean hasField(RecordProperty symbol) {
    typeTracker.maybeSetLegacyObjectMapOrRecordType();
    return providerMap.containsKey(symbol.getName());
  }

  @Override
  public SoyValue getField(RecordProperty symbol) {
    typeTracker.maybeSetLegacyObjectMapOrRecordType();
    return getFieldInternal(symbol);
  }

  private SoyValue getFieldInternal(RecordProperty symbol) {
    return getFieldInternal(symbol.getName());
  }

  private SoyValue getFieldInternal(String key) {
    SoyValueProvider provider = providerMap.get(key);
    return (provider != null) ? provider.resolve() : null;
  }

  @Override
  public SoyValueProvider getFieldProvider(RecordProperty symbol) {
    typeTracker.maybeSetLegacyObjectMapOrRecordType();
    return providerMap.get(symbol.getName());
  }

  @Override
  public ImmutableMap<String, SoyValueProvider> recordAsMap() {
    typeTracker.maybeSetLegacyObjectMapOrRecordType();
    return ImmutableMap.copyOf(providerMap);
  }

  @Override
  public void forEach(BiConsumer<RecordProperty, ? super SoyValueProvider> action) {
    typeTracker.maybeSetLegacyObjectMapOrRecordType();
    providerMap.forEach((key, value) -> action.accept(RecordProperty.get(key), value));
  }

  @Override
  public int recordSize() {
    typeTracker.maybeSetLegacyObjectMapOrRecordType();
    return providerMap.size();
  }

  @Override
  public int getItemCnt() {
    typeTracker.maybeSetLegacyObjectMapOrRecordType();
    return providerMap.size();
  }

  @Override
  public int size() {
    typeTracker.maybeSetMapType();
    return providerMap.size();
  }

  @Override
  @Nonnull
  public Iterable<? extends SoyValue> getItemKeys() {
    typeTracker.maybeSetLegacyObjectMapOrRecordType();
    return Iterables.transform(providerMap.keySet(), StringData::forValue);
  }

  @Nonnull
  @Override
  public Iterable<? extends SoyValue> keys() {
    typeTracker.maybeSetMapType();
    return Iterables.transform(providerMap.keySet(), StringData::forValue);
  }

  @Override
  public boolean hasItem(SoyValue key) {
    typeTracker.maybeSetLegacyObjectMapOrRecordType();
    return providerMap.containsKey(getStringKey(key));
  }

  @Override
  public boolean containsKey(SoyValue key) {
    typeTracker.maybeSetMapType();
    return providerMap.containsKey(getStringKey(key));
  }

  @Override
  public SoyValue getItem(SoyValue key) {
    typeTracker.maybeSetLegacyObjectMapOrRecordType();
    return getFieldInternal(getStringKey(key));
  }

  @Override
  public SoyValue get(SoyValue key) {
    typeTracker.maybeSetMapType();
    return getFieldInternal(getStringKey(key));
  }

  @Override
  public SoyValueProvider getItemProvider(SoyValue key) {
    typeTracker.maybeSetLegacyObjectMapOrRecordType();
    return providerMap.get(getStringKey(key));
  }

  @Override
  public SoyValueProvider getProvider(SoyValue key) {
    typeTracker.maybeSetMapType();
    return providerMap.get(getStringKey(key));
  }

  @Override
  @Nonnull
  public Map<String, ? extends SoyValueProvider> asJavaStringMap() {
    return Collections.unmodifiableMap(providerMap);
  }

  @Override
  @Nonnull
  public Map<String, ? extends SoyValue> asResolvedJavaStringMap() {
    return Maps.transformValues(asJavaStringMap(), SoyValueProvider::resolve);
  }

  @Nonnull
  @Override
  public Map<? extends SoyValue, ? extends SoyValueProvider> asJavaMap() {
    ImmutableMap.Builder<SoyValue, SoyValueProvider> builder = ImmutableMap.builder();
    for (Map.Entry<String, ? extends SoyValueProvider> entry : providerMap.entrySet()) {
      builder.put(StringData.forValue(entry.getKey()), entry.getValue());
    }
    return builder.buildOrThrow();
  }

  private String getStringKey(SoyValue key) {
    try {
      return key.stringValue();
    } catch (SoyDataException e) {
      throw new SoyDataException(
          "SoyDict accessed with non-string key (got key type " + key.getSoyTypeName() + ").", e);
    }
  }

  @Override
  public void render(LoggingAdvisingAppendable appendable) throws IOException {
    appendable.append('{');

    boolean isFirst = true;
    boolean useNewSoyMap = typeTracker.type() == RuntimeMapTypeTracker.Type.MAP;
    for (SoyValue key : (useNewSoyMap ? keys() : getItemKeys())) {
      SoyValue value = useNewSoyMap ? get(key) : getItem(key);
      if (isFirst) {
        isFirst = false;
      } else {
        appendable.append(", ");
      }
      key.render(appendable);
      appendable.append(": ");
      // TODO: Remove this once we box record values as SoyValueProvider
      if (value == null) {
        appendable.append("null");
      } else {
        value.render(appendable);
      }
    }
    appendable.append('}');
  }

  public RuntimeMapTypeTracker.Type getMapType() {
    return typeTracker.type();
  }

  @Override
  public String getSoyTypeName() {
    return "dict";
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public boolean equals(Object other) {
    return other == this;
  }
}
