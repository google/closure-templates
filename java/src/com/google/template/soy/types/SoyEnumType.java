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

package com.google.template.soy.types;

import com.google.template.soy.base.SoyBackendKind;

import javax.annotation.Nullable;

/**
 * Type representing an enumeration. Enum types have a unique name,
 * and can have zero or more enum constant values. Enum values are always
 * integers.
 *
 * <p>Enum types are always referred to by their fully-qualified name.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
*/
public interface SoyEnumType extends SoyType {

  /**
   * Return the fully-qualified name of this object type.
   */
  String getName();

  /**
   * Return the fully-qualified name of this type for a given output context.
   * @param backend Which backend we're generating code for.
   */
  String getNameForBackend(SoyBackendKind backend);

  /**
   * Given the name of an enum member, return the integer value of that member.
   * Returns {@code null} if there is no such field.
   */
  @Nullable Integer getValue(String memberName);
}
