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

import static com.google.template.soy.invocationbuilders.javatypes.CodeGenUtils.CHECK_NOT_NULL;

import com.google.common.base.Strings;

/** Abstract base class representing a Java type used for invocation builders. */
public abstract class JavaType {

  private final boolean isNullable;

  JavaType(boolean isNullable) {
    this.isNullable = isNullable;
  }

  /** Returns the type as a generated Java string. */
  public abstract String toJavaTypeString();

  /**
   * Given a variable name, append any runtime checks / operations for this type. Returns the
   * variable name that should be used for the result. The default behavior is to check that the
   * object is non-null (for non-primitive types) and return the original variable name. Subtypes
   * should override this if any additional runtime operations need to be done for better
   * type-safety.
   *
   * <p>For example:
   *
   * <p>For variable "myList," if the type is List<? extends Number> but we wanted the values to be
   * Long (and used "? extends Number" b/c of autoboxing), then we might append:
   *
   * <p>"{@code ImmutableList<Long>} myListAsLong =
   * myList.stream().map(Long::valueOf).collect(toImmutableList())".
   *
   * <p>The return value would be "myListAsLong".
   */
  public String asInlineCast(String variableName) {
    if (!isNullable() && !isPrimitive()) {
      return CHECK_NOT_NULL + "(" + variableName + ")";
    }
    return variableName;
  }

  /** Whether the type should be treated as {@code @Nullable}). */
  public boolean isNullable() {
    return isNullable;
  }

  /** Whether this is a primitive type. */
  abstract boolean isPrimitive();

  /** Returns this type as a nullable type. Primitive should make sure to switch to a boxed type. */
  public abstract JavaType asNullable();

  /**
   * Returns the string to use if this is a type argument for a generic type (e.g. "? extends
   * Number" instead of "Number", so we can accept Long/Double).
   *
   * <p>Note that this is NOT public because it should only be used by other java types (e.g.
   * ListJavaType needs to get its element type as a generic type string to know what to put in the
   * <>).
   */
  abstract String asGenericsTypeArgumentString();

  public boolean isGenericsTypeSupported() {
    return !Strings.isNullOrEmpty(asGenericsTypeArgumentString());
  }
}
