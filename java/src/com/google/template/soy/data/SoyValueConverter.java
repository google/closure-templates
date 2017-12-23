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
import com.google.common.io.BaseEncoding;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.template.soy.data.internal.DictImpl;
import com.google.template.soy.data.internal.EasyListImpl;
import com.google.template.soy.data.internal.ListImpl;
import com.google.template.soy.data.internal.SoyMapImpl;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.api.RenderResult;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
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

  /** Static instance of this class that does not include any custom value converters. */
  public static final SoyValueConverter UNCUSTOMIZED_INSTANCE = new SoyValueConverter();

  /** An immutable empty dict. */
  public static final SoyDict EMPTY_DICT = DictImpl.forProviderMap(ImmutableMap.of());

  /** An immutable empty list. */
  public static final SoyList EMPTY_LIST = ListImpl.forProviderList(ImmutableList.of());

  /** An immutable empty map. */
  public static final SoyMapImpl EMPTY_MAP = SoyMapImpl.forProviderMap(ImmutableMap.of());

  private final TypeMap cheapConverterMap = new TypeMap();
  private final TypeMap expensiveConverterMap = new TypeMap();

  /** List of user-provided custom value converters. */
  // Note: Using field injection instead of constructor injection because we want optional = true.
  @Inject(optional = true)
  private List<SoyCustomValueConverter> customValueConverters;

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
          public SoyValueProvider apply(String input) {
            return StringData.forValue(input);
          }
        });
    cheapConverterMap.put(
        Boolean.class,
        new Converter<Boolean>() {
          @Override
          public SoyValueProvider apply(Boolean input) {
            return BooleanData.forValue(input);
          }
        });
    cheapConverterMap.put(
        Integer.class,
        new Converter<Integer>() {
          @Override
          public SoyValueProvider apply(Integer input) {
            return IntegerData.forValue(input.longValue());
          }
        });
    cheapConverterMap.put(
        Long.class,
        new Converter<Long>() {
          @Override
          public SoyValueProvider apply(Long input) {
            return IntegerData.forValue(input.longValue());
          }
        });

    cheapConverterMap.put(
        Float.class,
        new Converter<Float>() {
          @Override
          public SoyValueProvider apply(Float input) {
            return FloatData.forValue(input.doubleValue());
          }
        });
    cheapConverterMap.put(
        Double.class,
        new Converter<Double>() {
          @Override
          public SoyValueProvider apply(Double input) {
            return FloatData.forValue(input.doubleValue());
          }
        });
    cheapConverterMap.put(
        Future.class,
        new Converter<Future<?>>() {
          @Override
          @Nullable
          public SoyValueProvider apply(@Nullable Future<?> input) {
            return new SoyFutureValueProvider(SoyValueConverter.this, input);
          }
        });
    cheapConverterMap.put(
        EnumValueDescriptor.class,
        new Converter<EnumValueDescriptor>() {
          @Override
          public SoyValueProvider apply(@Nullable EnumValueDescriptor input) {
            // / Proto enum that was obtained via reflection (e.g. from SoyProtoValue)
            return IntegerData.forValue(input.getNumber());
          }
        });
    cheapConverterMap.put(
        ProtocolMessageEnum.class,
        new Converter<ProtocolMessageEnum>() {
          @Override
          public SoyValueProvider apply(@Nullable ProtocolMessageEnum input) {
            // Proto enum that was directly passed into the template
            return IntegerData.forValue(input.getNumber());
          }
        });

    expensiveConverterMap.put(
        ByteString.class,
        new Converter<ByteString>() {
          @Override
          public SoyValueProvider apply(ByteString input) {
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
          public SoyValueProvider apply(Map<String, ?> input) {
            return newDictFromMap(input);
          }
        });
    expensiveConverterMap.put(
        Collection.class,
        new Converter<Collection<?>>() {
          @Override
          public SoyValueProvider apply(Collection<?> input) {
            return newListFromIterable(input);
          }
        });
    expensiveConverterMap.put(
        FluentIterable.class,
        new Converter<FluentIterable<?>>() {
          @Override
          public SoyValueProvider apply(FluentIterable<?> input) {
            return newListFromIterable(input);
          }
        });
  }

  // -----------------------------------------------------------------------------------------------
  // Creating.

  /**
   * Creates a Soy dictionary from a Java string map. While this is O(N) with the map's shallow
   * size, the values are converted into Soy values lazily and only once.
   *
   * @param javaStringMap The map backing the dict.
   * @return A new SoyDict initialized from the given Java string-keyed map.
   */
  public SoyDict newDictFromMap(Map<String, ?> javaStringMap) {
    // Create a dictionary backed by a map which has eagerly converted each value into a lazy
    // value provider. Specifically, the map iteration is done eagerly so that the lazy value
    // provider can cache its value.
    ImmutableMap.Builder<String, SoyValueProvider> builder = ImmutableMap.builder();
    for (Map.Entry<String, ?> entry : javaStringMap.entrySet()) {
      builder.put(entry.getKey(), convertLazy(entry.getValue()));
    }
    return DictImpl.forProviderMap(builder.build());
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

    if (customValueConverters != null) {
      for (SoyCustomValueConverter customConverter : customValueConverters) {
        converted = customConverter.convert(this, obj);
        if (converted != null) {
          return converted;
        }
      }
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

    @SuppressWarnings("unchecked")
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
}
