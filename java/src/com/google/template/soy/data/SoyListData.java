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

import com.google.common.base.Joiner;
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
import java.util.regex.Pattern;


/**
 * A list data node in a Soy data tree.
 *
 * @author Kai Huang
 */
public class SoyListData extends CollectionData implements Iterable<SoyData> {


  /** The underlying list. */
  private final List<SoyData> list;


  public SoyListData() {
    list = Lists.newArrayList();
  }


  /**
   * Constructor that initializes this SoyListData from an existing list.
   * @param data The initial data in an existing Iterable.
   */
  public SoyListData(Iterable<?> data) {
    this();
    add(data);
  }


  /**
   * Constructor that initializes this SoyListData with the given elements.
   * @param values The initial data to add.
   */
  public SoyListData(Object... values) {
    this(Arrays.asList(values));
  }


  /**
   * Important: Please treat this method as superpackage-private. Do not call this method from
   * outside the 'tofu' and 'data' packages.
   *
   * Returns a view of this SoyListData object as a List.
   */
  public List<SoyData> asList() {
    return Collections.unmodifiableList(list);
  }


  /**
   * {@inheritDoc}
   *
   * <p> This method should only be used for debugging purposes.
   */
  @Override public String toString() {
    return "[" + Joiner.on(", ").join(list) + "]";
  }


  /**
   * {@inheritDoc}
   *
   * <p> A list is always truthy.
   */
  @Override public boolean toBoolean() {
    return true;
  }


  @Override public boolean equals(Object other) {
    return this == other;  // fall back to object equality
  }


  /**
   * Gets the length of this list.
   * @return The length of this list.
   */
  public int length() {
    return list.size();
  }


  @Override public Iterator<SoyData> iterator() {
    return Collections.unmodifiableList(list).iterator();
  }


  // ------------ add() ------------


  /**
   * Private helper shared by constructor SoyListData(Iterable) and add(Object...).
   * @param data The data to add.
   */
  private void add(Iterable<?> data) {
    for (Object el : data) {
      add(SoyData.createFromExistingData(el));
    }
  }


  /**
   * Convenience function to add multiple values in one call.
   * @param values The data to add.
   */
  public void add(Object... values) {
    add(Arrays.asList(values));
  }


  /**
   * Adds a data value.
   * @param value The data to add.
   */
  public void add(SoyData value) {
    list.add(ensureValidValue(value));
  }

  /**
   * Adds a data value.
   * @param value The data to add.
   */
  public void add(boolean value) {
    add(new BooleanData(value));
  }

  /**
   * Adds a data value.
   * @param value The data to add.
   */
  public void add(int value) {
    add(new IntegerData(value));
  }

  /**
   * Adds a data value.
   * @param value The data to add.
   */
  public void add(double value) {
    add(new FloatData(value));
  }

  /**
   * Adds a data value.
   * @param value The data to add.
   */
  public void add(String value) {
    add(new StringData(value));
  }


  // ------------ set() ------------

  /**
   * Sets a data value at a given index.
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
   * @param index The index.
   * @param value The data to set.
   */
  public void set(int index, boolean value) {
    set(index, new BooleanData(value));
  }

  /**
   * Sets a data value at a given index.
   * @param index The index.
   * @param value The data to set.
   */
  public void set(int index, int value) {
    set(index, new IntegerData(value));
  }

  /**
   * Sets a data value at a given index.
   * @param index The index.
   * @param value The data to set.
   */
  public void set(int index, double value) {
    set(index, new FloatData(value));
  }

  /**
   * Sets a data value at a given index.
   * @param index The index.
   * @param value The data to set.
   */
  public void set(int index, String value) {
    set(index, new StringData(value));
  }


  // ------------ remove() ------------

  /**
   * Removes the data value at a given index.
   * @param index The index.
   */
  public void remove(int index) {
    list.remove(index);
  }


  // ------------ get*() ------------

  /**
   * Gets the data value at a given index.
   * @param index The index.
   * @return The data at the given index, or null of the index is undefined.
   */
  public SoyData get(int index) {
    try {
      return list.get(index);
    } catch (IndexOutOfBoundsException ioobe) {
      return null;
    }
  }

  /**
   * Precondition: The specified index contains a SoyMapData object.
   * Gets the SoyMapData at the given index.
   * @param index The index.
   * @return The SoyMapData at the given index, or null of the index is undefined.
   */
  public SoyMapData getMapData(int index) {
    return (SoyMapData) get(index);
  }

  /**
   * Precondition: The specified index contains a SoyListData object.
   * Gets the SoyListData at the given index.
   * @param index The index.
   * @return The SoyListData at the given index, or null of the index is undefined.
   */
  public SoyListData getListData(int index) {
    return (SoyListData) get(index);
  }

  /**
   * Precondition: The specified index contains a boolean.
   * Gets the boolean at the given index.
   * @param index The index.
   * @return The boolean at the given index, or null of the index is undefined.
   */
  public boolean getBoolean(int index) {
    return get(index).booleanValue();
  }

  /**
   * Precondition: The specified index contains an integer.
   * Gets the integer at the given index.
   * @param index The index.
   * @return The integer at the given index, or null of the index is undefined.
   */
  public int getInteger(int index) {
    return get(index).integerValue();
  }

  /**
   * Precondition: The specified index contains a float.
   * Gets the float at the given index.
   * @param index The index.
   * @return The float at the given index, or null of the index is undefined.
   */
  public double getFloat(int index) {
    return get(index).floatValue();
  }

  /**
   * Precondition: The specified index contains a string.
   * Gets the string at the given index.
   * @param index The index.
   * @return The string at the given index, or null of the index is undefined.
   */
  public String getString(int index) {
    return get(index).stringValue();
  }


  // -----------------------------------------------------------------------------------------------
  // Protected/private helpers.


  /** Pattern for a valid key for SoyListData (an index). */
  private static final Pattern KEY_PATTERN = Pattern.compile("[0-9]+");


  @Override protected void checkKey(String key) throws IllegalArgumentException {
    if (!KEY_PATTERN.matcher(key).matches()) {
      throw new IllegalArgumentException("Illegal data key '" + key + "' for list data.");
    }
  }


  @Override protected void putSingle(String key, SoyData value) {
    set(Integer.parseInt(key), value);
  }


  @Override protected void removeSingle(String key) {
    remove(Integer.parseInt(key));
  }


  @Override protected SoyData getSingle(String key) {
    return get(Integer.parseInt(key));
  }

}
