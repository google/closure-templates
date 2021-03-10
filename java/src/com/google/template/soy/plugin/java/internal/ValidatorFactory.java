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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.primitives.Primitives;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyProtoValue;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyVisualElement;
import com.google.template.soy.data.SoyVisualElementData;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.internal.proto.JavaQualifiedNames;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.MethodSignature;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.FloatType;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.UnionType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** A JavaValueFactory for validating plugins. */
final class ValidatorFactory extends JavaValueFactory {
  // List of classes that are allowed as parameter types for each soy types.

  private static final ImmutableSet<Class<?>> UNKNOWN_TYPES = ImmutableSet.of(SoyValue.class);

  private static final ImmutableSet<Class<?>> SANITIZED_TYPES =
      ImmutableSet.of(SoyValue.class, SanitizedContent.class, String.class);

  private static final ImmutableSet<Class<?>> BOOL_TYPES =
      ImmutableSet.of(SoyValue.class, boolean.class, BooleanData.class);

  private static final ImmutableSet<Class<?>> FLOAT_TYPES =
      ImmutableSet.of(SoyValue.class, double.class, FloatData.class, NumberData.class);

  private static final ImmutableSet<Class<?>> NUMBER_TYPES =
      ImmutableSet.of(SoyValue.class, double.class, NumberData.class);

  // We allow 'double' for soy int types because double has more precision than soy guarantees
  // for its int type.
  private static final ImmutableSet<Class<?>> INT_TYPES =
      ImmutableSet.of(
          SoyValue.class, long.class, IntegerData.class, NumberData.class, int.class, double.class);

  @SuppressWarnings("deprecation") // SoyLegacyObjectMap is deprecated
  private static final ImmutableSet<Class<?>> LEGACY_OBJECT_MAP_TYPES =
      ImmutableSet.of(SoyValue.class, SoyLegacyObjectMap.class, SoyDict.class);

  // TODO(sameb): Remove List?  We can't validate it's generic type.
  private static final ImmutableSet<Class<?>> LIST_TYPES =
      ImmutableSet.of(SoyValue.class, SoyList.class, List.class);

  private static final ImmutableSet<Class<?>> MAP_TYPES =
      ImmutableSet.of(SoyValue.class, SoyMap.class, SoyDict.class, SoyRecord.class);

  private static final ImmutableSet<Class<?>> RECORD_TYPES =
      ImmutableSet.of(SoyValue.class, SoyRecord.class);

  private static final ImmutableSet<Class<?>> STRING_TYPES =
      ImmutableSet.of(SoyValue.class, String.class, StringData.class);

  private static final ImmutableSet<Class<?>> NULL_TYPES =
      ImmutableSet.of(SoyValue.class, NullData.class);

  private static final ImmutableSet<Class<?>> PROTO_TYPES =
      ImmutableSet.of(SoyValue.class, Message.class, SoyProtoValue.class);

  private static final ImmutableSet<Class<?>> PROTO_ENUM_TYPES =
      ImmutableSet.of(SoyValue.class, int.class);

  private static final ImmutableSet<Class<?>> VE_TYPES =
      ImmutableSet.of(SoyValue.class, SoyVisualElement.class);

  private static final ImmutableSet<Class<?>> VE_DATA_TYPES =
      ImmutableSet.of(SoyValue.class, SoyVisualElementData.class);

  private static final ImmutableSet<Class<?>> MESSAGE_TYPES =
      ImmutableSet.of(SoyValue.class, SoyProtoValue.class, Message.class);

  private final ValidatorErrorReporter reporter;

  ValidatorFactory(ValidatorErrorReporter reporter) {
    this.reporter = reporter;
  }

