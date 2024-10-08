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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.template.soy.data.SoyValueUnconverter.unconvert;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.common.html.types.SafeHtml;
import com.google.common.html.types.SafeHtmlProto;
import com.google.common.html.types.SafeHtmls;
import com.google.common.html.types.SafeUrl;
import com.google.common.html.types.SafeUrlProto;
import com.google.common.html.types.SafeUrls;
import com.google.common.html.types.TrustedResourceUrl;
import com.google.common.html.types.TrustedResourceUrlProto;
import com.google.common.html.types.TrustedResourceUrls;
import com.google.errorprone.annotations.Keep;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContents;
import com.google.template.soy.data.SoyIterable;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.SoyValueUnconverter;
import com.google.template.soy.data.internal.IterableImpl;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.plugin.java.SharedExternRuntime;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Runtime methods exclusive to Soy extern compilation. */
public final class JbcSrcExternRuntime {

  private JbcSrcExternRuntime() {}

  private static MethodRef create(String methodName, Class<?>... params) {
    return MethodRef.createPure(JbcSrcExternRuntime.class, methodName, params);
  }

  public static final MethodRef CONVERT_OBJECT_TO_SOY_VALUE =
      create("convertObjectToSoyValue", Object.class);

  public static final MethodRef CONVERT_OBJECT_TO_SOY_VALUE_PROVIDER =
      create("convertObjectToSoyValueProvider", Object.class);

  @Keep
  @Nonnull
  public static SoyValue convertObjectToSoyValue(Object o) {
    return SoyValueConverter.INSTANCE.convert(o).resolve();
  }

  @Keep
  @Nonnull
  public static SoyValueProvider convertObjectToSoyValueProvider(Object o) {
    return SoyValueConverter.INSTANCE.convert(o);
  }

  public static final MethodRef CONVERT_SAFE_HTML_PROTO_TO_SOY_VALUE_PROVIDER =
      MethodRef.createPure(SanitizedContents.class, "fromSafeHtmlProto", SafeHtmlProto.class);
  public static final MethodRef CONVERT_SAFE_HTML_TO_SOY_VALUE_PROVIDER =
      MethodRef.createPure(SanitizedContents.class, "fromSafeHtml", SafeHtml.class);
  public static final MethodRef CONVERT_SAFE_URL_PROTO_TO_SOY_VALUE_PROVIDER =
      MethodRef.createPure(SanitizedContents.class, "fromSafeUrlProto", SafeUrlProto.class);
  public static final MethodRef CONVERT_SAFE_URL_TO_SOY_VALUE_PROVIDER =
      MethodRef.createPure(SanitizedContents.class, "fromSafeUrl", SafeUrl.class);
  public static final MethodRef CONVERT_TRUSTED_RESOURCE_URL_PROTO_TO_SOY_VALUE_PROVIDER =
      MethodRef.createPure(
          SanitizedContents.class, "fromTrustedResourceUrlProto", TrustedResourceUrlProto.class);
  public static final MethodRef CONVERT_TRUSTED_RESOURCE_URL_TO_SOY_VALUE_PROVIDER =
      MethodRef.createPure(
          SanitizedContents.class, "fromTrustedResourceUrl", TrustedResourceUrl.class);

  public static final MethodRef LIST_BOX_VALUES = create("listBoxValues", Iterable.class);

  @Keep
  @Nullable
  public static List<SoyValueProvider> listBoxValues(Iterable<?> javaValues) {
    if (javaValues == null) {
      return null;
    }
    return Streams.stream(javaValues).map(SoyValueConverter.INSTANCE::convert).collect(toList());
  }

  public static final MethodRef ITERABLE_BOX_VALUES = create("iterableBoxValues", Iterable.class);

  @Keep
  @Nullable
  public static SoyIterable iterableBoxValues(Iterable<?> javaValues) {
    if (javaValues == null) {
      return null;
    }
    return IterableImpl.forJavaIterable(javaValues, SoyValueConverter.INSTANCE::convert);
  }

  public static final MethodRef LIST_UNBOX_BOOLS = create("listUnboxBools", List.class);

  @Keep
  @Nullable
  public static ImmutableList<Boolean> listUnboxBools(List<SoyValue> values) {
    if (values == null) {
      return null;
    }
    return values.stream().map(SoyValue::coerceToBoolean).collect(toImmutableList());
  }

  public static final MethodRef LIST_UNBOX_ENUMS =
      create("listUnboxEnums", List.class, Class.class);

  @Keep
  @Nullable
  public static <T extends ProtocolMessageEnum> ImmutableList<T> listUnboxEnums(
      List<SoyValue> values, Class<T> type) {
    if (values == null) {
      return null;
    }
    return values.stream()
        .map(v -> getEnumValue(type, (int) v.longValue()))
        .collect(toImmutableList());
  }

  public static final MethodRef LIST_UNBOX_FLOATS = create("listUnboxFloats", List.class);

