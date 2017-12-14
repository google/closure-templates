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

package com.google.template.soy.data.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SoyAbstractValue;
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
 * A map containing key-to-value mappings referred to as items. Each key is a SoyValue (must be
 * already resolved) and each value is a SoyValue (can be unresolved).
 *
 * <p>This is a new implementation (compared to {@link DictImpl}) that was designed for supporting
 * proto map. There are two different Soy map types, one uses ES6 map in JS, and another uses plain
 * object.
 */
@ParametersAreNonnullByDefault
public final class SoyMapImpl extends SoyAbstractValue implements SoyMap {
  // TODO(b/69794482)): Support non-string keys in the new map implementation.
  /** Creates a SoyDict implementation for a particular underlying provider map. */
  public static SoyMapImpl forProviderMap(Map<String, ? extends SoyValueProvider> providerMap) {
    return new SoyMapImpl(providerMap);
  }

  private SoyMapImpl(Map<String, ? extends SoyValueProvider> providerMap) {
    this.providerMap = ImmutableMap.copyOf(checkNotNull(providerMap));
  }

  /** Map containing each data provider. */
  private final ImmutableMap<String, ? extends SoyValueProvider> providerMap;

  @Override
  public int getItemCnt() {
    return providerMap.size();
  }

  @Override
  @Nonnull
  public final Iterable<? extends SoyValue> getItemKeys() {
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
  public boolean hasItem(SoyValue key) {
    return providerMap.containsKey(key.stringValue());
  }

  @Override
  public SoyValue getItem(SoyValue key) {
    SoyValueProvider provider = getItemProvider(key);
    return (provider != null) ? provider.resolve() : null;
  }

  @Override
  public SoyValueProvider getItemProvider(SoyValue key) {
    return providerMap.get(key.stringValue());
  }

  @Nonnull
  public Map<String, ? extends SoyValueProvider> asJavaStringMap() {
    return Collections.unmodifiableMap(providerMap);
  }

  @Nonnull
  public Map<String, ? extends SoyValue> asResolvedJavaStringMap() {
    return Maps.transformValues(asJavaStringMap(), Transforms.RESOLVE_FUNCTION);
  }

  @Override
  public boolean coerceToBoolean() {
    return true;
  }

  @Override
  public String coerceToString() {
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
    for (SoyValue key : getItemKeys()) {
      SoyValue value = getItem(key);
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
  public boolean equals(Object other) {
    // Instance equality, to match Javascript behavior.
    return this == other;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public String toString() {
    return coerceToString();
  }
}
