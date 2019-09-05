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
public abstract class PrimitiveJavaNumberType extends JavaType {

  public static final PrimitiveJavaNumberType DOUBLE =
      new PrimitiveJavaNumberType("double", "Double") {
        @Override
        String transformNumberTypeToStricterSubtype(String numberRef) {
          return numberRef + ".doubleValue()";
        }
      };

  public static final PrimitiveJavaNumberType LONG =
      new PrimitiveJavaNumberType("long", "Long") {
        @Override
        String transformNumberTypeToStricterSubtype(String numberRef) {
          return numberRef + ".longValue()";
        }
      };

  private final String primitiveTypeString; // E.g. "long" or "double".
  private final String
      boxedTypeNameString; // Corresponding boxed type name (e.g. "Long" or "Double").

  private PrimitiveJavaNumberType(String primitiveTypeString, String boxedTypeNameString) {
    this.primitiveTypeString = primitiveTypeString;
    this.boxedTypeNameString = boxedTypeNameString;
  }

  @Override
  public String toJavaTypeString() {
    return primitiveTypeString;
  }

  @Override
  boolean isPrimitive() {
    return true;
  }

  @Override
  String asGenericsTypeArgumentString() {
    return "? extends Number"; // For caller ease b/c of autoboxing.
  }

  /** Returns the boxed type name corresponding to this primitive type (e.g. "Long" or "Double"); */
  String getBoxedTypeNameString() {
    return boxedTypeNameString;
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
  abstract String transformNumberTypeToStricterSubtype(String numberRef);
}
