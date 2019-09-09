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

/**
 * Wrapper class for representing a primitive Java number type (e.g. long or double)
 *
 * <p>Since int doesn't autobox to Long, and float doesn't autobox to Double, our param setters
 * accept things like List<? extends Number> to make composite types easier for callers to
 * construct. This class contains methods for writing generated code inside our setters to downcast
 * to a stricter type (e.g. Number -> Long).
 */
public class JavaNumberSubtype extends JavaType {
  public static final JavaNumberSubtype DOUBLE =
      new JavaNumberSubtype(NumberSubtype.DOUBLE, /* isNullable= */ false);

  public static final JavaNumberSubtype NULLABLE_DOUBLE =
      new JavaNumberSubtype(NumberSubtype.DOUBLE, /* isNullable= */ true);

  public static final JavaNumberSubtype LONG =
      new JavaNumberSubtype(NumberSubtype.LONG, /* isNullable= */ false);

  public static final JavaNumberSubtype NULLABLE_LONG =
      new JavaNumberSubtype(NumberSubtype.LONG, /* isNullable= */ true);

  private enum NumberSubtype {
    DOUBLE("double", "Double"),
    LONG("long", "Long");

    private final String primitiveTypeString;
    private final String boxedTypeString;

    NumberSubtype(String primitiveTypeString, String boxedTypeString) {
      this.primitiveTypeString = primitiveTypeString;
      this.boxedTypeString = boxedTypeString;
    }

    String toJavaTypeString(boolean isNullable) {
      return isNullable ? boxedTypeString : primitiveTypeString;
    }

    String getBoxedTypeNameString() {
      return boxedTypeString;
    }
  }

  private final NumberSubtype type;

  private final boolean isNullable;

  private JavaNumberSubtype(NumberSubtype type, boolean isNullable) {
    super(isNullable);
    this.type = type;
    this.isNullable = isNullable;
  }

  @Override
  public String toJavaTypeString() {
    return type.toJavaTypeString(isNullable);
  }

  @Override
  boolean isPrimitive() {
    return !isNullable(); // If it's not nullable, we will use the primitive.
  }

  @Override
  String asGenericsTypeArgumentString() {
    return "? extends Number"; // For caller ease b/c of autoboxing.
  }

  /** Returns the boxed type name corresponding to this primitive type (e.g. "Long" or "Double"); */
  String getBoxedTypeNameString() {
    return type.getBoxedTypeNameString();
  }

  /**
   * Converts a {@code Number} in the generated code to a stricter subtype (corresponding to this
   * primitive's boxed type, such as Long or Double).
   *
   * <p>{@param numberExpr: String} A generated code string containing a reference to a {@code
   * Number} (e.g. a variable name).
   *
   * @return An expression to use in the generated code that will coerce the {@code numberRef} to
   *     the boxed number subtype corresponding to this primitive. For example, for numberRef
   *     "myNum", this might return "myNum.doubleValue()".
   */
  String transformNumberTypeToStricterSubtype(String numberRef) {
    switch (type) {
      case DOUBLE:
        return numberRef + ".doubleValue()";
      case LONG:
        return numberRef + ".longValue()";
    }
    throw new IllegalStateException("Impossible");
  }

  @Override
  public JavaNumberSubtype asNullable() {
    return new JavaNumberSubtype(type, true);
  }
}
