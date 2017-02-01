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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.CollectionData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * A list data node in a Soy data tree.
 *
 */
public final class SoyListData extends CollectionData implements Iterable<SoyData>, SoyList {
  /** The underlying list. */
  private final List<SoyData> list;

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

  /**
   * Important: Please treat this method as superpackage-private. Do not call this method from
   * outside the 'tofu' and 'data' packages.
   *
   * <p>Returns a view of this SoyListData object as a List.
   */
  public List<SoyData> asList() {
    return Collections.unmodifiableList(list);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method should only be used for debugging purposes.
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    try {
      render(sb);
    } catch (IOException e) {
      throw new RuntimeException(e); // impossible
    }
    return sb.toString();
  }

  @Override
  public void render(Appendable appendable) throws IOException {
    appendable.append("[");
    int size = list.size();
    if (size != 0) {
      list.get(0).render(appendable);
      for (int i = 1; i < size; i++) {
        appendable.append(", ");
        list.get(i).render(appendable);
      }
    }
    appendable.append("]");
  }

  /**
   * {@inheritDoc}
   *
   * <p>A list is always truthy.
   */
  @Override
  public boolean coerceToBoolean() {
    return true;
  }

  @Override
  public String coerceToString() {
    return toString();
  }

  @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
  @Override
  public boolean equals(Object other) {
    return this == other; // fall back to object equality
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
  public Iterator<SoyData> iterator() {
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
        add(SoyData.createFromExistingData(el));

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
  public void add(SoyData value) {
    list.add(ensureValidValue(value));
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
  public void set(int index, SoyData value) {
    if (index == list.size()) {
      list.add(ensureValidValue(value));
    } else {
      list.set(index, ensureValidValue(value));
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
  public SoyData get(int index) {
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
   * Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * <p>Puts data into this data object at the specified key.
   *
   * @param key An individual key.
   * @param value The data to put at the specified key.
   */
  @Override
  public void putSingle(String key, SoyData value) {
    set(Integer.parseInt(key), value);
  }

  /**
   * Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * <p>Removes the data at the specified key.
   *
   * @param key An individual key.
   */
  @Override
  public void removeSingle(String key) {
    remove(Integer.parseInt(key));
  }

  /**
   * Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * <p>Gets the data at the specified key.
   *
   * @param key An individual key.
   * @return The data at the specified key, or null if the key is not defined.
   */
  @Override
  public SoyData getSingle(String key) {
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

  // -----------------------------------------------------------------------------------------------
  // SoyMap.

  @Override
  public int getItemCnt() {
    return length();
  }

  @Override
  @Nonnull
  public Iterable<? extends SoyValue> getItemKeys() {
    ImmutableList.Builder<IntegerData> indexesBuilder = ImmutableList.builder();
    for (int i = 0, n = length(); i < n; i++) {
      indexesBuilder.add(IntegerData.forValue(i));
    }
    return indexesBuilder.build();
  }

  @Override
  public boolean hasItem(SoyValue key) {
    int index = getIntegerIndex(key);
    return 0 <= index && index < length();
  }

  @Override
  public SoyValue getItem(SoyValue key) {
    return get(getIntegerIndex(key));
  }

  @Override
  public SoyValueProvider getItemProvider(SoyValue key) {
    return get(getIntegerIndex(key));
  }

  /**
   * Gets the integer index out of a SoyValue key, or throws SoyDataException if the key is not an
   * integer.
   *
   * @param key The SoyValue key.
   * @return The index.
   */
  private int getIntegerIndex(SoyValue key) {
    try {
      return ((IntegerData) key).integerValue();
    } catch (ClassCastException cce) {
      try {
        // TODO: Remove this old bad behavior after existing code is compliant.
        return Integer.parseInt(key.coerceToString());
      } catch (NumberFormatException nfe) {
        throw new SoyDataException(
            "SoyList accessed with non-integer key (got key type "
                + key.getClass().getName()
                + ").");
      }
    }
  }
}
