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

/**
 * Type representing an object. Object types have a unique name,
 * and can have zero or more member fields.
 *
 * <p>Object types are always referred to by their fully-qualified name; That
 * is, there's no concept of packages or scopes in this type system (those
 * concepts are already factored out before the type definition reaches this
 * point.)
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
*/
public interface SoyObjectType extends SoyType {

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
   * Return the data type of the field with the given name; If there's no such
   * field, then return {@code null}.
   * @param fieldName The name of the field.
   * @return The field type, or null.
   */
  SoyType getFieldType(String fieldName);

  /**
   * Return the expression (including the leading '.' or '[') used to access
   * the value of the field, for a given output context.
   * @param fieldName Name of the field.
   * @param backend Which backend we're generating code for.
   * @return Expression used to access the field data.
   */
  String getFieldAccessor(String fieldName, SoyBackendKind backend);

  /**
   * In some cases, accessing a field requires importing a symbol into the
   * generated code (example being protobuf extension fields which require
   * importing the extension type). If this field requires an import, then this
   * method will return the string representing the symbol needed to import.
   * Otherwise, returns {@code null}.
   *
   * @param fieldName The name of the field being accessed.
   * @param backend Which backend we're generating code for.
   * @return String The import symbol.
   */
  String getFieldImport(String fieldName, SoyBackendKind backend);
}
