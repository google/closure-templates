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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.template.soy.plugin.java.internal.ValidatorFactory.nameFromDescriptor;

import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.MethodSignature;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.LegacyObjectMapType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.RecordType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.UnionType;
import com.google.template.soy.types.UnknownType;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Validates plugin functions. */
public class JavaPluginValidator {

  private final SoyTypeRegistry typeRegistry;
  private final ErrorReporter baseReporter;

  public JavaPluginValidator(ErrorReporter reporter, SoyTypeRegistry typeRegistry) {
    this.typeRegistry = typeRegistry;
    this.baseReporter = reporter;
  }

  public void validate(
      String fnName,
      SoyJavaSourceFunction fn,
      List<SoyType> expectedParams,
      SoyType expectedReturn,
      SourceLocation sourceLocation,
      boolean includeTriggeredInTemplateMsg) {
    ValidatorErrorReporter reporter =
        new ValidatorErrorReporter(
            baseReporter, fnName, fn.getClass(), sourceLocation, includeTriggeredInTemplateMsg);
    ValidatorFactory factory = new ValidatorFactory(reporter);
    ValidatorContext context = new ValidatorContext(reporter);
    JavaValue result = null;
    try {
      result =
          fn.applyForJavaSource(
              factory,
              expectedParams.stream()
                  .map(t -> ValidatorValue.forSoyType(t, reporter))
                  .collect(toImmutableList()),
              context);
      if (result == null) {
        reporter.nullReturn();
      }
    } catch (Throwable t) {
      BaseUtils.trimStackTraceTo(t, getClass());
      reporter.unexpectedError(t);
    }
    // Note: Successful return of null is reported above.
    if (result != null) {
      validateReturnValue((ValidatorValue) result, expectedReturn, reporter);
    }
  }

  private void validateReturnValue(
      ValidatorValue pluginReturnValue, SoyType expectedType, ValidatorErrorReporter reporter) {
    // Don't bother doing anything if this is an error value, we already recorded errors.
    if (pluginReturnValue.isError()) {
      return;
    }

    SoyType actualSoyType = null;
    switch (pluginReturnValue.valueType().type()) {
      case CONSTANT_NULL:
        actualSoyType = NullType.getInstance();
        break;
      case SOY_TYPE:
        actualSoyType = pluginReturnValue.valueType().soyType();
        break;
      case CLAZZ:
        actualSoyType = null;
        break;
    }
    if (actualSoyType == null) {
      Class<?> actualClass;
      MethodSignature method = pluginReturnValue.methodInfo();
      if (method != null) {
        actualClass = method.returnType();
      } else {
        actualClass = pluginReturnValue.valueType().clazz();
      }
      if (List.class.isAssignableFrom(actualClass)) {
        if (expectedType instanceof ListType) {
          actualSoyType = expectedType;
        } else if (expectedType.getKind() == SoyType.Kind.UNKNOWN
            || expectedType.getKind() == SoyType.Kind.ANY) {
          actualSoyType = ListType.of(UnknownType.getInstance());
        } else {
          reporter.invalidReturnType(actualClass, expectedType, method);
          return;
        }
      } else if (Map.class.isAssignableFrom(actualClass)) {
        // maps are allowed as long as the value is one of our static map types.  We don't allow
        // maps to be returned as ? (unlike lists which are less ambiguous)
        if (expectedType instanceof MapType
            || expectedType instanceof RecordType
            || expectedType instanceof LegacyObjectMapType) {
          actualSoyType = expectedType;
        } else {
          reporter.invalidReturnType(actualClass, expectedType, method);
          return;
        }
      } else if (SoyValue.class.isAssignableFrom(actualClass)) {
        // TODO(sameb): This could validate that the boxed soy type is valid for the return type
        // at compile time too.
        actualSoyType = expectedType;
      } else if (Message.class.isAssignableFrom(actualClass)) {
        Optional<SoyType> returnType =
            soyTypeForProtoOrEnum(actualClass, expectedType, method, reporter);
        if (!returnType.isPresent()) {
          return; // error already reported
        }
        actualSoyType = returnType.get();
      } else if (actualClass.isEnum() && ProtocolMessageEnum.class.isAssignableFrom(actualClass)) {
        Optional<SoyType> returnType =
            soyTypeForProtoOrEnum(actualClass, expectedType, method, reporter);
        if (!returnType.isPresent()) {
          return; // error already reported
        }
        if (!expectedType.isAssignableFromStrict(returnType.get())) {
          reporter.incompatibleReturnType(returnType.get(), expectedType, method);
          return;
        }
        // TODO(lukes): SoyExpression should have a way to track type information with an unboxed
        // int that is actually a proto enum.  Like we do with SanitizedContents
        actualSoyType = IntType.getInstance();
      } else {
        reporter.invalidReturnType(actualClass, expectedType, method);
        return;
      }
    }

    // We special-case proto enums when the return expression is an INT, to allow someone to return
    // an 'int' representing the enum.
    boolean isPossibleProtoEnum =
        actualSoyType.getKind() == SoyType.Kind.INT
            && isOrContains(expectedType, SoyType.Kind.PROTO_ENUM);
    if (!isPossibleProtoEnum && !expectedType.isAssignableFromStrict(actualSoyType)) {
      reporter.incompatibleReturnType(actualSoyType, expectedType, pluginReturnValue.methodInfo());
    }
  }

  /**
   * Attempts to discover the SoyType for a proto or proto enum, reporting an error if unable to.
   */
  private Optional<SoyType> soyTypeForProtoOrEnum(
      Class<?> actualType,
      SoyType expectedType,
      MethodSignature method,
      ValidatorErrorReporter reporter) {
    // Message isn't supported because we can't get a descriptor from it.
    if (actualType == Message.class) {
      reporter.invalidReturnType(Message.class, expectedType, method);
      return Optional.empty();
    }
    Optional<String> fullName = nameFromDescriptor(actualType);
    if (!fullName.isPresent()) {
      reporter.incompatibleReturnType(actualType, expectedType, method);
      return Optional.empty();
    }
    SoyType returnType = typeRegistry.getProtoRegistry().getProtoType(fullName.get());
    if (returnType == null) {
      reporter.incompatibleReturnType(actualType, expectedType, method);
      return Optional.empty();
    }
    return Optional.of(returnType);
  }

  /** Returns true if the type is the given kind or contains the given kind. */
  private boolean isOrContains(SoyType type, SoyType.Kind kind) {
    if (type.getKind() == kind) {
      return true;
    }
    if (type.getKind() == SoyType.Kind.UNION) {
      for (SoyType member : ((UnionType) type).getMembers()) {
        if (member.getKind() == kind) {
          return true;
        }
      }
    }
    return false;
  }
}
