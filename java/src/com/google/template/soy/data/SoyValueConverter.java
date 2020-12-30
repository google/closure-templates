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
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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

  /** Static instance of this class */
  public static final SoyValueConverter INSTANCE = new SoyValueConverter();

  private final TypeMap cheapConverterMap = new TypeMap();
  private final TypeMap expensiveConverterMap = new TypeMap();

  private SoyValueConverter() {
    cheapConverterMap.put(
        SoyValueProvider.class,
        input -> {
          throw new AssertionError("shouldn't get here.");
        });
    cheapConverterMap.put(String.class, StringData::forValue);
    cheapConverterMap.put(Boolean.class, BooleanData::forValue);
    cheapConverterMap.put(Integer.class, input -> IntegerData.forValue(input.longValue()));
    cheapConverterMap.put(Long.class, IntegerData::forValue);

    cheapConverterMap.put(Float.class, input -> FloatData.forValue(input.doubleValue()));
    cheapConverterMap.put(Double.class, FloatData::forValue);
    cheapConverterMap.put(Future.class, (f) -> new SoyFutureValueProvider(f, this::convert));
    // Proto enum that was obtained via reflection (e.g. from SoyProtoValue)
    cheapConverterMap.put(
        EnumValueDescriptor.class, input -> IntegerData.forValue(input.getNumber()));
    // Proto enum that was directly passed into the template
    cheapConverterMap.put(
        ProtocolMessageEnum.class, input -> IntegerData.forValue(input.getNumber()));
    cheapConverterMap.put(CssParam.class, SanitizedContents::fromCss);
    cheapConverterMap.put(SafeHtml.class, SanitizedContents::fromSafeHtml);
    cheapConverterMap.put(SafeHtmlProto.class, SanitizedContents::fromSafeHtmlProto);
    cheapConverterMap.put(SafeScript.class, SanitizedContents::fromSafeScript);
    cheapConverterMap.put(SafeScriptProto.class, SanitizedContents::fromSafeScriptProto);
    cheapConverterMap.put(SafeStyle.class, SanitizedContents::fromSafeStyle);
    cheapConverterMap.put(SafeStyleProto.class, SanitizedContents::fromSafeStyleProto);
    cheapConverterMap.put(SafeStyleSheet.class, SanitizedContents::fromSafeStyleSheet);
    cheapConverterMap.put(SafeStyleSheetProto.class, SanitizedContents::fromSafeStyleSheetProto);
    cheapConverterMap.put(SafeUrl.class, SanitizedContents::fromSafeUrl);
    cheapConverterMap.put(SafeUrlProto.class, SanitizedContents::fromSafeUrlProto);
    cheapConverterMap.put(TrustedResourceUrl.class, SanitizedContents::fromTrustedResourceUrl);
    cheapConverterMap.put(
        TrustedResourceUrlProto.class, SanitizedContents::fromTrustedResourceUrlProto);
    cheapConverterMap.put(Message.Builder.class, input -> SoyProtoValue.create(input.build()));
    cheapConverterMap.put(Message.class, SoyProtoValue::create);

    expensiveConverterMap.put(
        ByteString.class,
        input -> StringData.forValue(BaseEncoding.base64().encode(input.toByteArray())));
    expensiveConverterMap.putStringMap(this::newDictFromMap);
    expensiveConverterMap.put(MarkAsSoyMap.class, input -> newSoyMapFromJavaMap(input.delegate()));
    expensiveConverterMap.put(Collection.class, this::newListFromIterable);
    // NOTE: We don't convert plain Iterables, because many types extend from Iterable but are not
    // meant to be enumerated. (e.g. ByteString implements Iterable<Byte>)
    expensiveConverterMap.put(FluentIterable.class, this::newListFromIterable);
  }

  // -----------------------------------------------------------------------------------------------
  // Creating.

  /**
   * Creates a Soy dictionary from a Java string map. While this is O(n) in the map's shallow size,
   * the Java values are converted into Soy values lazily and only once.
   */
  SoyDict newDictFromMap(Map<String, ?> javaStringMap) {
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

  /**
   * Converts the object returned by the given supplier lazily.
   *
   * <p>The supplier is guaranteed to only be called once and will be immediately discarded after
   * being invoked.
   *
   * @param supplier The object to convert.
   * @return An equivalent SoyValueProvider.
   */
  public SoyValueProvider convertLazy(Supplier<?> supplier) {
    return new LazyProvider(() -> convert(supplier.get()));
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

  private static final class LazyProvider implements SoyValueProvider {
    Supplier<SoyValueProvider> delegateProvider;
    SoyValueProvider delegate;

    LazyProvider(Supplier<SoyValueProvider> delegateProvider) {
      this.delegateProvider = delegateProvider;
    }

    @Override
    public SoyValue resolve() {
      return delegate().resolve();
    }

    @Override
    public RenderResult status() {
      return delegate().status();
    }

    @Override
    public RenderResult renderAndResolve(LoggingAdvisingAppendable appendable, boolean isLast)
        throws IOException {
      return delegate().renderAndResolve(appendable, isLast);
    }

    SoyValueProvider delegate() {
      if (delegate == null) {
        delegate = delegateProvider.get();
        delegateProvider = null;
      }
      return delegate;
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
    if (obj instanceof SoyValueProvider) {
      return (SoyValueProvider) obj;
    }
    return cheapConverterMap.convert(obj);
  }

  private interface Converter<T> extends Function<T, SoyValueProvider> {}

  private static final class TypeMap {

    /**
     * Returns the converter for the given type. The lookup algorithm is:
     *
     * <ul>
     *   <li>Explicit mapping
     *   <li>Check the superclass
     *   <li>Check each interface
     * </ul>
     *
     * <p>This caches converters for types that aren't explicitly configured. These are types like
     * protos and subtypes of explicitly configured types. This caches the created converter, but
     * with a weak reference to the Class instance, so the Class can be garbage collected if this is
     * the last reference to it.
     */
    private final ClassValue<Converter<?>> converterValue =
        new ClassValue<Converter<?>>() {

          @Override
          protected Converter<?> computeValue(Class<?> clz) {
            // See if we are bootstrapping a value.
            Converter<?> c = toLoad;
            if (c != null) {
              toLoad = null;
              return c;
            }
            // Otherwise recurse through the type hierarchy.
            c = getConverterOrNull(clz.getSuperclass());
            if (c == null) {
              for (Class<?> iface : clz.getInterfaces()) {
                c = getConverterOrNull(iface);
                if (c != null) {
                  return c;
                }
              }
            }
            return c;
          }

          private Converter<?> getConverterOrNull(Class<?> clz) {
            if (clz == null) {
              return null;
            }
            return get(clz);
          }
        };

    private Converter<?> toLoad;

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
      return (Converter) converterValue.get(checkNotNull(clz));
    }

    <T> void put(Class<T> clazz, Converter<? extends T> converter) {
      // bootstrap the ClassValue by putting the converter to use in a field and then eagerly
      // fetching from the ClassValue.
      // This is a little unusual however, this put() method is called only ever from a single
      // thread at SoyValueConverter initialization time.
      toLoad = converter;
      // Fetch the value from the classValue to initialize it.
      Converter<?> loaded = converterValue.get(clazz);
      // check that we actually loaded the expected converter
      checkState(loaded == converter);
      // Check that the classValue cleared the field.
      checkState(toLoad == null);
    }

    void putStringMap(Converter<Map<String, ?>> converter) {
      put(Map.class, converter);
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
