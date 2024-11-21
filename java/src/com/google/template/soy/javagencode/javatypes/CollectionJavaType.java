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

package com.google.template.soy.javagencode.javatypes;

import com.google.template.soy.javagencode.javatypes.CodeGenUtils.Member;
import com.google.template.soy.types.SoyType.Kind;

/** Represents a collection type for generated Soy Java invocation builders. */
public final class CollectionJavaType extends JavaType {

  /** The collection subtype. */
  public enum Subtype {
    LIST(
        // For nested lists we require them to be of type Collection since that's what Soy requires
        // and we can't convert arbitrarily nested object graphs.
        "java.util.Collection",
        CodeGenUtils.castFunction("asList"),
        CodeGenUtils.castFunction("asNullableList")),
    SET(
        "java.util.Set",
        CodeGenUtils.castFunction("asSet"),
        CodeGenUtils.castFunction("asNullableSet")),
    ITERABLE(
        "java.lang.Iterable",
        CodeGenUtils.castFunction("asIterable"),
        CodeGenUtils.castFunction("asNullableIterable"));

    final String genericsType;
    final Member cast;
    final Member nullableCast;

    Subtype(String genericsType, Member cast, Member nullableCast) {
      this.genericsType = genericsType;
      this.cast = cast;
      this.nullableCast = nullableCast;
    }

    public static Subtype forSoyType(Kind kind) {
      switch (kind) {
        case ITERABLE:
          return ITERABLE;
        case LIST:
          return LIST;
        case SET:
          return SET;
        default:
          throw new IllegalArgumentException("" + kind);
      }
    }
  }

  private final Subtype subtype;
  private final JavaType elementType; // The type of the list's elements.

  public CollectionJavaType(Subtype subtype, JavaType elementType) {
    this(subtype, elementType, /* isNullable= */ false);
  }

  public CollectionJavaType(Subtype subtype, JavaType elementType, boolean isNullable) {
    super(isNullable);
    this.subtype = subtype;
    this.elementType = elementType;
  }

  @Override
  public String toJavaTypeString() {
    return "java.lang.Iterable<" + elementType.asGenericsTypeArgumentString() + ">";
  }

  @Override
  String asGenericsTypeArgumentString() {
    return "? extends "
        + subtype.genericsType
        + "<"
        + elementType.asGenericsTypeArgumentString()
        + ">";
  }

  @Override
  public String asInlineCast(String variableName, int depth) {
    return (isNullable() ? subtype.nullableCast : subtype.cast)
        + "("
        + variableName
        + ", "
        + elementType.getAsInlineCastFunction(depth)
        + ")";
  }

  public JavaType getElementType() {
    return elementType;
  }

  public Subtype getSubtype() {
    return subtype;
  }

  @Override
  public CollectionJavaType asNullable() {
    return new CollectionJavaType(subtype, elementType, true);
  }
}
