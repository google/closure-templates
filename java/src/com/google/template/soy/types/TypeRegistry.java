/*
 * Copyright 2020 Google Inc.
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

package com.google.template.soy.types;

import javax.annotation.Nullable;

/** Repository of registered types. */
public interface TypeRegistry {

  /**
   * Looks up a simple type by name.
   *
   * @param typeName "string", "bool", "pkg.proto.Message", etc.
   */
  @Nullable
  SoyType getType(String typeName);

  default boolean hasType(String typeName) {
    return getType(typeName) != null;
  }

  /** Returns the sorted set of all types in this registry. */
  Iterable<String> getAllSortedTypeNames();

}
