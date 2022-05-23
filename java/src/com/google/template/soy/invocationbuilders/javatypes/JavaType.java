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
  public final String asInlineCast(String variableName) {
    return asInlineCast(variableName, 0);
  }

  abstract String asInlineCast(String variableName, int depth);

  /** Whether the type should be treated as {@code @Nullable}). */
  public boolean isNullable() {
    return isNullable;
  }

  /** Returns this type as a nullable type. Primitive should make sure to switch to a boxed type. */
  public abstract JavaType asNullable();

  /**
   * Returns a string that evaluates to a Function for converting values of the Java type to
   * SoyValueProviders.
   *
   * <p>subtypes should override to supply method reference where they can
   */
  public String getAsInlineCastFunction(int depth) {
    // use the depth to generate a unique lambda name
    String lambdaName = "v" + (depth == 0 ? "" : depth);
    return lambdaName + " -> " + asInlineCast(lambdaName, depth + 1);
  }

  /*
   Returns the string to use if this is a type argument for a generic type (e.g. "? extends
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

  /**
   * Returns the token completing the expression "new TypeToken<%s>() {}", which should represent
   * this type in Java.
   */
  public String asTypeLiteralString() {
    return toJavaTypeString();
  }

  /**
   * Whether this type supports being represented as a TypeLiteral, TypeToken etc and whether this
   * field value can be provided via a Guice provider.
   *
   * <p>Union types will be excluded since unions cannot be represented in the Java generic system.
   * Additionally, any wrapped value, like CssParam, must be excluded because there's no hook in
   * Guice to unwrap the value.
   */
  public boolean isTypeLiteralSupported() {
    return true;
  }
}
