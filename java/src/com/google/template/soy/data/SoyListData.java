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

package com.google.template.soy.data;

import com.google.common.collect.Lists;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.CollectionData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * A list data node in a Soy data tree.
 *
 * @deprecated Users of this class should use normal {@code java.util.List}s instead. The Soy
 *     rendering APIs can automatically handle conversion of native Java types and Soy plugin users
 *     can directly use {@link SoyValueConverter#convert(Object)}. This class offers no benefits
 *     over those APIs.
 */
@Deprecated
public final class SoyListData extends SoyList implements Iterable<SoyValue>, CollectionData {
  /** The underlying list. */
  private final List<SoyValue> list;

  public SoyListData() {
    list = Lists.newArrayList();
  }

  /**
   * Constructor that initializes this SoyListData from an existing list.
   *
   * @param data The initial data in an existing Iterable.
   */
  public SoyListData(Iterable<?> data) {
    this();
    add(data);
  }

  /**
   * Constructor that initializes this SoyListData with the given elements.
   *
   * @param values The initial data to add.
   */
  public SoyListData(Object... values) {
    this(Arrays.asList(values));
  }

  /** Returns a view of this SoyListData object as a List. */
  public List<SoyValue> asList() {
    return Collections.unmodifiableList(list);
  }

  /**
   * Gets the length of this list.
   *
   * @return The length of this list.
   */
  @Override
  public int length() {
    return list.size();
  }

  @Override
  public Iterator<SoyValue> iterator() {
    return Collections.unmodifiableList(list).iterator();
  }

  // ------------ add() ------------

  /**
   * Private helper shared by constructor SoyListData(Iterable) and add(Object...).
   *
   * @param data The data to add.
   */
  private void add(Iterable<?> data) {

    for (Object el : data) {
      try {
        add(CollectionData.createFromExistingData(el));

      } catch (SoyDataException sde) {
        sde.prependIndexToDataPath(list.size());
        throw sde;
      }
    }
  }

  /**
   * Convenience function to add multiple values in one call.
   *
   * @param values The data to add.
   */
  public void add(Object... values) {
    add(Arrays.asList(values));
  }

  /**
   * Adds a data value.
   *
   * @param value The data to add.
   */
  public void add(SoyValue value) {
    list.add(CollectionData.ensureValidValue(value));
  }

  /**
   * Adds a data value.
   *
   * @param value The data to add.
   */
  public void add(boolean value) {
    add(BooleanData.forValue(value));
  }

  /**
   * Adds a data value.
   *
   * @param value The data to add.
   */
  public void add(int value) {
    add(IntegerData.forValue(value));
  }

  /**
   * Adds a data value.
   *
   * @param value The data to add.
   */
  public void add(long value) {
    add(IntegerData.forValue(value));
  }

  /**
   * Adds a data value.
   *
   * @param value The data to add.
   */
  public void add(double value) {
    add(FloatData.forValue(value));
  }

  /**
   * Adds a data value.
   *
   * @param value The data to add.
   */
  public void add(String value) {
    add(StringData.forValue(value));
  }

  // ------------ set() ------------

  /**
   * Sets a data value at a given index.
   *
   * @param index The index.
   * @param value The data to set.
   */
  public void set(int index, SoyValue value) {
    if (index == list.size()) {
      list.add(CollectionData.ensureValidValue(value));
    } else {
      list.set(index, CollectionData.ensureValidValue(value));
    }
  }

  /**
   * Sets a data value at a given index.
   *
   * @param index The index.
   * @param value The data to set.
   */
  public void set(int index, boolean value) {
    set(index, BooleanData.forValue(value));
  }

  /**
   * Sets a data value at a given index.
   *
   * @param index The index.
   * @param value The data to set.
   */
  public void set(int index, int value) {
    set(index, IntegerData.forValue(value));
  }

  /**
   * Sets a data value at a given index.
   *
   * @param index The index.
   * @param value The data to set.
   */
  public void set(int index, double value) {
    set(index, FloatData.forValue(value));
  }

  /**
   * Sets a data value at a given index.
   *
   * @param index The index.
   * @param value The data to set.
   */
  public void set(int index, String value) {
    set(index, StringData.forValue(value));
  }

  // ------------ remove() ------------

  /**
   * Removes the data value at a given index.
   *
   * @param index The index.
   */
  public void remove(int index) {
    list.remove(index);
  }

  // ------------ get*() ------------

  /**
   * Gets the data value at a given index.
   *
   * @param index The index.
   * @return The data at the given index, or null of the index is undefined.
   */
  @Override
  public SoyValue get(int index) {
    try {
      return list.get(index);
    } catch (IndexOutOfBoundsException ioobe) {
      return null;
    }
  }

  /**
   * Precondition: The specified index contains a SoyMapData object. Gets the SoyMapData at the
   * given index.
   *
   * @param index The index.
   * @return The SoyMapData at the given index, or null of the index is undefined.
   */
  public SoyMapData getMapData(int index) {
    return (SoyMapData) get(index);
  }

  /**
   * Precondition: The specified index contains a SoyListData object. Gets the SoyListData at the
   * given index.
   *
   * @param index The index.
   * @return The SoyListData at the given index, or null of the index is undefined.
   */
  public SoyListData getListData(int index) {
    return (SoyListData) get(index);
  }

  /**
   * Precondition: The specified index contains a boolean. Gets the boolean at the given index.
   *
   * @param index The index.
   * @return The boolean at the given index, or null of the index is undefined.
   */
  public boolean getBoolean(int index) {
    return get(index).booleanValue();
  }

  /**
   * Precondition: The specified index contains an integer. Gets the integer at the given index.
   *
   * @param index The index.
   * @return The integer at the given index, or null of the index is undefined.
   */
  public int getInteger(int index) {
    return get(index).integerValue();
  }

  /**
   * Precondition: The specified index contains a long. Gets the long at the given index.
   *
   * @param index The index.
   * @return The long at the given index, or null of the index is undefined.
   */
  public long getLong(int index) {
    return get(index).longValue();
  }

  /**
   * Precondition: The specified index contains a float. Gets the float at the given index.
   *
   * @param index The index.
   * @return The float at the given index, or null of the index is undefined.
   */
  public double getFloat(int index) {
    return get(index).floatValue();
  }

  /**
   * Precondition: The specified index contains a string. Gets the string at the given index.
   *
   * @param index The index.
   * @return The string at the given index, or null of the index is undefined.
   */
  public String getString(int index) {
    return get(index).stringValue();
  }

  // -----------------------------------------------------------------------------------------------
  // Superpackage-private methods.

  /**
   * Puts data into this data object at the specified key.
   *
   * @param key An individual key.
   * @param value The data to put at the specified key.
   */
  @Override
  public void putSingle(String key, SoyValue value) {
    set(Integer.parseInt(key), value);
  }

  /**
   * Removes the data at the specified key.
   *
   * @param key An individual key.
   */
  @Override
  public void removeSingle(String key) {
    remove(Integer.parseInt(key));
  }

  /**
   * Gets the data at the specified key.
   *
   * @param key An individual key.
   * @return The data at the specified key, or null if the key is not defined.
   */
  @Override
  public SoyValue getSingle(String key) {
    return get(Integer.parseInt(key));
  }

  // -----------------------------------------------------------------------------------------------
  // SoyList.

  @Override
  @Nonnull
  public List<? extends SoyValueProvider> asJavaList() {
    return asList();
  }

  @Override
  @Nonnull
  public List<? extends SoyValue> asResolvedJavaList() {
    return asList();
  }

  @Override
  public SoyValueProvider getProvider(int index) {
    return get(index);
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public boolean equals(Object other) {
    return other == this;
  }
}
