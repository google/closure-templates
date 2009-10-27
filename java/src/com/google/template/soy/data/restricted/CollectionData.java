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
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.SoyMapData;

import java.util.List;


/**
 * Abstract superclass for a node in a Soy data tree that represents a collection of data (i.e. an
 * internal node).
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 * @author Kai Huang
 */
public abstract class CollectionData extends SoyData {


  // ------------ put() ------------


  /**
   * Convenience function to put multiple mappings in one call.
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
        put((String) data[i], SoyData.createFromExistingData(data[i + 1]));
      } catch (ClassCastException cce) {
        throw new SoyDataException(
            "Attempting to add a mapping containing a non-string key (key type " +
            data[i].getClass().getSimpleName() + ").");
      }
    }
  }


  /**
   * Puts data into this data tree at the specified key string.
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @param value The data to put at the specified location.
   */
  public void put(String keyStr, SoyData value) {

    List<String> keys = split(keyStr, '.');
    int numKeys = keys.size();

    CollectionData collectionData = this;
    for (int i = 0; i <= numKeys - 2; ++i) {

      checkKeyHelper(collectionData, keys.get(i), keyStr);
      SoyData nextSoyData = collectionData.getSingle(keys.get(i));
      if (nextSoyData != null && !(nextSoyData instanceof CollectionData)) {
        throw new SoyDataException(
            "Failed to evaluate key string \"" + keyStr + "\" for put().");
      }
      CollectionData nextCollectionData = (CollectionData) nextSoyData;

      if (nextCollectionData == null) {
        // Create the SoyData object that will be bound to keys.get(i). We need to check the first
        // part of keys[i+1] to know whether to create a SoyMapData or SoyListData (checking the
        // first char is sufficient).
        nextCollectionData =
            (Character.isDigit(keys.get(i + 1).charAt(0))) ? new SoyListData() : new SoyMapData();
        collectionData.putSingle(keys.get(i), nextCollectionData);
      }
      collectionData = nextCollectionData;
    }

    checkKeyHelper(collectionData, keys.get(numKeys - 1), keyStr);
    collectionData.putSingle(keys.get(numKeys - 1), ensureValidValue(value));
  }


  /**
   * Puts data into this data tree at the specified key string.
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @param value The data to put at the specified location.
   */
  public void put(String keyStr, boolean value) {
    put(keyStr, new BooleanData(value));
  }

  /**
   * Puts data into this data tree at the specified key string.
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @param value The data to put at the specified location.
   */
  public void put(String keyStr, int value) {
    put(keyStr, new IntegerData(value));
  }

  /**
   * Puts data into this data tree at the specified key string.
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @param value The data to put at the specified location.
   */
  public void put(String keyStr, double value) {
    put(keyStr, new FloatData(value));
  }

  /**
   * Puts data into this data tree at the specified key string.
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @param value The data to put at the specified location.
   */
  public void put(String keyStr, String value) {
    put(keyStr, new StringData(value));
  }


  // ------------ remove() ------------


  /**
   * Removes the data at the specified key string.
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   */
  public void remove(String keyStr) {

    List<String> keys = split(keyStr, '.');
    int numKeys = keys.size();

    CollectionData collectionData = this;
    for (int i = 0; i <= numKeys - 2; ++i) {
      checkKeyHelper(collectionData, keys.get(i), keyStr);
      SoyData soyData = collectionData.getSingle(keys.get(i));
      if (soyData == null || !(soyData instanceof CollectionData)) {
        return;
      }
      collectionData = (CollectionData) soyData;
    }

    checkKeyHelper(collectionData, keys.get(numKeys - 1), keyStr);
    collectionData.removeSingle(keys.get(numKeys - 1));
  }


  // ------------ get*() ------------


  /**
   * Gets the data at the specified key string.
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @return The data at the specified key string, or null if there's no data at the location.
   */
  public SoyData get(String keyStr) {

    List<String> keys = split(keyStr, '.');
    int numKeys = keys.size();

    CollectionData collectionData = this;
    for (int i = 0; i <= numKeys - 2; ++i) {
      checkKeyHelper(collectionData, keys.get(i), keyStr);
      SoyData soyData = collectionData.getSingle(keys.get(i));
      if (soyData == null || !(soyData instanceof CollectionData)) {
        return null;
      }
      collectionData = (CollectionData) soyData;
    }

    checkKeyHelper(collectionData, keys.get(numKeys - 1), keyStr);
    return collectionData.getSingle(keys.get(numKeys - 1));
  }


