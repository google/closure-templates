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

import com.google.common.collect.ImmutableMap;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A record containing name-to-value mappings referred to as fields. Each name is a string and each
 * value is a SoyValue (can be unresolved).
 *
 * <p>Important: Until this API is more stable and this note is removed, users must not define
 * classes that implement this interface.
 *
 */
@ParametersAreNonnullByDefault
public interface SoyRecord extends SoyValue {

  /**
   * Checks whether this SoyRecord has a field of the given name.
   *
   * @param name The field name to check.
   * @return Whether this SoyRecord has a field of the given name.
   */
  boolean hasField(String name);

  /**
   * Gets a field value of this SoyRecord.
   *
   * @param name The field name to get.
   * @return The field value for the given field name, or null if no such field name.
   */
  SoyValue getField(String name);

  /**
   * Gets a provider of a field value of this SoyRecord.
   *
   * @param name The field name to get.
   * @return A provider of the field value for the given field name, or null if no such field name.
   */
  SoyValueProvider getFieldProvider(String name);

  /** Returns a view of this object as a java map. */
  ImmutableMap<String, SoyValueProvider> recordAsMap();
}