  @Keep
  @Nullable
  public static ImmutableList<Double> listUnboxFloats(List<SoyValue> values) {
    if (values == null) {
      return null;
    }
    return values.stream().map(SoyValue::floatValue).collect(toImmutableList());
  }

  public static final MethodRef LIST_UNBOX_INTS = create("listUnboxInts", List.class);

  @Keep
  @Nullable
  public static ImmutableList<Long> listUnboxInts(List<SoyValue> values) {
    if (values == null) {
      return null;
    }
    return values.stream().map(SoyValue::longValue).collect(toImmutableList());
  }

  public static final MethodRef LIST_UNBOX_NUMBERS = create("listUnboxNumbers", List.class);

  @Keep
  @Nullable
  public static ImmutableList<Double> listUnboxNumbers(List<SoyValue> values) {
    if (values == null) {
      return null;
    }
    return values.stream().map(SoyValue::numberValue).collect(toImmutableList());
  }

  public static final MethodRef LIST_UNBOX_PROTOS = create("listUnboxProtos", List.class);

  @Keep
  @Nullable
  public static ImmutableList<Message> listUnboxProtos(List<SoyValue> values) {
    if (values == null) {
      return null;
    }
    return values.stream().map(SoyValue::getProto).collect(toImmutableList());
  }

  public static final MethodRef LIST_UNBOX_STRINGS = create("listUnboxStrings", List.class);

  @Keep
  @Nullable
  public static ImmutableList<String> listUnboxStrings(List<SoyValue> values) {
    if (values == null) {
      return null;
    }
    return values.stream().map(SoyValue::coerceToString).collect(toImmutableList());
  }

  public static final MethodRef LONG_TO_INT = create("longToInt", long.class);

  @Keep
  public static int longToInt(long value) {
    Preconditions.checkState(
        value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE,
        "Casting long to integer results in overflow: %s",
        value);
    return (int) value;
  }

  public static final MethodRef MARK_AS_SOY_MAP =
      MethodRef.createNonPure(SoyValueConverter.class, "markAsSoyMap", Map.class);

  public static final MethodRef NO_EXTERN_JAVA_IMPL = create("noExternJavaImpl");

  @Nonnull
  public static NoSuchMethodException noExternJavaImpl() {
    return new NoSuchMethodException("No Java implementation for extern.");
  }

  public static final MethodRef SOY_VALUE_TO_BOXED_BOOLEAN =
      create("toBoxedBoolean", SoyValue.class);

  @Keep
  @Nullable
  public static Boolean toBoxedBoolean(SoyValue value) {
    if (value.isNullish()) {
      return null;
    }
    return value.coerceToBoolean();
  }

  public static final MethodRef SOY_VALUE_TO_BOXED_DOUBLE = create("toBoxedDouble", SoyValue.class);

  @Keep
  @Nullable
  public static Double toBoxedDouble(SoyValue value) {
    if (value.isNullish()) {
      return null;
    } else if (value instanceof NumberData) {
      return value.numberValue();
    }
    // This is probably an error, in which case this call with throw an appropriate exception.
    return value.floatValue();
  }

  public static final MethodRef SOY_VALUE_TO_BOXED_FLOAT = create("toBoxedFloat", SoyValue.class);

  @Keep
  @Nullable
  public static Float toBoxedFloat(SoyValue value) {
    if (value.isNullish()) {
      return null;
    } else if (value instanceof NumberData) {
      return (float) value.numberValue();
    }
    // This is probably an error, in which case this call with throw an appropriate exception.
    return (float) value.floatValue();
  }

  public static final MethodRef SOY_VALUE_TO_BOXED_INTEGER =
      create("toBoxedInteger", SoyValue.class);

  @Keep
  @Nullable
  public static Integer toBoxedInteger(SoyValue value) {
    if (value.isNullish()) {
      return null;
    }
    return value.integerValue();
  }

  public static final MethodRef SOY_VALUE_TO_BOXED_LONG = create("toBoxedLong", SoyValue.class);

  @Keep
  @Nullable
  public static Long toBoxedLong(SoyValue value) {
    if (value.isNullish()) {
      return null;
    }
    return value.longValue();
  }

  public static final MethodRef SOY_VALUE_TO_ENUM = create("toEnum", SoyValue.class, Class.class);

  @Keep
  @Nullable
  public static <T> T toEnum(SoyValue value, Class<T> clazz) {
    if (value.isNullish()) {
      return null;
    }
    return getEnumValue(clazz, value.integerValue());
  }

  public static final MethodRef UNBOX_BOOLEAN = MethodRef.createPure(Boolean.class, "booleanValue");
  public static final MethodRef UNBOX_DOUBLE = MethodRef.createPure(Double.class, "doubleValue");
  public static final MethodRef UNBOX_FLOAT = MethodRef.createPure(Float.class, "doubleValue");
  public static final MethodRef UNBOX_INTEGER = MethodRef.createPure(Integer.class, "longValue");
  public static final MethodRef UNBOX_LONG = MethodRef.createPure(Long.class, "longValue");

