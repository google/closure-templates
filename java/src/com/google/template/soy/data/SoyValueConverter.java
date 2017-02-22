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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
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
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.api.RenderResult;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
// TODO(user): Make final after SoyValueHelper is removed
public class SoyValueConverter {

  /** Static instance of this class that does not include any custom value converters. */
  public static final SoyValueConverter UNCUSTOMIZED_INSTANCE = new SoyValueConverter();

  /** An immutable empty dict. */
  public static final SoyDict EMPTY_DICT = UNCUSTOMIZED_INSTANCE.newDict();

  /** An immutable empty list. */
  public static final SoyList EMPTY_LIST = UNCUSTOMIZED_INSTANCE.newList();

  /** List of user-provided custom value converters. */
  // Note: Using field injection instead of constructor injection because we want optional = true.
  @Inject(optional = true)
  private List<SoyCustomValueConverter> customValueConverters;

  @Inject
  SoyValueConverter() {}

  // -----------------------------------------------------------------------------------------------
  // Creating.

  /**
   * Creates a new SoyDict initialized from the given keys and values. Values are converted eagerly.
   * Recognizes dotted-name syntax: adding {@code ("foo.goo", value)} will automatically create
   * {@code ['foo': ['goo': value]]}.
   *
   * @param alternatingKeysAndValues An alternating list of keys and values.
   * @return A new SoyDict initialized from the given keys and values.
   */
  @VisibleForTesting
  public SoyDict newDict(Object... alternatingKeysAndValues) {
    Preconditions.checkArgument(alternatingKeysAndValues.length % 2 == 0);

    Map<String, Object> map = new HashMap<>();
    for (int i = 0, n = alternatingKeysAndValues.length / 2; i < n; i++) {
      String key = (String) alternatingKeysAndValues[2 * i];
      SoyValueProvider value = convert(alternatingKeysAndValues[2 * i + 1]); // convert eagerly
      insertIntoNestedMap(map, key, value);
    }
    return newDictFromMap(map);
  }

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
   * Private helper to create nested maps based off of a given string of dot-separated field names,
   * then insert the value into the innermost map.
   *
   * <p>For example, {@code insertIntoNestedMap(new HashMap<>(), "foo.bar.baz", val)} will return a
   * map of {@code {"foo": {"bar": {"baz": val}}}}.
   *
   * @param map Top-level map to insert into
   * @param dottedName One or more field names, dot-separated.
   * @param value Value to insert
   */
  private static void insertIntoNestedMap(
      Map<String, Object> map, String dottedName, SoyValueProvider value) {

    String[] names = dottedName.split("[.]");
    int n = names.length;

    String lastName = names[n - 1];

    Map<String, Object> lastMap;
    if (n == 1) {
      lastMap = map;
    } else {
      lastMap = map;
      for (int i = 0; i <= n - 2; i++) {
        Object o = lastMap.get(names[i]);
        if (o instanceof Map) {
          @SuppressWarnings("unchecked")
          Map<String, Object> m = (Map<String, Object>) o;
          lastMap = m;
        } else if (o == null) {
          Map<String, Object> newMap = new HashMap<>();
          lastMap.put(names[i], newMap);
          lastMap = newMap;
        } else {
          throw new AssertionError("should not happen");
        }
      }
    }

    lastMap.put(lastName, value);
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
   * IMPORTANT: Do not use this method. Consider it internal to Soy.
   *
   * <p>Creates a new SoyList initialized from the given values. Values are converted eagerly.
   *
   * @param items A list of values.
   * @return A new SoyEasyList initialized from the given values.
   */
  @VisibleForTesting
  public SoyList newList(Object... items) {
    ImmutableList.Builder<SoyValueProvider> builder = ImmutableList.builder();
    for (Object o : items) {
      builder.add(convert(o));
    }
    return ListImpl.forProviderList(builder.build());
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
    SoyValueProvider convertedPrimitive = convertPrimitive(obj);
    if (convertedPrimitive != null) {
      return convertedPrimitive;
    } else if (obj instanceof Map<?, ?>) {
      // TODO: Instead of hoping that the map is string-keyed, we should only enter this case if we
      // know the map is string-keyed. Otherwise, we should fall through and let the user's custom
      // converters have a chance at converting the map.
      @SuppressWarnings("unchecked")
      Map<String, ?> objCast = (Map<String, ?>) obj;
      return newDictFromMap(objCast);
    } else if (obj instanceof Collection<?> || obj instanceof FluentIterable<?>) {
      // NOTE: We don't trap Iterable itself, because many types extend from Iterable but are not
      // meant to be enumerated. (e.g. ByteString implements Iterable<Byte>)
      return newListFromIterable((Iterable<?>) obj);
    } else if (obj instanceof SoyGlobalsValue) {
      return convert(((SoyGlobalsValue) obj).getSoyGlobalValue());
    } else if (obj instanceof ByteString) {
      // Encode ByteStrings as base 64, as a safe and consistent way to send them to JS
      return StringData.forValue(BaseEncoding.base64().encode(((ByteString) obj).toByteArray()));
    } else if (obj instanceof EnumValueDescriptor) {
      // Proto enum that was obtained via reflection (e.g. from SoyProtoValue)
      return IntegerData.forValue(((EnumValueDescriptor) obj).getNumber());
    } else if (obj instanceof ProtocolMessageEnum) {
      // Proto enum that was directly passed into the template
      return IntegerData.forValue(((ProtocolMessageEnum) obj).getNumber());
    } else {
      if (customValueConverters != null) {
        for (SoyCustomValueConverter customConverter : customValueConverters) {
          SoyValueProvider result = customConverter.convert(this, obj);
          if (result != null) {
            return result;
          }
        }
      }
      throw new SoyDataException(
          "Attempting to convert unrecognized object to Soy value (object type "
              + obj.getClass().getName()
              + ").");
    }
  }

  /**
   * Returns a SoyValueProvider corresponding to a Java object, but doesn't perform any work until
   * resolve() is called.
   */
  private SoyValueProvider convertLazy(@Nullable final Object obj) {
    SoyValueProvider convertedPrimitive = convertPrimitive(obj);
    if (convertedPrimitive != null) {
      return convertedPrimitive;
    } else {
      return new SoyAbstractCachingValueProvider() {
        @Override
        protected SoyValue compute() {
          return convert(obj).resolve();
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
  private SoyValueProvider convertPrimitive(@Nullable Object obj) {
    if (obj == null) {
      return NullData.INSTANCE;
    } else if (obj instanceof SoyValueProvider) {
      return (SoyValueProvider) obj;
    } else if (obj instanceof String) {
      return StringData.forValue((String) obj);
    } else if (obj instanceof Boolean) {
      return BooleanData.forValue((Boolean) obj);
    } else if (obj instanceof Number) {
      if (obj instanceof Integer) {
        return IntegerData.forValue((Integer) obj);
      } else if (obj instanceof Long) {
        return IntegerData.forValue((Long) obj);
      } else if (obj instanceof Double) {
        return FloatData.forValue((Double) obj);
      } else if (obj instanceof Float) {
        // Automatically convert float to double.
        return FloatData.forValue((Float) obj);
      }
    } else if (obj instanceof Future<?>) {
      return new SoyFutureValueProvider(this, (Future<?>) obj);
    }

    return null;
  }
}
