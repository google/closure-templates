/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.data.restricted;

import com.google.common.collect.Lists;
import com.google.template.soy.data.SoyAbstractValue;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Abstract superclass for a node in a Soy data tree that represents a collection of data (i.e. an
 * internal node).
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p>Important: Even though this class is not marked 'final', do not extend this class.
 */
public abstract class CollectionData extends SoyAbstractValue {

  /**
   * Creates SoyValue objects from standard Java data structures.
   *
   * @param obj The existing object or data structure to convert.
   * @return A SoyValue object or tree that corresponds to the given object.
   * @throws SoyDataException If the given object cannot be converted to SoyValue.
   * @deprecated It's best to pass whatever object you have directly to the Soy templates you're
   *     using -- Soy understands primitives, lists, and maps natively, and if you install runtime
   *     support you can also pass protocol buffers. If you're interacting directly with the Soy
   *     runtime and need SoyValue objects, use SoyValueConverter instead.
   */
  @Deprecated
  protected static SoyValue createFromExistingData(Object obj) {
    if (obj instanceof SoyValue) {
      return (SoyValue) obj;
    } else if (obj instanceof Map<?, ?>) {
      @SuppressWarnings("unchecked")
      Map<String, ?> objCast = (Map<String, ?>) obj;
      return new SoyMapData(objCast);
    } else if (obj instanceof Iterable<?>) {
      return new SoyListData((Iterable<?>) obj);
    } else if (obj instanceof Future<?>) {
      // Note: In the old SoyValue, we don't support late-resolution of Futures. We immediately
      // resolve the Future object here. For late-resolution, use SoyValueConverter.convert().
      try {
        return createFromExistingData(((Future<?>) obj).get());
      } catch (InterruptedException e) {
        throw new SoyDataException(
            "Encountered InterruptedException when resolving Future object.", e);
      } catch (ExecutionException e) {
        throw new SoyDataException(
            "Encountered ExecutionException when resolving Future object.", e);
      }
    } else {
      return SoyValueConverter.INSTANCE.convert(obj).resolve();
    }
  }

  // ------------ put() ------------

  /**
   * Convenience function to put multiple mappings in one call.
   *
   * @param data The mappings to put, as alternating keys/values. Indices 0, 2, 4, ... must be valid
   *     key strings. Indices 1, 3, 5, ... must be valid Soy data values.
   * @throws SoyDataException When attempting to add an invalid varargs list or a mapping containing
   *     an invalid key.
   */
  public void put(Object... data) {

    // TODO: Perhaps change to only convert varargs to Map, and do put(Map) elsewhere.
    if (data.length % 2 != 0) {
      throw new SoyDataException(
          "Varargs to put(...) must have an even number of arguments (key-value pairs).");
    }
    for (int i = 0; i < data.length; i += 2) {
      try {
        put((String) data[i], createFromExistingData(data[i + 1]));
      } catch (ClassCastException cce) {
        throw new SoyDataException(
            "Attempting to add a mapping containing a non-string key (key type "
                + data[i].getClass().getName()
                + ").");
      }
    }
  }

  /**
   * Puts data into this data tree at the specified key string.
   *
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @param value The data to put at the specified location.
   */
  public void put(String keyStr, SoyValue value) {

    List<String> keys = split(keyStr, '.');
    int numKeys = keys.size();

    CollectionData collectionData = this;
    for (int i = 0; i <= numKeys - 2; ++i) {

      SoyValue nextSoyData = collectionData.getSingle(keys.get(i));
      if (nextSoyData != null && !(nextSoyData instanceof CollectionData)) {
        throw new SoyDataException("Failed to evaluate key string \"" + keyStr + "\" for put().");
      }
      CollectionData nextCollectionData = (CollectionData) nextSoyData;

      if (nextCollectionData == null) {
        // Create the SoyValue object that will be bound to keys.get(i). We need to check the first
        // part of keys[i+1] to know whether to create a SoyMapData or SoyListData (checking the
        // first char is sufficient).
        nextCollectionData =
            (Character.isDigit(keys.get(i + 1).charAt(0))) ? new SoyListData() : new SoyMapData();
        collectionData.putSingle(keys.get(i), nextCollectionData);
      }
      collectionData = nextCollectionData;
    }

    collectionData.putSingle(keys.get(numKeys - 1), ensureValidValue(value));
  }

  /**
   * Puts data into this data tree at the specified key string.
   *
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @param value The data to put at the specified location.
   */
  public void put(String keyStr, boolean value) {
    put(keyStr, BooleanData.forValue(value));
  }

  /**
   * Puts data into this data tree at the specified key string.
   *
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @param value The data to put at the specified location.
   */
  public void put(String keyStr, int value) {
    put(keyStr, IntegerData.forValue(value));
  }

  /**
   * Puts data into this data tree at the specified key string.
   *
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @param value The data to put at the specified location.
   */
  public void put(String keyStr, long value) {
    put(keyStr, IntegerData.forValue(value));
  }

  /**
   * Puts data into this data tree at the specified key string.
   *
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @param value The data to put at the specified location.
   */
  public void put(String keyStr, double value) {
    put(keyStr, FloatData.forValue(value));
  }

  /**
   * Puts data into this data tree at the specified key string.
   *
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @param value The data to put at the specified location.
   */
  public void put(String keyStr, String value) {
    put(keyStr, StringData.forValue(value));
  }

  // ------------ remove() ------------

