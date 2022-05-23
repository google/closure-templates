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

package com.google.template.soy.data;

import com.google.template.soy.data.SoyValueConverter.TypeMap;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Performs the inverse operation to {@link SoyValueConverter#convert(Object)}. */
public final class SoyValueUnconverter {

  private SoyValueUnconverter() {}

  private static final TypeMap<Object> CONVERTERS = new TypeMap<>();

  static {
    CONVERTERS.put(NullData.class, v -> null);
    CONVERTERS.put(BooleanData.class, BooleanData::getValue);
    CONVERTERS.put(IntegerData.class, IntegerData::getValue);
    CONVERTERS.put(FloatData.class, FloatData::getValue);
    CONVERTERS.put(StringData.class, StringData::getValue);
    CONVERTERS.put(
        SoyList.class,
        v ->
            v.asResolvedJavaList().stream()
                .map(SoyValueUnconverter::unconvert)
                .collect(Collectors.toList())); // Use ArrayList to allow nulls.
    CONVERTERS.put(
        SoyMap.class,
        v -> {
          // Use LinkedHashMap to preserve ordering and allow nulls.
          Map<Object, Object> unconverted = new LinkedHashMap<>();
          v.asJavaMap().forEach((key, value) -> unconverted.put(unconvert(key), unconvert(value)));
          return unconverted;
        });
    CONVERTERS.put(SoyProtoValue.class, SoyProtoValue::getProto);
    CONVERTERS.put(
        // Note that SoyProtoValue implements SoyRecord but TypeMap's lookup order handles that OK
        // since SoyProtoValue is a final class.
        SoyRecord.class,
        v -> {
          Map<String, Object> unconverted = new LinkedHashMap<>();
          v.forEach((key, value) -> unconverted.put(key, unconvert(value)));
          return unconverted;
        });
    CONVERTERS.put(
        SanitizedContent.class,
        sc -> {
          switch (sc.getContentKind()) {
            case ATTRIBUTES:
              return sc;
            case CSS:
              try {
                return sc.toSafeStyle();
              } catch (IllegalStateException e) {
                return sc.toSafeStyleSheet();
              }
            case HTML:
              return sc.toSafeHtml();
            case JS:
              return sc.toSafeScript();
            case TRUSTED_RESOURCE_URI:
              return sc.toTrustedResourceUrl();
            case URI:
              return sc.toSafeUrl();
            default:
              throw new IllegalArgumentException(sc.getContentKind().toString());
          }
        });
  }

  public static Object unconvert(SoyValueProvider provider) {
    if (provider == null) {
      return null;
    }
    SoyValue soyValue = provider.resolve();
    return unconvertInternal(soyValue);
  }

  private static <T extends SoyValue> Object unconvertInternal(T soyValue) {
    @SuppressWarnings({"unchecked"}) // Java thinks it's "? extends T"
    Class<T> valueClass = (Class<T>) soyValue.getClass();

    Function<T, Object> converter = CONVERTERS.getConverter(valueClass);
    if (converter != null) {
      return converter.apply(soyValue);
    }

    throw new IllegalArgumentException(
        "Can't unconvert values of type: " + soyValue.getClass().getName());
  }
}