  @Override
  public ValidatorValue callStaticMethod(Method method, JavaValue... params) {
    if (method == null) {
      reporter.nullMethod("callStaticMethod");
      return errorValue();
    }
    MethodSignature methodSignature = toMethodSignature(method);
    if (!Modifier.isStatic(method.getModifiers())) {
      reporter.staticMismatch(methodSignature, /* expectedInstance= */ false);
      return errorValue();
    }
    if (!validateParams(methodSignature, params, "callStaticMethod")) {
      return errorValue();
    }
    return ValidatorValue.forMethodReturnType(methodSignature, reporter);
  }

  @Override
  public JavaValue callStaticMethod(MethodSignature methodSignature, JavaValue... params) {
    if (methodSignature == null) {
      reporter.nullMethod("callStaticMethod");
      return errorValue();
    }
    if (!validateParams(methodSignature, params, "callStaticMethod")) {
      return errorValue();
    }
    return ValidatorValue.forMethodReturnType(methodSignature, reporter);
  }

  @Override
  public ValidatorValue callInstanceMethod(Method method, JavaValue... params) {
    if (method == null) {
      reporter.nullMethod("callInstanceMethod");
      return errorValue();
    }
    MethodSignature methodSignature = toMethodSignature(method);
    if (Modifier.isStatic(method.getModifiers())) {
      reporter.staticMismatch(methodSignature, /* expectedInstance= */ true);
      return errorValue();
    }
    return ValidatorValue.forMethodReturnType(methodSignature, reporter);
  }

  @Override
  public ValidatorValue callInstanceMethod(MethodSignature methodSignature, JavaValue... params) {
    if (methodSignature == null) {
      reporter.nullMethod("callInstanceMethod");
      return errorValue();
    }
    if (!validateParams(methodSignature, params, "callInstanceMethod")) {
      return errorValue();
    }
    return ValidatorValue.forMethodReturnType(methodSignature, reporter);
  }

  private static MethodSignature toMethodSignature(Method method) {
    return MethodSignature.create(
        method.getDeclaringClass().getName(),
        method.getName(),
        method.getReturnType(),
        method.getParameterTypes());
  }

  @Override
  public ValidatorValue listOf(List<JavaValue> args) {
    return ValidatorValue.forClazz(List.class, reporter);
  }

  @Override
  public ValidatorValue constant(boolean value) {
    return ValidatorValue.forSoyType(BoolType.getInstance(), reporter);
  }

  @Override
  public ValidatorValue constant(double value) {
    return ValidatorValue.forSoyType(FloatType.getInstance(), reporter);
  }

  @Override
  public ValidatorValue constant(long value) {
    return ValidatorValue.forSoyType(IntType.getInstance(), reporter);
  }

  @Override
  public ValidatorValue constant(String value) {
    return ValidatorValue.forSoyType(StringType.getInstance(), reporter);
  }

  @Override
  public ValidatorValue constantNull() {
    return ValidatorValue.forConstantNull(reporter);
  }

  /**
   * Returns true if we should continue doing more validation, false if the parameters were so
   * wildly invalid that we can't continue doing any more validation.
   */
  private boolean validateParams(
      MethodSignature method, JavaValue[] userParams, String callerMethodName) {
    if (userParams == null) {
      reporter.nullParamArray(method, callerMethodName);
      return false;
    }

    ImmutableList<Class<?>> methodParams = method.arguments();
    if (methodParams.size() != userParams.length) {
      reporter.invalidParameterLength(method, userParams);
      return false;
    }

    for (int i = 0; i < userParams.length; i++) {
      Class<?> methodParam = methodParams.get(i);
      if (userParams[i] == null) {
        reporter.nullParam(method, i + 1, methodParam);
        continue;
      }
      ValidatorValue userValue = (ValidatorValue) userParams[i];
      switch (userValue.valueType().type()) {
        case CLAZZ:
          if (!methodParam.isAssignableFrom(userValue.valueType().clazz())) {
            reporter.invalidParameterType(method, i, methodParam, userValue.valueType().clazz());
          }
          break;
        case CONSTANT_NULL: // fall through
        case SOY_TYPE:
          validateParameter(method, i, methodParam, userValue);
          break;
      }
    }
    return true;
  }

