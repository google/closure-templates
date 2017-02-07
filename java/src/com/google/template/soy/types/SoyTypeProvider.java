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

/**
 * Interface used to lookup information about an object type.
 *
 * <p>TODO(user): Will be removed soon. Please do not add additional implementations.
 */
public interface SoyTypeProvider {

  /**
   * Given a fully-qualified name of a type, return the {@link SoyType} that describes this type, or
   * {@code null} if this type provider does not have a definition for the requested type.
   *
   * @param typeName The fully-qualified name of the type.
   * @param typeRegistry The global type registry.
   * @return The type object, or null.
   */
  SoyType getType(String typeName, SoyTypeRegistry typeRegistry);
}