  public static final MethodRef UNBOX_MAP =
      create("unboxMap", SoyValue.class, Class.class, Class.class);

  @Keep
  @Nullable
  public static ImmutableMap<?, ?> unboxMap(SoyValue value, Class<?> keyType, Class<?> valueType) {
    if (value.isNullish()) {
      return null;
    }
    SoyMap map = (SoyMap) value;
    return map.entrySet().stream()
        .collect(
            toImmutableMap(
                e -> unboxMapItem(e.getKey(), keyType),
                e -> unboxMapItem(e.getValue().resolve(), valueType)));
  }

  public static final MethodRef UNBOX_OBJECT =
      MethodRef.createPure(SoyValueUnconverter.class, "unconvert", SoyValueProvider.class);

  public static final MethodRef RECORD_TO_MAP =
      MethodRef.createPure(SharedExternRuntime.class, "recordToMap", SoyValue.class);
  public static final MethodRef RECORD_TO_IMMUTABLE_MAP =
      MethodRef.createPure(SharedExternRuntime.class, "recordToImmutableMap", SoyValue.class);

  public static final MethodRef DEEP_UNBOX_LIST = create("unboxList", List.class);

  @Keep
  @Nullable
  public static ImmutableList<?> unboxList(List<?> value) {
    if (value == null) {
      return null;
    }
    return value.stream()
        .map(
            v -> {
              if (v instanceof List) {
                return unboxList((List<?>) v);
              } else if (v instanceof SoyValueProvider) {
                return unconvert((SoyValueProvider) v);
              } else {
                return v;
              }
            })
        .collect(toImmutableList());
  }

  public static final MethodRef UNBOX_SAFE_HTML = create("unboxSafeHtml", SoyValue.class);

  @Keep
  @Nullable
  public static SafeHtml unboxSafeHtml(SoyValue value) {
    if (value.isNullish()) {
      return null;
    }
    return ((SanitizedContent) value).toSafeHtml();
  }

  public static final MethodRef UNBOX_SAFE_HTML_PROTO =
      create("unboxSafeHtmlProto", SoyValue.class);

  @Keep
  @Nullable
  public static SafeHtmlProto unboxSafeHtmlProto(SoyValue value) {
    if (value.isNullish()) {
      return null;
    }
    return SafeHtmls.toProto(((SanitizedContent) value).toSafeHtml());
  }

  public static final MethodRef UNBOX_SAFE_URL = create("unboxSafeUrl", SoyValue.class);

  @Keep
  @Nullable
  public static SafeUrl unboxSafeUrl(SoyValue value) {
    if (value.isNullish()) {
      return null;
    }
    return ((SanitizedContent) value).toSafeUrl();
  }

  public static final MethodRef UNBOX_SAFE_URL_PROTO = create("unboxSafeUrlProto", SoyValue.class);

  @Keep
  @Nullable
  public static SafeUrlProto unboxSafeUrlProto(SoyValue value) {
    if (value.isNullish()) {
      return null;
    }
    return SafeUrls.toProto(((SanitizedContent) value).toSafeUrl());
  }

  public static final MethodRef UNBOX_TRUSTED_RESOURCE_URL =
      create("unboxTrustedResourceUrl", SoyValue.class);

  @Keep
  @Nullable
  public static TrustedResourceUrl unboxTrustedResourceUrl(SoyValue value) {
    if (value.isNullish()) {
      return null;
    }
    return ((SanitizedContent) value).toTrustedResourceUrl();
  }

  public static final MethodRef UNBOX_TRUSTED_RESOURCE_URL_PROTO =
      create("unboxTrustedResourceUrlProto", SoyValue.class);

  @Keep
  @Nullable
  public static TrustedResourceUrlProto unboxTrustedResourceUrlProto(SoyValue value) {
    if (value.isNullish()) {
      return null;
    }
    return TrustedResourceUrls.toProto(((SanitizedContent) value).toTrustedResourceUrl());
  }

  private static Object unboxMapItem(SoyValue value, Class<?> type) {
    if (value == null || value.isNullish()) {
      return null;
    } else if (type == Long.class) {
      return value.longValue();
    } else if (type == String.class) {
      return value.coerceToString();
    } else if (type == Boolean.class) {
      return value.coerceToBoolean();
    } else if (type == Double.class) {
      return value.floatValue();
    } else if (Message.class.isAssignableFrom(type)) {
      return value.getProto();
    } else if (ProtocolMessageEnum.class.isAssignableFrom(type)) {
      return getEnumValue(type, value.integerValue());
    } else {
      return unconvert(value);
    }
  }

  private static <T> T getEnumValue(Class<T> clazz, int enumValue) {
    try {
      Method forNumber = clazz.getMethod("forNumber", int.class);
      return clazz.cast(forNumber.invoke(null, enumValue));
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
