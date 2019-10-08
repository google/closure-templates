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

import static com.google.template.soy.invocationbuilders.javatypes.CodeGenUtils.AS_NULLABLE_NUMBER;
import static com.google.template.soy.invocationbuilders.javatypes.CodeGenUtils.AS_NUMBER;

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

  public static final JavaNumberSubtype NUMBER =
      new JavaNumberSubtype(NumberSubtype.NUMBER, /* isNullable= */ false);

  public static final JavaNumberSubtype NULLABLE_NUMBER =
      new JavaNumberSubtype(NumberSubtype.NUMBER, /* isNullable= */ true);

  private enum NumberSubtype {
    DOUBLE("double", "Double", CodeGenUtils.AS_LIST_OF_DOUBLES, CodeGenUtils.DOUBLE_MAPPER),
    LONG("long", "Long", CodeGenUtils.AS_LIST_OF_LONGS, CodeGenUtils.LONG_MAPPER),
    NUMBER("Number", "Number", CodeGenUtils.AS_NUMBER_COLLECTION, CodeGenUtils.NUMBER_MAPPER);

    private final String primitiveTypeString;
    private final String boxedTypeString;
    /**
     * A method reference that takes a {@code List<? extends Number>} and validates that all
     * elements are of this type.
     */
    private final CodeGenUtils.Member listConverterMethod;
    /** A member reference to pass to BaseSoyTemplateImpl.AbstractBuilder#asMapOfNumbers. */
    private final CodeGenUtils.Member mapperFunction;

    NumberSubtype(
        String primitiveTypeString,
        String boxedTypeString,
        CodeGenUtils.Member listConverterMethod,
        CodeGenUtils.Member mapperFunction) {
      this.primitiveTypeString = primitiveTypeString;
      this.boxedTypeString = boxedTypeString;
      this.listConverterMethod = listConverterMethod;
      this.mapperFunction = mapperFunction;
    }

    String toJavaTypeString(boolean isNullable) {
      return isNullable ? boxedTypeString : primitiveTypeString;
    }

    CodeGenUtils.Member getListConverterMethod() {
      return listConverterMethod;
    }

    CodeGenUtils.Member getMapperFunction() {
      return mapperFunction;
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
    // If it's not nullable, we will use the primitive.
    return type != NumberSubtype.NUMBER && !isNullable();
  }

  @Override
  String asGenericsTypeArgumentString() {
    return "? extends Number"; // For caller ease b/c of autoboxing.
  }

  @Override
  public String asTypeLiteralString() {
    return type.boxedTypeString;
  }

  CodeGenUtils.Member getListConverterMethod() {
    return type.getListConverterMethod();
  }

  CodeGenUtils.Member getMapperFunction() {
    return type.getMapperFunction();
  }

  @Override
  public JavaNumberSubtype asNullable() {
    return new JavaNumberSubtype(type, true);
  }

  @Override
  public String asInlineCast(String variableName) {
    if (type == NumberSubtype.NUMBER) {
      return (isNullable() ? AS_NULLABLE_NUMBER : AS_NUMBER) + "(" + variableName + ")";
    }
    return super.asInlineCast(variableName);
  }
}
