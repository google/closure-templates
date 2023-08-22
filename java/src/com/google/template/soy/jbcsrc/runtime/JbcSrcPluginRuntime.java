/*
 * Copyright 2023 Google Inc.
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

package com.google.template.soy.jbcsrc.runtime;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.errorprone.annotations.Keep;
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.internal.SoyLegacyObjectMapImpl;
import com.google.template.soy.data.internal.SoyMapImpl;
import com.google.template.soy.data.internal.SoyRecordImpl;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import java.util.Map;
import java.util.concurrent.Future;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Runtime methods exclusive to Soy plugin compilation. */
public final class JbcSrcPluginRuntime {

  private JbcSrcPluginRuntime() {}

  private static MethodRef create(String methodName, Class<?>... params) {
    return MethodRef.create(JbcSrcPluginRuntime.class, methodName, params);
  }

  public static final MethodRef CONVERT_FUTURE_TO_SOY_VALUE_PROVIDER =
      create("convertFutureToSoyValueProvider", Future.class);

  @Keep
  @Nonnull
  public static SoyValueProvider convertFutureToSoyValueProvider(Future<?> future) {
    return SoyValueConverter.INSTANCE.convert(future);
  }

  public static final MethodRef SOY_VALUE_INTEGER_VALUE =
      MethodRef.create(SoyValue.class, "integerValue").asCheap();

  public static final MethodRef NULLISH_TO_JAVA_NULL = create("nullishToJavaNull", SoyValue.class);

  @Keep
  @Nullable
  public static SoyValue nullishToJavaNull(@Nonnull SoyValue value) {
    return value.isNullish() ? null : value;
  }

  public static final MethodRef NULL_TO_JAVA_NULL = create("soyNullToJavaNull", SoyValue.class);

  @Keep
  @Nullable
  public static SoyValue soyNullToJavaNull(@Nonnull SoyValue value) {
    return value.isNull() ? null : value;
  }

  public static final MethodRef BOX_JAVA_MAP_AS_SOY_MAP = create("boxJavaMapAsSoyMap", Map.class);

  @Keep
  @Nonnull
  public static SoyMap boxJavaMapAsSoyMap(Map<?, ?> javaMap) {
    Map<SoyValue, SoyValueProvider> map = Maps.newHashMapWithExpectedSize(javaMap.size());
    for (Map.Entry<?, ?> entry : javaMap.entrySet()) {
      map.put(
          SoyValueConverter.INSTANCE.convert(entry.getKey()).resolve(),
          SoyValueConverter.INSTANCE.convert(entry.getValue()));
    }
    return SoyMapImpl.forProviderMap(map);
  }

  public static final MethodRef BOX_JAVA_MAP_AS_SOY_RECORD =
      create("boxJavaMapAsSoyRecord", Map.class);

  @Keep
  @Nonnull
  public static SoyRecord boxJavaMapAsSoyRecord(Map<String, ?> javaMap) {
    return new SoyRecordImpl(javaMapAsProviderMap(javaMap));
  }

  public static final MethodRef BOX_JAVA_MAP_AS_SOY_LEGACY_OBJECT_MAP =
      create("boxJavaMapAsSoyLegacyObjectMap", Map.class);

  @Keep
  @Nonnull
  public static SoyLegacyObjectMap boxJavaMapAsSoyLegacyObjectMap(Map<String, ?> javaMap) {
    return new SoyLegacyObjectMapImpl(javaMapAsProviderMap(javaMap));
  }

  private static ImmutableMap<String, SoyValueProvider> javaMapAsProviderMap(
      Map<String, ?> javaMap) {
    return javaMap.entrySet().stream()
        .collect(
            toImmutableMap(
                Map.Entry::getKey, e -> SoyValueConverter.INSTANCE.convert(e.getValue())));
  }
}
