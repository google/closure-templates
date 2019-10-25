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

import static com.google.template.soy.invocationbuilders.javatypes.CodeGenUtils.AS_ATTRIBUTES;
import static com.google.template.soy.invocationbuilders.javatypes.CodeGenUtils.AS_CSS;
import static com.google.template.soy.invocationbuilders.javatypes.CodeGenUtils.AS_NULLABLE_ATTRIBUTES;
import static com.google.template.soy.invocationbuilders.javatypes.CodeGenUtils.AS_NULLABLE_CSS;

/**
 * Class for simple java types (e.g. boolean, String, Number, SafeHtml) that do not need specialized
 * / complex logic. There are static constants at the top of this class for each of the types we
 * support. Callers must use these constants and cannot instantiate new instances of this class.
 *
 * <p>NOTE: For doubles and longs, see {@link PrimitiveJavaNumberType.DOUBLE} and {@link
 * PrimitiveJavaNumberType.LONG}. These are not simple types because they need special logic for
 * coercing {@code Number} types to{@code Long} or {@code Double} at runtime.
 */
public class SimpleJavaType extends JavaType {

  /**
   * Constants for all of the simple types we support. Use {@link #asNullable} to get the
   * corresponding nullable type.
   */
  public static final SimpleJavaType BOOLEAN = new BooleanJavaType(false);

  public static final SimpleJavaType HTML = new SimpleJavaType("SafeHtml", false);

  public static final SimpleJavaType JS = new SimpleJavaType("SafeScript", false);

  public static final SimpleJavaType URL = new SimpleJavaType("SafeUrl", false);

  public static final SimpleJavaType TRUSTED_RESOURCE_URL =
      new SimpleJavaType("TrustedResourceUrl", false);

  public static final SimpleJavaType STRING = new SimpleJavaType("String", false);

  public static final SimpleJavaType OBJECT = new SimpleJavaType("Object", "?", false, false);

  public static final SimpleJavaType ATTRIBUTES = new AttributesJavaType(false);

  // Don't support as list/map type for now because we don't have a way of running the toSoyValue
  // over all values.
  public static final SimpleJavaType CSS = new CssJavaType(false);

  private final String javaTypeString;
  private final String genericsTypeArgumentString;
  private final boolean isPrimitive;

  private SimpleJavaType(
      String javaTypeString,
      String genericsTypeArgumentString,
      boolean isPrimitive,
      boolean isNullable) {
    super(isNullable);
    this.javaTypeString = javaTypeString;
    this.genericsTypeArgumentString = genericsTypeArgumentString;
    this.isPrimitive = isPrimitive;
  }

  /**
   * Construct overload that =defaults to non-nullable and uses {@code javaTypeString} as the {@code
   * genericsTypeArgumentString} as well.
   */
  private SimpleJavaType(String javaTypeString, boolean isPrimitive) {
    this(javaTypeString, javaTypeString, isPrimitive, false);
  }

  @Override
  public String toJavaTypeString() {
    return javaTypeString;
  }

  @Override
  boolean isPrimitive() {
    return isPrimitive;
  }

  @Override
  String asGenericsTypeArgumentString() {
    return genericsTypeArgumentString;
  }

  @Override
  public SimpleJavaType asNullable() {
    return new SimpleJavaType(javaTypeString, genericsTypeArgumentString, isPrimitive, true);
  }

  /**
   * Special boolean subtype. Uses primitive unless the type needs to be nullable, and then we
   * switch to the boxed Boolean.
   */
  private static final class BooleanJavaType extends SimpleJavaType {
    BooleanJavaType(boolean isNullable) {
      // Use boxed boolean if the type needs to be nullable. Otherwise use primitive boolean.
      super(isNullable ? "Boolean" : "boolean", "Boolean", !isNullable, isNullable);
    }

    @Override
    public BooleanJavaType asNullable() {
      return new BooleanJavaType(true);
    }

    @Override
    public String asTypeLiteralString() {
      return asGenericsTypeArgumentString();
    }
  }

  /**
   * The Attributes type needs special runtime operations. This subclass preserves the {@link
   * JavaType#asInlineCast} override when {@link #asNullable} is called.
   */
  private static final class AttributesJavaType extends SimpleJavaType {

    AttributesJavaType(boolean isNullable) {
      super("SanitizedContent", null, false, isNullable);
    }

    @Override
    public String asInlineCast(String variableName) {
      return (isNullable() ? AS_NULLABLE_ATTRIBUTES : AS_ATTRIBUTES) + "(" + variableName + ")";
    }

    @Override
    public AttributesJavaType asNullable() {
      return new AttributesJavaType(true);
    }
  }

  private static final class CssJavaType extends SimpleJavaType {

    CssJavaType(boolean isNullable) {
      super("com.google.template.soy.data.CssParam", null, false, isNullable);
    }

    @Override
    public String asInlineCast(String variableName) {
      return (isNullable() ? AS_NULLABLE_CSS : AS_CSS) + "(" + variableName + ")";
    }

    @Override
    public CssJavaType asNullable() {
      return new CssJavaType(true);
    }

    @Override
    public boolean isTypeLiteralSupported() {
      // Not supported because there's no hook to unwrap the value.
      return false;
    }
  }
}
