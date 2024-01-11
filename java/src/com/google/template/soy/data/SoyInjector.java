/*
 * Copyright 2024 Google Inc.
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

import com.google.template.soy.data.internal.ParamStore;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Injector for soy {@code @inject} parameters.
 *
 * <p>All implemenetations should be idempotent, they need not be thread safe.
 *
 * <p>Several implementations are provided to adapt to/from standard soy datastructures.
 */
@FunctionalInterface
public interface SoyInjector {

  /** An empty injector with no bindings. Returns {@code null} for all queries. */
  public static final SoyInjector EMPTY = key -> null;

  /** Creates an injector with bindings provided by the given string map. */
  public static SoyInjector fromStringMap(Map<String, ?> params) {
    var ihm = new IdentityHashMap<RecordProperty, SoyValueProvider>(params.size());
    for (Map.Entry<String, ?> entry : params.entrySet()) {
      String key = entry.getKey();
      SoyValueProvider value;
      try {
        value = SoyValueConverter.INSTANCE.convert(entry.getValue());
      } catch (RuntimeException e) {
        throw new IllegalArgumentException("Unable to convert param " + key + " to a SoyValue", e);
      }
      ihm.put(RecordProperty.get(key), value);
    }
    return ihm::get;
  }

  /** Creates an injector with bindings provided by the given ParamStore. */
  public static SoyInjector fromParamStore(ParamStore params) {
    return params::getFieldProvider;
  }

  /** Creates an injector with bindings provided by the given record. */
  public static SoyInjector fromRecord(SoyRecord params) {
    return params::getFieldProvider;
  }

  /**
   * Fetches the value for the given key. Returns {@code null} if no binding exists.
   *
   * <p>This function should be idempotent.
   */
  @Nullable
  SoyValueProvider get(RecordProperty key);
}
