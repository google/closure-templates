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

package com.google.template.soy.plugin.java.internal;

import com.google.auto.value.AutoOneOf;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.MethodSignature;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.FloatType;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.StringType;
import javax.annotation.Nullable;

/** A JavaValue for validating plugins. */
final class ValidatorValue implements JavaValue {
  private final boolean error;
  private final ValueType valueType;
  private final ValidatorErrorReporter reporter;
  private final MethodSignature methodSignature;

  static ValidatorValue forConstantNull(ValidatorErrorReporter reporter) {
    return new ValidatorValue(
        AutoOneOf_ValidatorValue_ValueType.constantNull(true),
        /* error= */ false,
        /* method= */ null,
        reporter);
  }

  static ValidatorValue forSoyType(SoyType type, ValidatorErrorReporter reporter) {
    return new ValidatorValue(
        AutoOneOf_ValidatorValue_ValueType.soyType(type),
        /* error= */ false,
        /* method= */ null,
        reporter);
  }

  static ValidatorValue forClazz(Class<?> clazz, ValidatorErrorReporter reporter) {
    return new ValidatorValue(
        AutoOneOf_ValidatorValue_ValueType.clazz(clazz),
        /* error= */ false,
        /* method= */ null,
        reporter);
  }

  static ValidatorValue forError(Class<?> clazz, ValidatorErrorReporter reporter) {
    return new ValidatorValue(
        AutoOneOf_ValidatorValue_ValueType.clazz(clazz),
        /* error= */ true,
        /* method= */ null,
        reporter);
  }

  static ValidatorValue forError(SoyType type, ValidatorErrorReporter reporter) {
    return new ValidatorValue(
        AutoOneOf_ValidatorValue_ValueType.soyType(type),
        /* error= */ true,
        /* method= */ null,
        reporter);
  }

  static ValidatorValue forMethodReturnType(
      MethodSignature method, ValidatorErrorReporter reporter) {
    SoyType type = null;
    if (method.returnType() == boolean.class) {
      type = BoolType.getInstance();
    }
    if (method.returnType() == int.class || method.returnType() == long.class) {
      type = IntType.getInstance();
    }
    if (method.returnType() == int.class) {
      type = IntType.getInstance();
    }
    if (method.returnType() == double.class) {
      type = FloatType.getInstance();
    }
    if (method.returnType() == String.class) {
      type = StringType.getInstance();
    }
    if (type != null) {
      return new ValidatorValue(
          AutoOneOf_ValidatorValue_ValueType.soyType(type), /* error= */ false, method, reporter);
    }
    return new ValidatorValue(
        AutoOneOf_ValidatorValue_ValueType.clazz(method.returnType()),
        /* error= */ false,
        method,
        reporter);
  }

  private ValidatorValue(
      ValueType valueType, boolean error, MethodSignature method, ValidatorErrorReporter reporter) {
    this.valueType = valueType;
    this.reporter = reporter;
    this.error = error;
    this.methodSignature = method;
  }

  @Override
  public ValidatorValue isNonNull() {
    return forSoyType(BoolType.getInstance(), reporter);
  }

  @Override
  public ValidatorValue isNull() {
    return forSoyType(BoolType.getInstance(), reporter);
  }

  @Override
  public ValidatorValue asSoyBoolean() {
    return asValue(BoolType.getInstance(), "asSoyBoolean");
  }

  @Override
  public ValidatorValue asSoyString() {
    return asValue(StringType.getInstance(), "asSoyString");
  }

  @Override
  public ValidatorValue asSoyInt() {
    return asValue(IntType.getInstance(), "asSoyInt");
  }

  @Override
  public ValidatorValue asSoyFloat() {
    return asValue(FloatType.getInstance(), "asSoyFloat");
  }

  private ValidatorValue asValue(SoyType newType, String methodName) {
    if (valueType.type() != ValueType.Type.SOY_TYPE) {
      reporter.nonSoyExpressionNotConvertible(
          isConstantNull() ? Object.class : valueType.clazz(), newType, methodName);
      return forError(newType, reporter);
    }
    if (!valueType.soyType().isAssignableFrom(newType)) {
      reporter.incompatibleSoyType(valueType.soyType(), newType, methodName);
      return forError(newType, reporter);
    }
    return forSoyType(newType, reporter);
  }

  @Override
  public ValidatorValue coerceToSoyBoolean() {
    if (valueType.type() != ValueType.Type.SOY_TYPE) {
      reporter.nonSoyExpressionNotCoercible(
          isConstantNull() ? Object.class : valueType.clazz(),
          BoolType.getInstance(),
          "coerceToSoyBoolean");
      return forError(BoolType.getInstance(), reporter);
    }
    return forSoyType(BoolType.getInstance(), reporter);
  }

  @Override
  public ValidatorValue coerceToSoyString() {
    if (valueType.type() != ValueType.Type.SOY_TYPE) {
      reporter.nonSoyExpressionNotCoercible(
          isConstantNull() ? Object.class : valueType.clazz(),
          StringType.getInstance(),
          "coerceToSoyString");
      return forError(StringType.getInstance(), reporter);
    }
    return forSoyType(StringType.getInstance(), reporter);
  }

  @Nullable
  MethodSignature methodInfo() {
    return methodSignature;
  }

  boolean isError() {
    return error;
  }

  ValueType valueType() {
    return valueType;
  }

  boolean hasSoyType() {
    return valueType.type() == ValueType.Type.SOY_TYPE;
  }

  boolean isConstantNull() {
    return valueType.type() == ValueType.Type.CONSTANT_NULL;
  }

  boolean hasClazz() {
    return valueType.type() == ValueType.Type.CLAZZ;
  }

  @AutoOneOf(ValueType.Type.class)
  abstract static class ValueType {
    enum Type {
      CONSTANT_NULL,
      SOY_TYPE,
      CLAZZ
    }

    abstract ValueType.Type type();

    // note: this should be a void return, the mvn dep for AutoOneOf doesn't support that yet
    abstract boolean constantNull();

    abstract SoyType soyType();

    abstract Class<?> clazz();
  }
}
