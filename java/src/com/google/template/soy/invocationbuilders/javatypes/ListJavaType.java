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

import com.google.template.soy.base.internal.IndentedLinesBuilder;

/** Represents a list type for generated Soy Java invocation builders. */
public final class ListJavaType extends JavaType {

  private final JavaType elementType; // The type of the list's elements.

  public ListJavaType(JavaType elementType) {
    this(elementType, /* isNullable= */ false);
  }

  public ListJavaType(JavaType elementType, boolean isNullable) {
    super(isNullable);
    this.elementType = elementType;
  }

  @Override
  boolean isPrimitive() {
    return false;
  }

  @Override
  public String toJavaTypeString() {
    return "java.util.List<" + elementType.asGenericsTypeArgumentString() + ">";
  }

  @Override
  String asGenericsTypeArgumentString() {
    return "? extends " + toJavaTypeString();
  }

  @Override
  public String appendRunTimeOperations(IndentedLinesBuilder ilb, String variableName) {
    String name = super.appendRunTimeOperations(ilb, variableName);
    if (elementType instanceof JavaNumberSubtype) {
      // Convert Iterable<? extends Number> to ImmutableList<Long> or ImmutableList<Double>.
      JavaNumberSubtype elementNumberType = (JavaNumberSubtype) elementType;
      ilb.appendLine(
          "ImmutableList<"
              + elementNumberType.getBoxedTypeNameString()
              + "> stronglyTypedList ="
              + " Streams.stream("
              + name
              + ").map((num) -> "
              + elementNumberType.transformNumberTypeToStricterSubtype("num")
              + ").collect(toImmutableList());");
      return "stronglyTypedList";
    }

    // Otherwise just do ImmutableList.copyOf();
    return "ImmutableList.copyOf(" + name + ")";
  }

  JavaType getElementType() {
    return elementType;
  }

  @Override
  public ListJavaType asNullable() {
    return new ListJavaType(elementType, true);
  }
}