  /**
   * Precondition: The specified key string is the path to a SoyMapData object.
   * Gets the SoyMapData at the specified key string.
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @return The SoyMapData at the specified key string, or null if no data is stored there.
   */
  public SoyMapData getMapData(String keyStr) {
    return (SoyMapData) get(keyStr);
  }

  /**
   * Precondition: The specified key string is the path to a SoyListData object.
   * Gets the SoyListData at the specified key string.
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @return The SoyListData at the specified key string, or null if no data is stored there.
   */
  public SoyListData getListData(String keyStr) {
    return (SoyListData) get(keyStr);
  }

  /**
   * Precondition: The specified key string is the path to a boolean.
   * Gets the boolean at the specified key string.
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @return The boolean at the specified key string.
   * @throws IllegalArgumentException If no data is stored at the specified key.
   */
  public boolean getBoolean(String keyStr) {
    SoyData valueData = get(keyStr);
    if (valueData == null) {
      throw new IllegalArgumentException("Missing key: " + keyStr);
    }
    return valueData.booleanValue();
  }

  /**
   * Precondition: The specified key string is the path to an integer.
   * Gets the integer at the specified key string.
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @return The integer at the specified key string.
   * @throws IllegalArgumentException If no data is stored at the specified key.
   */
  public int getInteger(String keyStr) {
    SoyData valueData = get(keyStr);
    if (valueData == null) {
      throw new IllegalArgumentException("Missing key: " + keyStr);
    }
    return valueData.integerValue();
  }

  /**
   * Precondition: The specified key string is the path to a float.
   * Gets the float at the specified key string.
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @return The float at the specified key string.
   * @throws IllegalArgumentException If no data is stored at the specified key.
   */
  public double getFloat(String keyStr) {
    SoyData valueData = get(keyStr);
    if (valueData == null) {
      throw new IllegalArgumentException("Missing key: " + keyStr);
    }
    return valueData.floatValue();
  }

  /**
   * Precondition: The specified key string is the path to a string.
   * Gets the string at the specified key string.
   * @param keyStr One or more map keys and/or list indices (separated by '.' if multiple parts).
   *     Indicates the path to the location within this data tree.
   * @return The string at the specified key string.
   * @throws IllegalArgumentException If no data is stored at the specified key.
   */
  public String getString(String keyStr) {
    SoyData valueData = get(keyStr);
    if (valueData == null) {
      throw new IllegalArgumentException("Missing key: " + keyStr);
    }
    return valueData.stringValue();
  }


  // -----------------------------------------------------------------------------------------------
  // Protected/private helpers.


  /**
   * Private helper to check that a given individual key (may be an index) is valid for the given
   * CollectionData object.
   * @param collectionData The object that the key is to be referenced in.
   * @param key The individual key to check.
   * @param fullKeyStr The full key string that the individual key is part of (used in error
   *     reporting only).
   * @throws IllegalArgumentException If the key is not valid for the CollectionData.
   */
  private static void checkKeyHelper(CollectionData collectionData, String key, String fullKeyStr)
      throws IllegalArgumentException {
    try {
      collectionData.checkKey(key);
    } catch (IllegalArgumentException iae) {
      throw new IllegalArgumentException(
          "Error while referencing \"" + fullKeyStr + "\": " + iae.getMessage());
    }
  }


  /**
   * Ensures that the given value is valid for insertion into a Soy data tree. If the value is not
   * null, then simply returns it, else returns a new NullData object.
   * @param value The value to ensure validity for.
   * @return The given value if it's not null, or a new NullData object to represent the value if
   *     it is null.
   */
  protected static SoyData ensureValidValue(SoyData value) {
    return (value != null) ? value : NullData.INSTANCE;
  }


  /**
   * Checks that the given key is valid for this data object.
   * @param key An individual key.
   * @throws IllegalArgumentException If the given key is invalid for this collection data object.
   */
  protected abstract void checkKey(String key) throws IllegalArgumentException;


  /**
   * Puts data into this data object at the specified key.
   * @param key An individual key.
   * @param value The data to put at the specified key.
   */
  protected abstract void putSingle(String key, SoyData value);


  /**
   * Removes the data at the specified key.
   * @param key An individual key.
   */
  protected abstract void removeSingle(String key);


  /**
   * Gets the data at the specified key.
   * @param key An individual key.
   * @return The data at the specified key, or null if the key is not defined.
   */
  protected abstract SoyData getSingle(String key);


  /**
   * Splits a string into tokens at the specified delimiter.
   * @param str The string to split.  Must not be null.
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
