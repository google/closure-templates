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

import static com.google.template.soy.invocationbuilders.javatypes.CodeGenUtils.castFunction;

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
  public static final SimpleJavaType BOOLEAN =
      new PrimitiveJavaType(
          /* boxedType= */ "java.lang.Boolean",
          /* primitiveType= */ "boolean",
          /* genericType=*/ "java.lang.Boolean",
          /* isNullable=*/ false,
          castFunction("asBool"),
          castFunction("asBool"),
          castFunction("asNullableBool"));

  public static final SimpleJavaType INT =
      new PrimitiveJavaType(
          /* boxedType= */ "java.lang.Long",
          /* primitiveType= */ "long",
          /* genericType=*/ "? extends java.lang.Number",
          /* isNullable=*/ false,
          castFunction("asInt"),
          castFunction("asBoxedInt"),
          castFunction("asNullableInt"));

  public static final SimpleJavaType FLOAT =
      new PrimitiveJavaType(
          /* boxedType= */ "java.lang.Double",
          /* primitiveType= */ "double",
          /* genericType=*/ "? extends java.lang.Number",
          /* isNullable=*/ false,
          castFunction("asFloat"),
          castFunction("asBoxedFloat"),
          castFunction("asNullableFloat"));

  public static final SimpleJavaType NUMBER =
      new SimpleJavaType(
          "java.lang.Number",
          "? extends java.lang.Number",
          /* isNullable=*/ false,
          castFunction("asNumber"),
          castFunction("asNullableNumber"));

  public static final SimpleJavaType HTML =
      new SimpleJavaType(
          "com.google.common.html.types.SafeHtml",
          castFunction("asHtml"),
          castFunction("asNullableHtml"));

  public static final SimpleJavaType JS =
      new SimpleJavaType(
          "com.google.common.html.types.SafeScript",
          castFunction("asJs"),
          castFunction("asNullableJs"));

  public static final SimpleJavaType URL =
      new SimpleJavaType(
          "com.google.common.html.types.SafeUrl",
          castFunction("asUri"),
          castFunction("asNullableUri"));

  public static final SimpleJavaType TRUSTED_RESOURCE_URL =
      new SimpleJavaType(
          "com.google.common.html.types.TrustedResourceUrl",
          /* asReference=*/ castFunction("asTrustedResourceUri"),
          /* asNullableReference=*/ castFunction("asNullableTrustedResourceUri"));

  public static final SimpleJavaType STRING =
      new SimpleJavaType(
          "java.lang.String",
          /* asReference=*/ castFunction("asString"),
          /* asNullableReference=*/ castFunction("asNullableString"));

  private static final CodeGenUtils.Member AS_SOY_VALUE = castFunction("asSoyValue");

  public static final SimpleJavaType OBJECT =
      new SimpleJavaType(
          "java.lang.Object", "?", /* isNullable=*/ false, AS_SOY_VALUE, AS_SOY_VALUE);

  public static final SimpleJavaType ATTRIBUTES =
      new SimpleJavaType(
          "com.google.template.soy.data.SanitizedContent",
          /* asReference=*/ castFunction("asAttributes"),
          /* asNullableReference=*/ castFunction("asNullableAttributes"));
  public static final SimpleJavaType CSS =
      new SimpleJavaType(
          "com.google.template.soy.data.CssParam",
          /* asReference=*/ castFunction("asCss"),
          /* asNullableReference=*/ castFunction("asNullableCss"));

  public static final SimpleJavaType MESSAGE =
      new SimpleJavaType(
          "com.google.protobuf.Message",
          /* asReference=*/ castFunction("asProto"),
          /* asNullableReference=*/ castFunction("asNullableProto"));

  private final String javaTypeString;
  private final String genericsTypeArgumentString;
  final CodeGenUtils.Member asReference;
  final CodeGenUtils.Member asNullableReference;

  private SimpleJavaType(
      String javaTypeString,
      CodeGenUtils.Member asReference,
      CodeGenUtils.Member asNullableReference) {
    this(javaTypeString, javaTypeString, /* isNullable=*/ false, asReference, asNullableReference);
  }

  private SimpleJavaType(
      String javaTypeString,
      String genericsTypeArgumentString,
      boolean isNullable,
      CodeGenUtils.Member asReference,
      CodeGenUtils.Member asNullableReference) {
    super(isNullable);
    this.javaTypeString = javaTypeString;
    this.genericsTypeArgumentString = genericsTypeArgumentString;
    this.asReference = asReference;
    this.asNullableReference = asNullableReference;
  }

  @Override
  public String toJavaTypeString() {
    return javaTypeString;
  }

  @Override
  String asGenericsTypeArgumentString() {
    return genericsTypeArgumentString;
  }

  @Override
  public SimpleJavaType asNullable() {
    return new SimpleJavaType(
        javaTypeString, genericsTypeArgumentString, true, asReference, asNullableReference);
  }

  @Override
  public String getAsInlineCastFunction(int depth) {
    return "AbstractBuilder::" + getMapperFunction();
  }

  CodeGenUtils.Member getMapperFunction() {
    return isNullable() ? asNullableReference : asReference;
  }

  @Override
  public String asInlineCast(String variable, int depth) {
    return getMapperFunction() + "(" + variable + ")";
  }

  /**
   * Special boolean subtype. Uses primitive unless the type needs to be nullable, and then we
   * switch to the boxed Boolean.
   */
  private static final class PrimitiveJavaType extends SimpleJavaType {
    final String primitiveType;
    final String boxedType;
    final String genericType;
    final CodeGenUtils.Member asFunctionBoxed;

    PrimitiveJavaType(
        String boxedType,
        String primitiveType,
        String genericType,
        boolean isNullable,
        CodeGenUtils.Member asFunctionUnboxed,
        CodeGenUtils.Member asFunctionBoxed,
        CodeGenUtils.Member asNullableFunction) {
      // Use the boxed type if the type needs to be nullable (or generic). Otherwise use the
      // primitive.
      super(
          isNullable ? boxedType : primitiveType,
          boxedType,
          isNullable,
          asFunctionUnboxed,
          asNullableFunction);
      this.genericType = genericType;
      this.asFunctionBoxed = asFunctionBoxed;
      this.primitiveType = primitiveType;
      this.boxedType = boxedType;
    }

    @Override
    public String asTypeLiteralString() {
      return boxedType;
    }

    @Override
    String asGenericsTypeArgumentString() {
      return genericType;
    }

    @Override
    public PrimitiveJavaType asNullable() {
      return new PrimitiveJavaType(
          boxedType,
          primitiveType,
          genericType,
          true,
          asReference,
          asFunctionBoxed,
          asNullableReference);
    }

    @Override
    public String getAsInlineCastFunction(int depth) {
      return "AbstractBuilder::" + (isNullable() ? asNullableReference : asFunctionBoxed);
    }
  }
}
