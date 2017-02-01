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

import java.util.Map;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A mutable SoyDict with additional methods for ease-of-use.
 *
 * <p>In map usage, the item keys are the record field names in the form of StringData.
 *
 * <p>Important: Do not use. Use java.util.Map instead.
 *
 */
@Deprecated
@ParametersAreNonnullByDefault
public interface SoyEasyDict extends SoyDict {

  /**
   * Sets a field of this dict.
   *
   * @param name The field name to set.
   * @param valueProvider A provider of the field value for the given field name. Note that this is
   *     often just the field value itself, since all values are also providers.
   */
  @Deprecated
  public void setField(String name, SoyValueProvider valueProvider);

  /**
   * Deletes a field of this dict.
   *
   * @param name The field name to delete.
   */
  @Deprecated
  public void delField(String name);

  /**
   * Sets fields on this dict from a Java string-keyed map.
   *
   * @param javaStringMap A Java string-keyed map of the fields to set.
   */
  @Deprecated
  public void setFieldsFromJavaStringMap(Map<String, ?> javaStringMap);

  /**
   * Sets a subfield of this dict.
   *
   * @param dottedName The dotted name to set (one or more field names, dot-separated).
   * @param value The subfield value for the given dotted name. If it's not a SoyValueProvider
   *     (includes SoyValue), it will be converted.
   */
  @Deprecated
  public void set(String dottedName, @Nullable Object value);

  /**
   * Gets a subfield value of this dict.
   *
   * @param dottedName The dotted name to get (one or more field names, dot-separated).
   * @return The subfield value for the given dotted name, or null if no such subfield.
   */
  @Deprecated
  public SoyValue get(String dottedName);
}