  private void validateParameter(
      MethodSignature method, int paramIdx, Class<?> expectedParamType, ValidatorValue value) {
    // First we validate that the type is allowed based on the function's signature (if any).
    ValidationResult validationResult;
    if (value.isConstantNull()) {
      // If the value is for our "constant null", then we special-case things to allow
      // any valid type (expect primitives).
      // TODO(sameb): Limit the allowed types to ones that valid for real soy types, e.g
      // the union of all the values the *_TYPES constants + protos + proto enums - primitives.
      validationResult =
          Primitives.allPrimitiveTypes().contains(expectedParamType)
              ? ValidationResult.forNullToPrimitive(NullType.getInstance())
              : ValidationResult.valid();
    } else {
      validationResult = isValidClassForType(expectedParamType, value.valueType().soyType());
    }
    if (validationResult.result() != ValidationResult.Result.VALID) {
      reporter.invalidParameterType(method, paramIdx, expectedParamType, validationResult);
    }
  }

  @AutoValue
  abstract static class ValidationResult {
    enum Result {
      VALID,
      NULL_TO_PRIMITIVE,
      INVALID,
    }

    abstract Result result();

    @Nullable
    abstract SoyType allowedSoyType();

    abstract ImmutableSet<String> allowedTypes();

    ValidationResult merge(ValidationResult other) {
      if (other.result() == Result.VALID) {
        return this;
      }
      switch (result()) {
        case VALID:
          return other;
        case NULL_TO_PRIMITIVE:
        case INVALID:
          // When merging, the allowed types are the intersection of each type.
          return ValidationResult.invalid(Sets.intersection(allowedTypes(), other.allowedTypes()));
      }
      throw new AssertionError("above switch is exhaustive");
    }

    static ValidationResult valid() {
      return new AutoValue_ValidatorFactory_ValidationResult(Result.VALID, null, ImmutableSet.of());
    }

    static ValidationResult forNullToPrimitive(SoyType type) {
      return new AutoValue_ValidatorFactory_ValidationResult(
          Result.NULL_TO_PRIMITIVE, type, ImmutableSet.of());
    }

    static ValidationResult invalid(Set<String> allowedTypes) {
      return new AutoValue_ValidatorFactory_ValidationResult(
          Result.INVALID, null, ImmutableSet.copyOf(allowedTypes));
    }
  }

