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

import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Performs the inverse operation to {@link SoyValueConverter#convert(Object)}. */
final class SoyValueUnconverter {

  private SoyValueUnconverter() {}

  static Object unconvert(SoyValueProvider provider) {
    SoyValue soyValue = provider.resolve();
    if (soyValue instanceof NullData) {
      return null;
    } else if (soyValue instanceof BooleanData) {
      return ((BooleanData) soyValue).getValue();
    } else if (soyValue instanceof IntegerData) {
      return ((IntegerData) soyValue).getValue();
    } else if (soyValue instanceof FloatData) {
      return ((FloatData) soyValue).getValue();
    } else if (soyValue instanceof StringData) {
      return ((StringData) soyValue).getValue();
    } else if (soyValue instanceof SoyList) {
      // Use ArrayList to allow nulls.
      return ((SoyList) soyValue)
          .asResolvedJavaList().stream()
              .map(SoyValueUnconverter::unconvert)
              .collect(Collectors.toList());
    } else if (soyValue instanceof SoyMap) {
      // Use LinkedHashMap to preserve ordering and allow nulls.
      Map<Object, Object> unconverted = new LinkedHashMap<>();
      ((SoyMap) soyValue)
          .asJavaMap()
          .forEach((key, value) -> unconverted.put(unconvert(key), unconvert(value)));
      return unconverted;
    } else if (soyValue instanceof SoyProtoValue) {
      return ((SoyProtoValue) soyValue).getProto();
    } else if (soyValue instanceof SoyRecord) {
      // this needs to come after checking for SoyProtoValue since SoyProtoValue implements
      // SoyRecord
      Map<String, Object> unconverted = new LinkedHashMap<>();
      ((SoyRecord) soyValue)
          .recordAsMap()
          .forEach((key, value) -> unconverted.put(key, unconvert(value)));
      return unconverted;
    } else if (soyValue instanceof SanitizedContent) {
      SanitizedContent sc = (SanitizedContent) soyValue;
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
    } else {
      throw new IllegalArgumentException(
          "Can't unconvert values of type: " + soyValue.getClass().getName());
    }
  }
}
