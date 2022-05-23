/*
 * Copyright 2019 Google Inc.
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

package com.google.template.soy.invocationbuilders.javatypes;

/** Represents a list type for generated Soy Java invocation builders. */
public final class ListJavaType extends JavaType {

  private final JavaType elementType; // The type of the list's elements.
  private static final CodeGenUtils.Member AS_LIST = CodeGenUtils.castFunction("asList");
  private static final CodeGenUtils.Member AS_NULLABLE_LIST =
      CodeGenUtils.castFunction("asNullableList");

  public ListJavaType(JavaType elementType) {
    this(elementType, /* isNullable= */ false);
  }

  public ListJavaType(JavaType elementType, boolean isNullable) {
    super(isNullable);
    this.elementType = elementType;
  }

  @Override
  public String toJavaTypeString() {
    return "java.lang.Iterable<" + elementType.asGenericsTypeArgumentString() + ">";
  }

  @Override
  String asGenericsTypeArgumentString() {
    // For nested lists we require them to be of type Collection since that's what Soy requires and
    // we can't convert arbitrarily nested object graphs.
    return "? extends java.util.Collection<" + elementType.asGenericsTypeArgumentString() + ">";
  }

  @Override
  public String asInlineCast(String variableName, int depth) {
    return (isNullable() ? AS_NULLABLE_LIST : AS_LIST)
        + "("
        + variableName
        + ", "
        + elementType.getAsInlineCastFunction(depth)
        + ")";
  }

  JavaType getElementType() {
    return elementType;
  }

  @Override
  public ListJavaType asNullable() {
    return new ListJavaType(elementType, true);
  }
}