  /**
   * Returns the result of validating if the clazz is allowed as a parameter type for the given soy
   * type.
   */
  private static ValidationResult isValidClassForType(Class<?> clazz, SoyType type) {
    // Exit early if the class is primitive and the type is nullable -- that's not allowed.
    // Then remove null from the type.  This allows us to accept precise params for nullable
    // types, e.g, for int|null we can allow IntegerData (which will be passed as 'null').
    if (SoyTypes.isNullable(type) && Primitives.allPrimitiveTypes().contains(clazz)) {
      return ValidationResult.forNullToPrimitive(type);
    }

    ImmutableSet<Class<?>> expectedClasses = null;
    GenericDescriptor expectedDescriptor = null;
    type = SoyTypes.tryRemoveNull(type);
    switch (type.getKind()) {
      case ANY:
      case UNKNOWN:
        expectedClasses = UNKNOWN_TYPES;
        break;
      case ATTRIBUTES:
      case CSS:
      case ELEMENT:
      case HTML:
      case URI:
      case TRUSTED_RESOURCE_URI:
      case JS:
        expectedClasses = SANITIZED_TYPES;
        break;
      case BOOL:
        expectedClasses = BOOL_TYPES;
        break;
      case FLOAT:
        expectedClasses = FLOAT_TYPES;
        break;
      case INT:
        expectedClasses = INT_TYPES;
        break;
      case LEGACY_OBJECT_MAP:
        expectedClasses = LEGACY_OBJECT_MAP_TYPES;
        break;
      case LIST:
        expectedClasses = LIST_TYPES;
        break;
      case MAP:
        expectedClasses = MAP_TYPES;
        break;
      case RECORD:
        expectedClasses = RECORD_TYPES;
        break;
      case STRING:
        expectedClasses = STRING_TYPES;
        break;
      case NULL:
        expectedClasses = NULL_TYPES;
        break;
      case MESSAGE:
        expectedClasses = MESSAGE_TYPES;
        break;
      case PROTO:
        expectedClasses = PROTO_TYPES;
        expectedDescriptor = ((SoyProtoType) type).getDescriptor();
        break;
      case PROTO_ENUM:
        expectedClasses = PROTO_ENUM_TYPES;
        expectedDescriptor = ((SoyProtoEnumType) type).getDescriptor();
        break;
      case UNION:
        // number is a special case, it should work for double and NumberData
        if (type.equals(SoyTypes.NUMBER_TYPE)) {
          expectedClasses = NUMBER_TYPES;
          break;
        }
        // If this is a union, make sure the type is valid for every member.
        // If the type isn't valid for any member, then there's no guarantee this will work
        // for an arbitrary template at runtime.
        ValidationResult result = ValidationResult.valid();
        for (SoyType member : ((UnionType) type).getMembers()) {
          result.merge(isValidClassForType(clazz, member));
        }
        return result;
      case VE:
        expectedClasses = VE_TYPES;
        break;
      case VE_DATA:
        expectedClasses = VE_DATA_TYPES;
        break;
      case TEMPLATE:
      case PROTO_TYPE:
      case PROTO_ENUM_TYPE:
      case PROTO_EXTENSION:
      case PROTO_MODULE:
      case TEMPLATE_TYPE:
      case TEMPLATE_MODULE:
        throw new IllegalStateException(
            "Cannot have " + type.getKind() + " from function signature");
    }

    checkState(expectedClasses != null, "expectedClass not set!");
    if (expectedClasses.contains(clazz)) {
      return ValidationResult.valid();
    }
    ImmutableSet<String> expectedDescriptorNames = ImmutableSet.of();
    if (expectedDescriptor instanceof Descriptor) {
      expectedDescriptorNames =
          ImmutableSet.of(JavaQualifiedNames.getClassName((Descriptor) expectedDescriptor));
      if (matchesProtoDescriptor(Message.class, clazz, expectedDescriptor)) {
        return ValidationResult.valid();
      }
    }
    if (expectedDescriptor instanceof EnumDescriptor) {
      expectedDescriptorNames =
          ImmutableSet.of(JavaQualifiedNames.getClassName((EnumDescriptor) expectedDescriptor));
      if (clazz.isEnum()
          && matchesProtoDescriptor(ProtocolMessageEnum.class, clazz, expectedDescriptor)) {
        return ValidationResult.valid();
      }
    }
    // If none of the above conditions match, we failed.
    return ValidationResult.invalid(
        Stream.concat(
                expectedClasses.stream().map(Class::getName), expectedDescriptorNames.stream())
            .collect(toImmutableSet()));
  }

  private static boolean matchesProtoDescriptor(
      Class<?> expectedSupertype, Class<?> actualParamClass, GenericDescriptor expectedDescriptor) {
    if (!expectedSupertype.isAssignableFrom(actualParamClass)) {
      return false;
    }
    return nameFromDescriptor(actualParamClass).orElse("").equals(expectedDescriptor.getFullName());
  }

  static Optional<String> nameFromDescriptor(Class<?> protoType) {
    GenericDescriptor actualDescriptor;
    try {
      actualDescriptor =
          (GenericDescriptor) protoType.getDeclaredMethod("getDescriptor").invoke(null);
    } catch (ReflectiveOperationException roe) {
      return Optional.empty();
    }
    return Optional.of(actualDescriptor.getFullName());
  }

  /**
   * Returns a stub error value, for use in continuing in scenarios where we don't know expected
   * type.
   */
  private ValidatorValue errorValue() {
    return ValidatorValue.forError(BoolType.getInstance(), reporter);
  }
}
