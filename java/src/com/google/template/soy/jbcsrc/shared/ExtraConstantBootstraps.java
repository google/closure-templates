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
package com.google.template.soy.jbcsrc.shared;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Keep;
import com.google.template.soy.data.RecordProperty;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.data.internal.SoyMapImpl;
import com.google.template.soy.data.internal.SoyRecordImpl;
import java.lang.invoke.MethodHandles;

/** Extra constant bootstrap methods. */
public final class ExtraConstantBootstraps {
  @Keep
  public static boolean constantBoolean(
      MethodHandles.Lookup lookup, String name, Class<?> type, int v) {
    return v != 0;
  }

  /**
   * Returns a unique object. Useful for associating with a callsite to uniquely identify it at
   * runtime.
   */
  @Keep
  public static Object callSiteKey(MethodHandles.Lookup lookup, String name, Class<?> type, int v) {
    return new Object();
  }

  @Keep
  public static char constantChar(MethodHandles.Lookup lookup, String name, Class<?> type, int v) {
    return (char) v;
  }

  // NOTE: we don't use the `salt` parameters, they are just used to ensure we get unique constants
  @Keep
  public static ImmutableList<SoyValue> constantSoyList(
      MethodHandles.Lookup lookup, String name, Class<?> type, int salt, Object... args) {
    return stream(args)
        .map(v -> SoyValueConverter.INSTANCE.convert(v).resolve())
        .collect(toImmutableList());
  }

  @Keep
  public static SoyMapImpl constantSoyMap(
      MethodHandles.Lookup lookup, String name, Class<?> type, int salt, Object... keyValuePairs) {
    ImmutableMap.Builder<SoyValue, SoyValue> map =
        ImmutableMap.builderWithExpectedSize(keyValuePairs.length / 2);
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      map.put(
          SoyValueConverter.INSTANCE.convert(keyValuePairs[i]).resolve(),
          SoyValueConverter.INSTANCE.convert(keyValuePairs[i + 1]).resolve());
    }
    return SoyMapImpl.forProviderMap(map.buildKeepingLast());
  }

  @Keep
  public static SoyRecordImpl constantSoyRecord(
      MethodHandles.Lookup lookup, String name, Class<?> type, int salt, Object... keyValuePairs) {
    return new SoyRecordImpl(asParams(keyValuePairs));
  }

  @Keep
  public static ParamStore constantParamStore(
      MethodHandles.Lookup lookup, String name, Class<?> type, int salt, Object... keyValuePairs) {
    return asParams(keyValuePairs);
  }

  private static ParamStore asParams(Object... keyValuePairs) {
    ParamStore params = new ParamStore(keyValuePairs.length / 2);
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      params.setField(
          RecordProperty.get((String) keyValuePairs[i]),
          SoyValueConverter.INSTANCE.convert(keyValuePairs[i + 1]).resolve());
    }
    return params.freeze();
  }

  @Keep
  public static RecordProperty symbol(MethodHandles.Lookup lookup, String name, Class<?> type) {
    return RecordProperty.get(name);
  }

  private ExtraConstantBootstraps() {}
}
