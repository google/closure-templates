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

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.template.soy.data.SoyAbstractMap;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.StringData;

import java.util.Collections;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Internal implementation of SoyDict in terms of a map. Do not use directly; instead,
 * use {@link SoyValueHelper#convert}.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
@ParametersAreNonnullByDefault
abstract class AbstractDict extends SoyAbstractMap implements SoyDict {

  /** Map containing each data provider. */
  protected final Map<String, ? extends SoyValueProvider> providerMap;


  /**
   * Constructs with a backer provider map.
   * <p>
   * The provider map doesn't need to be immutable, but will not be mutated from any of
   * AbstractDict's implementation methods.
   */
  AbstractDict(Map<String, ? extends SoyValueProvider> providerMap) {
    this.providerMap = providerMap;
  }


  @Override public final boolean hasField(String name) {
    return providerMap.containsKey(name);
  }


  @Override public final SoyValue getField(String name) {
    SoyValueProvider provider = providerMap.get(name);
    return (provider != null) ? provider.resolve() : null;
  }


  @Override public final SoyValueProvider getFieldProvider(String name) {
    return providerMap.get(name);
  }


  @Override public final int getItemCnt() {
    return providerMap.size();
  }


  @Override @Nonnull public final Iterable<? extends SoyValue> getItemKeys() {
    return Iterables.transform(providerMap.keySet(), new Function<String, SoyValue>() {
      @Override public SoyValue apply(String key) {
        return StringData.forValue(key);
      }
    });
  }


  @Override public final boolean hasItem(SoyValue key) {
    return providerMap.containsKey(getStringKey(key));
  }


  @Override public final SoyValue getItem(SoyValue key) {
    return getField(getStringKey(key));
  }


  @Override public final SoyValueProvider getItemProvider(SoyValue key) {
    return providerMap.get(getStringKey(key));
  }


  @Override @Nonnull public final Map<String, ? extends SoyValueProvider> asJavaStringMap() {
    return Collections.unmodifiableMap(providerMap);
  }


  @Override @Nonnull public final Map<String, ? extends SoyValue> asResolvedJavaStringMap() {
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
}
