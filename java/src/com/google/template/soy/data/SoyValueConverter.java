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

package com.google.template.soy.data;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.html.types.SafeHtml;
import com.google.common.html.types.SafeHtmlProto;
import com.google.common.html.types.SafeScript;
import com.google.common.html.types.SafeScriptProto;
import com.google.common.html.types.SafeStyle;
import com.google.common.html.types.SafeStyleProto;
import com.google.common.html.types.SafeStyleSheet;
import com.google.common.html.types.SafeStyleSheetProto;
import com.google.common.html.types.SafeUrl;
import com.google.common.html.types.SafeUrlProto;
import com.google.common.html.types.TrustedResourceUrl;
import com.google.common.html.types.TrustedResourceUrlProto;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.template.soy.data.internal.DictImpl;
import com.google.template.soy.data.internal.EasyListImpl;
import com.google.template.soy.data.internal.ListImpl;
import com.google.template.soy.data.internal.RuntimeMapTypeTracker;
import com.google.template.soy.data.internal.SoyMapImpl;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.api.RenderResult;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * A converter that knows how to convert all expected Java objects into SoyValues or
 * SoyValueProviders.
 *
 * <p>IMPORTANT: This class is partially open for public use. Specifically, you may use the method
 * {@link #convert} and the static fields. But do not use the {@code new*} methods. Consider the
 * {@code new*} methods internal to Soy, since we haven't yet decided whether or not to make them
 * directly available.
 *
 */
public final class SoyValueConverter {

  /** Static instance of this class that does not include any custom value converters. */
  public static final SoyValueConverter UNCUSTOMIZED_INSTANCE = new SoyValueConverter();

  public static final SoyValueConverter INSTANCE = UNCUSTOMIZED_INSTANCE;

  /** An immutable empty dict. */
  public static final SoyDict EMPTY_DICT =
      DictImpl.forProviderMap(ImmutableMap.of(), RuntimeMapTypeTracker.Type.UNKNOWN);

  /** An immutable empty list. */
  public static final SoyList EMPTY_LIST = ListImpl.forProviderList(ImmutableList.of());

  /** An immutable empty map. */
  public static final SoyMapImpl EMPTY_MAP = SoyMapImpl.forProviderMap(ImmutableMap.of());

  private final TypeMap cheapConverterMap = new TypeMap();
  private final TypeMap expensiveConverterMap = new TypeMap();

  @Inject
  SoyValueConverter() {
    cheapConverterMap.put(
        SoyValueProvider.class,
        new Converter<SoyValueProvider>() {
          @Override
          public SoyValueProvider apply(SoyValueProvider input) {
            return input;
          }
        });
    cheapConverterMap.put(
        String.class,
        new Converter<String>() {
          @Override
          public SoyValue apply(String input) {
            return StringData.forValue(input);
          }
        });
    cheapConverterMap.put(
        Boolean.class,
        new Converter<Boolean>() {
          @Override
          public SoyValue apply(Boolean input) {
            return BooleanData.forValue(input);
          }
        });
    cheapConverterMap.put(
        Integer.class,
        new Converter<Integer>() {
          @Override
          public SoyValue apply(Integer input) {
            return IntegerData.forValue(input.longValue());
          }
        });
    cheapConverterMap.put(
        Long.class,
        new Converter<Long>() {
          @Override
          public SoyValue apply(Long input) {
            return IntegerData.forValue(input.longValue());
          }
        });

    cheapConverterMap.put(
        Float.class,
        new Converter<Float>() {
          @Override
          public SoyValue apply(Float input) {
            return FloatData.forValue(input.doubleValue());
          }
        });
    cheapConverterMap.put(
        Double.class,
        new Converter<Double>() {
          @Override
          public SoyValue apply(Double input) {
            return FloatData.forValue(input.doubleValue());
          }
        });
    cheapConverterMap.put(
        Future.class,
        new Converter<Future<?>>() {
          @Override
          public SoyValueProvider apply(Future<?> input) {
            return new SoyFutureValueProvider(input);
          }
        });
    cheapConverterMap.put(
        EnumValueDescriptor.class,
        new Converter<EnumValueDescriptor>() {
          @Override
          public SoyValue apply(EnumValueDescriptor input) {
            // / Proto enum that was obtained via reflection (e.g. from SoyProtoValue)
            return IntegerData.forValue(input.getNumber());
          }
        });
    cheapConverterMap.put(
        ProtocolMessageEnum.class,
        new Converter<ProtocolMessageEnum>() {
          @Override
          public SoyValue apply(ProtocolMessageEnum input) {
            // Proto enum that was directly passed into the template
            return IntegerData.forValue(input.getNumber());
          }
        });
    cheapConverterMap.put(
        SafeHtml.class,
        new Converter<SafeHtml>() {
          @Override
          public SoyValue apply(SafeHtml obj) {
            return SanitizedContents.fromSafeHtml(obj);
          }
        });
    cheapConverterMap.put(
        SafeHtmlProto.class,
        new Converter<SafeHtmlProto>() {
          @Override
          public SoyValue apply(SafeHtmlProto obj) {
            return SanitizedContents.fromSafeHtmlProto(obj);
          }
        });
    cheapConverterMap.put(
        SafeScript.class,
        new Converter<SafeScript>() {
          @Override
          public SoyValue apply(SafeScript obj) {
            return SanitizedContents.fromSafeScript(obj);
          }
        });
    cheapConverterMap.put(
        SafeScriptProto.class,
        new Converter<SafeScriptProto>() {
          @Override
          public SoyValue apply(SafeScriptProto obj) {
            return SanitizedContents.fromSafeScriptProto(obj);
          }
        });
    cheapConverterMap.put(
        SafeStyle.class,
        new Converter<SafeStyle>() {
          @Override
          public SoyValue apply(SafeStyle obj) {
            return SanitizedContents.fromSafeStyle(obj);
          }
        });
    cheapConverterMap.put(
        SafeStyleProto.class,
        new Converter<SafeStyleProto>() {
          @Override
          public SoyValue apply(SafeStyleProto obj) {
            return SanitizedContents.fromSafeStyleProto(obj);
          }
        });
    cheapConverterMap.put(
        SafeStyleSheet.class,
        new Converter<SafeStyleSheet>() {
          @Override
          public SoyValue apply(SafeStyleSheet obj) {
            return SanitizedContents.fromSafeStyleSheet(obj);
          }
        });
    cheapConverterMap.put(
        SafeStyleSheetProto.class,
        new Converter<SafeStyleSheetProto>() {
          @Override
          public SoyValue apply(SafeStyleSheetProto obj) {
            return SanitizedContents.fromSafeStyleSheetProto(obj);
          }
        });
    cheapConverterMap.put(
        SafeUrl.class,
        new Converter<SafeUrl>() {
          @Override
          public SoyValue apply(SafeUrl obj) {
            return SanitizedContents.fromSafeUrl(obj);
          }
        });
    cheapConverterMap.put(
        SafeUrlProto.class,
        new Converter<SafeUrlProto>() {
          @Override
          public SoyValue apply(SafeUrlProto obj) {
            return SanitizedContents.fromSafeUrlProto(obj);
          }
        });
    cheapConverterMap.put(
        TrustedResourceUrl.class,
        new Converter<TrustedResourceUrl>() {
          @Override
          public SoyValue apply(TrustedResourceUrl obj) {
            return SanitizedContents.fromTrustedResourceUrl(obj);
          }
        });
    cheapConverterMap.put(
        TrustedResourceUrlProto.class,
        new Converter<TrustedResourceUrlProto>() {
          @Override
          public SoyValue apply(TrustedResourceUrlProto obj) {
            return SanitizedContents.fromTrustedResourceUrlProto(obj);
          }
        });
    cheapConverterMap.put(
        Message.Builder.class,
        new Converter<Message.Builder>() {
          @Override
          public SoyValueProvider apply(Message.Builder input) {
            return SoyProtoValue.create(input.build());
          }
        });
    cheapConverterMap.put(
        Message.class,
        new Converter<Message>() {
          @Override
          public SoyValueProvider apply(Message input) {
            return SoyProtoValue.create(input);
          }
        });

    expensiveConverterMap.put(
        ByteString.class,
        new Converter<ByteString>() {
          @Override
          public SoyValue apply(ByteString input) {
            return StringData.forValue(BaseEncoding.base64().encode(input.toByteArray()));
          }
        });
    expensiveConverterMap.put(
        SoyGlobalsValue.class,
        new Converter<SoyGlobalsValue>() {
          @Override
          public SoyValueProvider apply(SoyGlobalsValue input) {
            return convert(input.getSoyGlobalValue());
          }
        });
    expensiveConverterMap.put(
        Map.class,
        new Converter<Map<String, ?>>() {
          @Override
          public SoyValue apply(Map<String, ?> input) {
            return newDictFromMap(input);
          }
        });
    expensiveConverterMap.put(
        MarkAsSoyMap.class,
        new Converter<MarkAsSoyMap>() {
          @Override
          public SoyValue apply(MarkAsSoyMap input) {
            return newSoyMapFromJavaMap(input.delegate());
          }
        });
    expensiveConverterMap.put(
        Collection.class,
        new Converter<Collection<?>>() {
          @Override
          public SoyValue apply(Collection<?> input) {
            return newListFromIterable(input);
          }
        });
    // NOTE: We don't convert plain Iterables, because many types extend from Iterable but are not
    // meant to be enumerated. (e.g. ByteString implements Iterable<Byte>)
    expensiveConverterMap.put(
        FluentIterable.class,
        new Converter<FluentIterable<?>>() {
          @Override
          public SoyValue apply(FluentIterable<?> input) {
            return newListFromIterable(input);
          }
        });
  }

  // -----------------------------------------------------------------------------------------------
  // Creating.

  /**
   * Creates a Soy dictionary from a Java string map. While this is O(n) in the map's shallow size,
   * the Java values are converted into Soy values lazily and only once.
   */
  public SoyDict newDictFromMap(Map<String, ?> javaStringMap) {
    // Create a dictionary backed by a map which has eagerly converted each value into a lazy
    // value provider. Specifically, the map iteration is done eagerly so that the lazy value
    // provider can cache its value.
    ImmutableMap.Builder<String, SoyValueProvider> builder = ImmutableMap.builder();
    for (Map.Entry<String, ?> entry : javaStringMap.entrySet()) {
      builder.put(entry.getKey(), convertLazy(entry.getValue()));
    }
    return DictImpl.forProviderMap(
        builder.build(),
        // This Java map could represent a Soy legacy_object_map, a Soy map, or a Soy record.
        // We don't know which until one of the SoyMap, SoyLegacyObjectMap, or SoyRecord methods
        // is invoked on it.
        RuntimeMapTypeTracker.Type.UNKNOWN);
  }

  /**
   * Creates a Soy map from a Java map. While this is O(n) in the map's shallow size, the Java
   * values are converted into Soy values lazily and only once. The keys are converted eagerly.
   */
  private SoyMap newSoyMapFromJavaMap(Map<?, ?> javaMap) {
    Map<SoyValue, SoyValueProvider> map = Maps.newHashMapWithExpectedSize(javaMap.size());
    for (Map.Entry<?, ?> entry : javaMap.entrySet()) {
      map.put(convert(entry.getKey()).resolve(), convertLazy(entry.getValue()));
    }
    return SoyMapImpl.forProviderMap(map);
  }

  /**
   * Signals to the Java rendering API that the wrapped {@code java.util.Map} represents a Soy
   * {@code map}, and not a {@code legacy_object_map} or record. In particular, this allows the map
   * to contain non-string keys. See discussion in {@link DictImpl}.
   *
   * <p>If you want to use non-string keys in a map in Soy, you need to do three things:
   *
   * <ul>
   *   <li>Change the type of your map from {@code legacy_object_map} to {@code map}
   *   <li>Change the map passed in from JS from a plain JS object to an ES6 Map
   *   <li>Wrap the map passed in from Java with {@code markAsSoyMap}
   * </ul>
   */
  public static Object markAsSoyMap(Map<?, ?> delegate) {
    return new MarkAsSoyMap(delegate);
  }

  /**
   * IMPORTANT: Do not use this method. Consider it internal to Soy.
   *
   * <p>Creates a new SoyEasyList initialized from a SoyList.
   *
   * @param list The list of initial values.
   * @return A new SoyEasyList initialized from the given SoyList.
   */
  @Deprecated
  public SoyEasyList newEasyListFromList(SoyList list) {
    EasyListImpl result = new EasyListImpl();
    for (SoyValueProvider provider : list.asJavaList()) {
      result.add(provider);
    }
    return result;
  }

  /**
   * Creates a SoyList from a Java Iterable.
   *
   * <p>Values are converted into Soy types lazily and only once.
   *
   * @param items The collection of Java values
   * @return A new SoyList initialized from the given Java Collection.
   */
  private SoyList newListFromIterable(Iterable<?> items) {
    // Create a list backed by a Java list which has eagerly converted each value into a lazy
    // value provider. Specifically, the list iteration is done eagerly so that the lazy value
    // provider can cache its value.
    ImmutableList.Builder<SoyValueProvider> builder = ImmutableList.builder();
    for (Object item : items) {
      builder.add(convertLazy(item));
    }
    return ListImpl.forProviderList(builder.build());
  }

  // -----------------------------------------------------------------------------------------------
  // Converting from existing data.
  /**
   * Converts a Java object into an equivalent SoyValueProvider.
   *
   * @param obj The object to convert.
   * @return An equivalent SoyValueProvider.
   * @throws SoyDataException If the given object cannot be converted.
   */
  @Nonnull
  public SoyValueProvider convert(@Nullable Object obj) {
    SoyValueProvider convertedPrimitive = convertCheap(obj);
    if (convertedPrimitive != null) {
      return convertedPrimitive;
    }
    return convertNonPrimitive(obj);
  }

  private SoyValueProvider convertNonPrimitive(@Nullable Object obj) {
    SoyValueProvider converted = expensiveConverterMap.convert(obj);
    if (converted != null) {
      return converted;
    }

    throw new SoyDataException(
        "Attempting to convert unrecognized object to Soy value (object type "
            + obj.getClass().getName()
            + ").");
  }

  /**
   * Returns a SoyValueProvider corresponding to a Java object, but doesn't perform any work until
   * resolve() is called.
   */
  private SoyValueProvider convertLazy(@Nullable final Object obj) {
    SoyValueProvider convertedPrimitive = convertCheap(obj);
    if (convertedPrimitive != null) {
      return convertedPrimitive;
    } else {
      return new SoyAbstractCachingValueProvider() {
        @Override
        protected SoyValue compute() {
          return convertNonPrimitive(obj).resolve();
        }

        @Override
        public RenderResult status() {
          return RenderResult.done();
        }
      };
    }
  }

  /**
   * Attempts to convert fast-converting primitive types. Returns null if obj is not a recognized
   * primitive.
   */
  @Nullable
  private SoyValueProvider convertCheap(@Nullable Object obj) {
    if (obj == null) {
      return NullData.INSTANCE;
    }
    return cheapConverterMap.convert(obj);
  }

  private interface Converter<T> extends Function<T, SoyValueProvider> {}

  private static final class TypeMap {
    // An explicit marker used to record failed lookups.
    private static final Object NULL_MARKER = new Object();
    private final Map<Class<?>, Object> map = new ConcurrentHashMap<>();

    <T> SoyValueProvider convert(T o) {
      @SuppressWarnings("unchecked")
      Converter<T> converter = getConverter((Class<T>) o.getClass());
      if (converter != null) {
        return converter.apply(o);
      }
      return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    <T> Converter<T> getConverter(Class<T> clz) {
      Object o = resolveConverter(checkNotNull(clz));
      if (o == NULL_MARKER) {
        return null;
      }
      return (Converter) o;
    }

    <T> void put(Class<T> clazz, Converter<? extends T> converter) {
      checkState(map.put(clazz, checkNotNull(converter)) == null);
    }

    /**
     * Returns the converter for the given type. The lookup algorithm is:
     *
     * <ul>
     *   <li>Explicit mapping
     *   <li>Check the superclass
     *   <li>Check each interface
     * </ul>
     *
     * @param clazz the type to lookup
     * @return the registered handler, or null if none could be found
     */
    private Object resolveConverter(@Nullable Class<?> clazz) {
      if (clazz == null) {
        // recursive base case
        return NULL_MARKER;
      }
      Object c = map.get(clazz);
      if (c != null) {
        return c;
      }
      // Walk the ancestors classes and interfaces.
      c = resolveConverter(clazz.getSuperclass());
      if (c == NULL_MARKER) {
        for (Class<?> iface : clazz.getInterfaces()) {
          c = resolveConverter(iface);
          if (c != NULL_MARKER) {
            break;
          }
        }
      }
      // at this point c is either a valid converter or NULL_MARKER, store the result to speed
      // future lookups
      map.put(clazz, c);
      return c;
    }
  }

  /** See discussion at {@link #markAsSoyMap}. */
  private static final class MarkAsSoyMap {

    final Map<?, ?> delegate;

    MarkAsSoyMap(Map<?, ?> delegate) {
      this.delegate = delegate;
    }

    Map<?, ?> delegate() {
      return delegate;
    }
  }
}
