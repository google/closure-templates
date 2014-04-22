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

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;


/**
 * A mutable list with additional methods for ease-of-use.
 *
 * <p>Important: Until this API is more stable and this note is removed, users must not define
 * classes that implement this interface.
 *
 */
@ParametersAreNonnullByDefault
public interface SoyEasyList extends SoyList, SoyMap {


  /**
   * Adds a value to the end of this SoyList.
   * @param valueProvider A provider of the value to add. Note that this is often just the value
   *     itself, since all values are also providers.
   */
  public void add(SoyValueProvider valueProvider);


  /**
   * Adds a value to the end of this SoyList.
   * @param value The value to add. If it's not a SoyValueProvider (includes SoyValue), it will be
   *     converted.
   */
  public void add(@Nullable Object value);


  /**
   * Adds a value to this SoyList at a given index.
   * @param index The index to add at.
   * @param valueProvider A provider of the value to add. Note that this is often just the value
   *     itself, since all values are also providers.
   */
  public void add(int index, SoyValueProvider valueProvider);


  /**
   * Adds a value to this SoyList at a given index.
   * @param index The index to add at.
   * @param value The value to add. If it's not a SoyValueProvider (includes SoyValue), it will be
   *     converted.
   */
  public void add(int index, @Nullable Object value);


  /**
   * Sets a value in this SoyList.
   * @param index The index to set.
   * @param valueProvider A provider of the value to set. Note that this is often just the value
   *     itself, since all values are also providers.
   */
  public void set(int index, SoyValueProvider valueProvider);


  /**
   * Sets a value in this SoyList.
   * @param index The index to set.
   * @param value The value to set. If it's not a SoyValueProvider (includes SoyValue), it will be
   *     converted.
   */
  public void set(int index, @Nullable Object value);


  /**
   * Deletes a value from this SoyList, given its index.
   * @param index The index to delete.
   */
  public void del(int index);


  /**
   * Adds values to the end of this SoyList from another list.
   * @param list A list of the values to add.
   */
  public void addAllFromList(SoyList list);


  /**
   * Adds values to the end of this SoyList from a Java iterable.
   * @param javaIterable A Java iterable of the values to add. Each value that is not a
   *     SoyValueProvider (includes SoyValue) will be converted.
   */
  public void addAllFromJavaIterable(Iterable<?> javaIterable);


  /**
   * Makes this list immutable.
   * @return This list, for convenience.
   */
  public SoyEasyList makeImmutable();

}
