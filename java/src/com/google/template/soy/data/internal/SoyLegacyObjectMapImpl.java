/*
 * Copyright 2020 Google Inc.
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
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SoyAbstractValue;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.StringData;
import java.io.IOException;
import java.util.Map;

/**
 * A simple legacy_object_map implementation.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class SoyLegacyObjectMapImpl extends SoyAbstractValue implements SoyLegacyObjectMap {
  private final ImmutableMap<String, SoyValueProvider> map;

  public SoyLegacyObjectMapImpl(ImmutableMap<String, SoyValueProvider> map) {
    this.map = checkNotNull(map);
  }

  @Override
  public int getItemCnt() {
    return map.size();
  }

  @Override
  public Iterable<? extends SoyValue> getItemKeys() {
    return Iterables.transform(map.keySet(), StringData::forValue);
  }

  @Override
  public boolean hasItem(SoyValue key) {
    return map.containsKey(getStringKey(key));
  }

  @Override
  public SoyValue getItem(SoyValue key) {
    SoyValueProvider provider = getItemProvider(key);
    return provider != null ? provider.resolve() : null;
  }

  @Override
  public SoyValueProvider getItemProvider(SoyValue key) {
    return map.get(getStringKey(key));
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
    for (Map.Entry<String, SoyValueProvider> entry : map.entrySet()) {
      if (isFirst) {
        isFirst = false;
      } else {
        appendable.append(", ");
      }
      appendable.append(entry.getKey()).append(": ");
      entry.getValue().resolve().render(appendable);
    }
    appendable.append('}');
  }

  @Override
  public final boolean equals(Object other) {
    return this == other;
  }

  @Override
  public final int hashCode() {
    return System.identityHashCode(this);
  }

  private static String getStringKey(SoyValue key) {
    try {
      return key.stringValue();
    } catch (SoyDataException e) {
      throw new SoyDataException(
          "SoyLegacyObjectMap accessed with non-string key (got key type "
              + key.getClass().getName()
              + ").",
          e);
    }
  }
}
