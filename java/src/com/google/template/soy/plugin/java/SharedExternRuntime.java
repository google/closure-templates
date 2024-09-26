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

package com.google.template.soy.plugin.java;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Keep;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.SoyValueUnconverter;
import com.google.template.soy.data.restricted.NullData;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;

/** Support for the renderCss extern function. */
public class SharedExternRuntime {

  private SharedExternRuntime() {}

  @Keep
  @Nullable
  public static Map<String, ?> recordToMap(SoyValue value) {
    if (value.isNullish()) {
      return null;
    }
    Map<String, Object> map = new HashMap<>();
    for (Entry<String, SoyValueProvider> e : ((SoyRecord) value).recordAsMap().entrySet()) {
      map.put(e.getKey(), unconvert(e.getValue(), false));
    }
    return map;
  }

  @Keep
  @Nullable
  public static ImmutableMap<String, ?> recordToImmutableMap(SoyValue value) {
    if (value.isNullish()) {
      return null;
    }
    SoyRecord map = (SoyRecord) value;
    return map.recordAsMap().entrySet().stream()
        .collect(toImmutableMap(Entry::getKey, e -> unconvert(e.getValue(), true)));
  }

  private static Object unconvert(SoyValueProvider svp, boolean marshalNull) {
    Object value = SoyValueUnconverter.unconvert(svp.resolve());
    if (marshalNull && value == null) {
      return NullData.INSTANCE;
    }
    return value;
  }
}