  /**
   * Removes the data at the specified key string.
   *
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   */
  public void remove(String keyStr) {

    List<String> keys = split(keyStr, '.');
    int numKeys = keys.size();

    CollectionData collectionData = this;
    for (int i = 0; i <= numKeys - 2; ++i) {
      SoyValue soyData = collectionData.getSingle(keys.get(i));
      if (!(soyData instanceof CollectionData)) {
        return;
      }
      collectionData = (CollectionData) soyData;
    }

    collectionData.removeSingle(keys.get(numKeys - 1));
  }

  // ------------ get*() ------------

  /**
   * Gets the data at the specified key string.
   *
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @return The data at the specified key string, or null if there's no data at the location.
   */
  public SoyValue get(String keyStr) {

    List<String> keys = split(keyStr, '.');
    int numKeys = keys.size();

    CollectionData collectionData = this;
    for (int i = 0; i <= numKeys - 2; ++i) {
      SoyValue soyData = collectionData.getSingle(keys.get(i));
      if (!(soyData instanceof CollectionData)) {
        return null;
      }
      collectionData = (CollectionData) soyData;
    }

    return collectionData.getSingle(keys.get(numKeys - 1));
  }

  /**
   * Precondition: The specified key string is the path to a SoyMapData object. Gets the SoyMapData
   * at the specified key string.
   *
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @return The SoyMapData at the specified key string, or null if no data is stored there.
   */
  public SoyMapData getMapData(String keyStr) {
    return (SoyMapData) get(keyStr);
  }

  /**
   * Precondition: The specified key string is the path to a SoyListData object. Gets the
   * SoyListData at the specified key string.
   *
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @return The SoyListData at the specified key string, or null if no data is stored there.
   */
  public SoyListData getListData(String keyStr) {
    return (SoyListData) get(keyStr);
  }

  /**
   * Precondition: The specified key string is the path to a boolean. Gets the boolean at the
   * specified key string.
   *
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @return The boolean at the specified key string.
   * @throws IllegalArgumentException If no data is stored at the specified key.
   */
  public boolean getBoolean(String keyStr) {
    SoyValue valueData = get(keyStr);
    if (valueData == null) {
      throw new IllegalArgumentException("Missing key: " + keyStr);
    }
    return valueData.booleanValue();
  }

  /**
   * Precondition: The specified key string is the path to an integer. Gets the integer at the
   * specified key string.
   *
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @return The integer at the specified key string.
   * @throws IllegalArgumentException If no data is stored at the specified key.
   */
  public int getInteger(String keyStr) {
    SoyValue valueData = get(keyStr);
    if (valueData == null) {
      throw new IllegalArgumentException("Missing key: " + keyStr);
    }
    return valueData.integerValue();
  }

  /**
   * Precondition: The specified key string is the path to a long. Gets the long at the specified
   * key string.
   *
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @return The long at the specified key string.
   * @throws IllegalArgumentException If no data is stored at the specified key.
   */
  public long getLong(String keyStr) {
    SoyValue valueData = get(keyStr);
    if (valueData == null) {
      throw new IllegalArgumentException("Missing key: " + keyStr);
    }
    return valueData.longValue();
  }

  /**
   * Precondition: The specified key string is the path to a float. Gets the float at the specified
   * key string.
   *
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @return The float at the specified key string.
   * @throws IllegalArgumentException If no data is stored at the specified key.
   */
  public double getFloat(String keyStr) {
    SoyValue valueData = get(keyStr);
    if (valueData == null) {
      throw new IllegalArgumentException("Missing key: " + keyStr);
    }
    return valueData.floatValue();
  }

  /**
   * Precondition: The specified key string is the path to a string. Gets the string at the
   * specified key string.
   *
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @return The string at the specified key string.
   * @throws IllegalArgumentException If no data is stored at the specified key.
   */
  public String getString(String keyStr) {
    SoyValue valueData = get(keyStr);
    if (valueData == null) {
      throw new IllegalArgumentException("Missing key: " + keyStr);
    }
    return valueData.stringValue();
  }

  // -----------------------------------------------------------------------------------------------
  // Superpackage-private methods.

  /**
   * Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * <p>Puts data into this data object at the specified key.
   *
   * @param key An individual key.
   * @param value The data to put at the specified key.
   */
  public abstract void putSingle(String key, SoyValue value);

  /**
   * Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * <p>Removes the data at the specified key.
   *
   * @param key An individual key.
   */
  public abstract void removeSingle(String key);

  /**
   * Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * <p>Gets the data at the specified key.
   *
   * @param key An individual key.
   * @return The data at the specified key, or null if the key is not defined.
   */
  public abstract SoyValue getSingle(String key);

  // -----------------------------------------------------------------------------------------------
  // Protected/private helpers.

  /**
   * Ensures that the given value is valid for insertion into a Soy data tree. If the value is not
   * null, then simply returns it, else return NullData.
   *
   * @param value The value to ensure validity for.
   * @return The given value if it's not null, or NullData if it is null.
   */
  protected static SoyValue ensureValidValue(SoyValue value) {
    return (value != null) ? value : NullData.INSTANCE;
  }

  /**
   * Splits a string into tokens at the specified delimiter.
   *
   * @param str The string to split. Must not be null.
   * @param delim The delimiter character.
   * @return A list of tokens. Will not return null.
   */
  private static List<String> split(String str, char delim) {

    List<String> result = Lists.newArrayList();

    int currPartStart = 0;
    while (true) {
      int currPartEnd = str.indexOf(delim, currPartStart);
      if (currPartEnd == -1) {
        result.add(str.substring(currPartStart));
        break;
      } else {
        result.add(str.substring(currPartStart, currPartEnd));
        currPartStart = currPartEnd + 1;
      }
    }

    return result;
  }
}
