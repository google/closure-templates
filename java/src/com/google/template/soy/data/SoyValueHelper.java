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

import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.data.internal.DictImpl;
import com.google.template.soy.data.internal.EasyDictImpl;
import com.google.template.soy.data.internal.EasyListImpl;
import com.google.template.soy.data.internal.ListImpl;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.api.RenderResult;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * IMPORTANT: This class is partially open for public use. Specifically, you may use the method
 * {@link #convert} and the static fields. But do not use the {@code new*} methods. Consider the
 * {@code new*} methods internal to Soy, since we haven't yet decided whether or not to make them
 * directly available.
 *
 * Helpers for creating or converting existing data to SoyValue. Replaces the static utilities in
 * the old class SoyData.
 *
 */
@ParametersAreNonnullByDefault
@Singleton
public final class SoyValueHelper implements SoyValueConverter {


  /** Static instance of this class that does not include any custom value converters. */
  public static final SoyValueHelper UNCUSTOMIZED_INSTANCE = new SoyValueHelper();

  /** An immutable empty dict. */
  public static final SoyDict EMPTY_DICT = DictImpl.EMPTY;

  /** An immutable empty list. */
  public static final SoyList EMPTY_LIST = UNCUSTOMIZED_INSTANCE.newEasyList().makeImmutable();


  /** List of user-provided custom value converters. */
  // Note: Using field injection instead of constructor injection because we want optional = true.
  @Inject(optional = true)
  private List<SoyCustomValueConverter> customValueConverters;


  @Inject
  public SoyValueHelper() {}


  // -----------------------------------------------------------------------------------------------
  // Creating.


  /**
   * IMPORTANT: Do not use this method. Consider it internal to Soy.
   *
   * Creates a new empty SoyEasyDict.
   * @return A new empty SoyEasyDict.
   */
  public SoyEasyDict newEasyDict() {
    return new EasyDictImpl(this);
  }


  /**
   * IMPORTANT: Do not use this method. Consider it internal to Soy.
   *
   * Creates a new SoyEasyDict initialized from the given keys and values.
   *
   * @param alternatingKeysAndValues An alternating list of keys and values.
   * @return A new SoyEasyDict initialized from the given keys and values.
   */
  public SoyEasyDict newEasyDict(Object... alternatingKeysAndValues) {
    Preconditions.checkArgument(alternatingKeysAndValues.length % 2 == 0);
    EasyDictImpl result = new EasyDictImpl(this);
    for (int i = 0, n = alternatingKeysAndValues.length / 2; i < n; i++) {
      result.set((String) alternatingKeysAndValues[2 * i], alternatingKeysAndValues[2 * i + 1]);
    }
    return result;
  }


  /**
   * IMPORTANT: Do not use this method. Consider it internal to Soy.
   *
   * Creates a new SoyEasyDict initialized from a SoyDict.
   *
   * @param dict The dict of initial items.
   * @return A new SoyEasyDict initialized from the given SoyDict.
   */
  public SoyEasyDict newEasyDictFromDict(SoyDict dict) {
    EasyDictImpl result = new EasyDictImpl(this);
    result.setItemsFromDict(dict);
    return result;
  }


  /**
   * IMPORTANT: Do not use this method. Consider it internal to Soy.
   *
   * Creates a new SoyEasyDict initialized from a Java string-keyed map.
   * @param javaStringMap The map of initial items.
   * @return A new SoyEasyDict initialized from the given Java string-keyed map.
   */
  public SoyEasyDict newEasyDictFromJavaStringMap(Map<String, ?> javaStringMap) {
    EasyDictImpl result = new EasyDictImpl(this);
    result.setFieldsFromJavaStringMap(javaStringMap);
    return result;
  }


  /**
   * Creates a Soy dictionary from a Java string map. While this is O(N) with the map's shallow
   * size, the values are converted into Soy types lazily, only once.
   *
   * @param javaStringMap The map backing the dict.
   * @return A new SoyEasyDict initialized from the given Java string-keyed map.
   */
  private SoyDict newDictFromJavaStringMap(Map<String, ?> javaStringMap) {
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
   * Creates a new empty SoyEasyList.
   * @return A new empty SoyEasyList.
   */
  public SoyEasyList newEasyList() {
    return new EasyListImpl(this);
  }


  /**
   * IMPORTANT: Do not use this method. Consider it internal to Soy.
   *
   * Creates a new SoyEasyList initialized from the given values.
   *
   * @param values A list of values.
   * @return A new SoyEasyList initialized from the given values.
   */
  public SoyEasyList newEasyList(Object... values) {
    return newEasyListFromJavaIterable(Arrays.asList(values));
  }


  /**
   * IMPORTANT: Do not use this method. Consider it internal to Soy.
   *
   * Creates a new SoyEasyList initialized from a SoyList.
   * @param list The list of initial values.
   * @return A new SoyEasyList initialized from the given SoyList.
   */
  public SoyEasyList newEasyListFromList(SoyList list) {
    EasyListImpl result = new EasyListImpl(this);
    result.addAllFromList(list);
    return result;
  }


  /**
   * IMPORTANT: Do not use this method. Consider it internal to Soy.
   *
   * Creates a new SoyEasyList initialized from a Java iterable.
   * @param javaIterable The Java iterable of initial values.
   * @return A new SoyEasyList initialized from the given Java iterable.
   */
  public SoyEasyList newEasyListFromJavaIterable(Iterable<?> javaIterable) {
    EasyListImpl result = new EasyListImpl(this);
    result.addAllFromJavaIterable(javaIterable);
    return result;
  }


  /**
   * Creates a Soy dictionary from a Java string map. While this is O(N) with the map's shallow
   * size, the values are converted into Soy types lazily, only once.
   *
   * @param javaList The list backing the result.
   * @return A new SoyEasyDict initialized from the given Java string-keyed map.
   */
  private SoyList newListFromJavaList(List<?> javaList) {
    // Create a list backed by a Java list which has eagerly converted each value into a lazy
    // value provider. Specifically, the list iteration is done eagerly so that the lazy value
    // provider can cache its value.
    ImmutableList.Builder<SoyValueProvider> builder = ImmutableList.builder();
    for (Object item : javaList) {
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
  @Override @Nonnull public SoyValueProvider convert(@Nullable Object obj) {
    SoyValueProvider convertedPrimitive = convertPrimitive(obj);

    if (convertedPrimitive != null) {
      return convertedPrimitive;
    } else if (obj instanceof Map<?, ?>) {
      // TODO: Instead of hoping that the map is string-keyed, we should only enter this case if we
      // know the map is string-keyed. Otherwise, we should fall through and let the user's custom
      // converters have a chance at converting the map.
      @SuppressWarnings("unchecked")
      Map<String, ?> objCast = (Map<String, ?>) obj;
      return newDictFromJavaStringMap(objCast);
    } else if (obj instanceof Collection<?>) {
      // NOTE: We don't trap Iterable, because many specific types extend from Iterable but are not
      // meant to be enumerated.
      if (obj instanceof List<?>) {
        return newListFromJavaList((List<?>) obj);
      } else {
        return newEasyListFromJavaIterable((Collection<?>) obj);
      }
    } else if (obj instanceof FluentIterable<?>) {
      return newEasyListFromJavaIterable((FluentIterable<?>) obj);
    } else if (obj instanceof SoyGlobalsValue) {
      return convert(((SoyGlobalsValue) obj).getSoyGlobalValue());
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
          "Attempting to convert unrecognized object to Soy value (object type " +
          obj.getClass().getName() + ").");
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
        @Override protected SoyValue compute() {
          return convert(obj).resolve();
        }

        @Override public RenderResult status() {
          return RenderResult.done();
        }
      };
    }
  }


  /**
   * Attempts to convert fast-converting primitive types.
   */
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
