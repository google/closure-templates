/*
 * Copyright 2021 Google Inc.
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

package com.google.template.soy.passes;

import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.primitives.Primitives;
import com.google.protobuf.Message;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.internal.proto.JavaQualifiedNames;
import com.google.template.soy.plugin.java.restricted.MethodSignature;
import com.google.template.soy.soytree.ExternNode;
import com.google.template.soy.soytree.JavaImplNode;
import com.google.template.soy.soytree.JsImplNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.types.FunctionType;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/** Checks various invariants related to externs. */
@RunBefore(ResolveExpressionTypesPass.class)
class ValidateExternsPass implements CompilerFilePass {

  private static final SoyErrorKind ATTRIBUTE_REQUIRED =
      SoyErrorKind.of("Attribute ''{0}'' is required.");
  private static final SoyErrorKind UNKNOWN_TYPE = SoyErrorKind.of("Type ''{0}'' not loaded.");
  private static final SoyErrorKind ARITY_MISMATCH =
      SoyErrorKind.of("Implementation must match extern signature with {0} parameter(s).");
  private static final SoyErrorKind INCOMPATIBLE_TYPE =
      SoyErrorKind.of("Java type ''{0}'' is not coercible to Soy type ''{1}''.");
  private static final SoyErrorKind OVERLOAD_RETURN_CONFLICT =
      SoyErrorKind.of(
          "Overloaded extern must have the same return type as the earlier extern defined on {0}.");
  private static final SoyErrorKind OVERLOAD_PARAM_CONFLICT =
      SoyErrorKind.of(
          "Overloaded extern parameters are ambiguous with the earlier extern defined on {0}.");

  private final ErrorReporter errorReporter;

  ValidateExternsPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    ListMultimap<String, ExternNode> externIndex =
        file.getExterns().stream()
            .collect(toImmutableListMultimap(e -> e.getIdentifier().identifier(), e -> e));
    for (Entry<String, Collection<ExternNode>> entry : externIndex.asMap().entrySet()) {
      validateNamedExterns((List<ExternNode>) entry.getValue());
    }
  }

  private void validateNamedExterns(List<ExternNode> externs) {
    for (ExternNode extern : externs) {
      extern.getJavaImpl().ifPresent(java -> validateJava(extern, java));
      extern.getJsImpl().ifPresent(this::validateJs);
    }

    for (int i = 1; i < externs.size(); i++) {
      for (int j = 0; j < i; j++) {
        ExternNode first = externs.get(j);
        ExternNode second = externs.get(i);
        FunctionType type1 = first.getType();
        FunctionType type2 = second.getType();

        if (type1.getReturnType() != type2.getReturnType()) {
          // All overloads must have the same return type.
          errorReporter.report(
              second.typeNode().sourceLocation(),
              OVERLOAD_RETURN_CONFLICT,
              first.getSourceLocation().toLineColumnString());
        } else if (type1.getParameters().size() != type2.getParameters().size()) {
          // good
        } else if (type1.isAssignableFromLoose(type2) || type2.isAssignableFromLoose(type1)) {
          // Allow overloads with the same number of params, but only if the types are not
          // ambiguous.
          errorReporter.report(
              second.typeNode().sourceLocation(),
              OVERLOAD_PARAM_CONFLICT,
              first.getSourceLocation().toLineColumnString());
        }
      }
    }
  }

  private void validateJava(ExternNode extern, JavaImplNode java) {
    int requiredParamCount = extern.getType().getParameters().size();

    // For now some of these won't trigger because the same checks exist in JavaImplNode.
    if (Strings.isNullOrEmpty(java.className())) {
      errorReporter.report(java.getSourceLocation(), ATTRIBUTE_REQUIRED, JavaImplNode.CLASS);
    }
    if (Strings.isNullOrEmpty(java.methodName())) {
      errorReporter.report(java.getSourceLocation(), ATTRIBUTE_REQUIRED, JavaImplNode.METHOD);
    }

    if (Strings.isNullOrEmpty(java.returnType())) {
      errorReporter.report(java.getSourceLocation(), ATTRIBUTE_REQUIRED, JavaImplNode.RETURN);
    } else {
      validateTypes(
          java.returnType(),
          extern.getType().getReturnType(),
          () -> java.getAttributeValueLocation(JavaImplNode.RETURN));
    }

    // Verify that the soy arity type and the java arity are equal.
    if (java.params().size() != requiredParamCount) {
      errorReporter.report(
          java.getAttributeValueLocation(JavaImplNode.PARAMS), ARITY_MISMATCH, requiredParamCount);
    } else {
      ImmutableList<String> paramTypes = java.params();
      for (int i = 0; i < paramTypes.size(); i++) {
        String paramType = paramTypes.get(i);
        validateTypes(
            paramType,
            extern.getType().getParameters().get(i).getType(),
            () -> java.getAttributeValueLocation(JavaImplNode.PARAMS));
      }
    }
  }

  private void validateTypes(String javaTypeName, SoyType soyType, Supplier<SourceLocation> loc) {
    Class<?> javaType = getType(javaTypeName);
    if (javaType != null) {
      // Verify that the soy param type and the java param type are compatible.
      if (!typesAreCompatible(javaType, soyType)) {
        errorReporter.report(loc.get(), INCOMPATIBLE_TYPE, javaType.getName(), soyType);
      }
    } else if (!protoTypesAreCompatible(javaTypeName, soyType)) {
      // Protos won't be loaded but we can make sure they are compatible via the descriptor.
      errorReporter.report(loc.get(), UNKNOWN_TYPE, javaTypeName);
    }
  }

  private void validateJs(JsImplNode jsImplNode) {}

  @Nullable
  private static Class<?> getType(String typeName) {
    try {
      return MethodSignature.forName(typeName);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  private static boolean typesAreCompatible(Class<?> javaType, SoyType soyType) {
    javaType = Primitives.wrap(javaType);
    switch (soyType.getKind()) {
      case INT:
        return javaType == Integer.class || javaType == Long.class || javaType == IntegerData.class;
      case FLOAT:
        return javaType == Integer.class
            || javaType == Long.class
            || javaType == Float.class
            || javaType == Double.class
            || javaType == IntegerData.class
            || javaType == FloatData.class;
      case STRING:
        return javaType == String.class || javaType == StringData.class;
      case BOOL:
        return javaType == Boolean.class || javaType == BooleanData.class;
      case UNKNOWN:
        return true;
      case ANY:
        return javaType == Object.class || javaType == SoyData.class;
      case LIST:
        return javaType == List.class;
      case MESSAGE:
        return javaType == Message.class;
      case PROTO:
        SoyProtoType protoType = (SoyProtoType) soyType;
        return JavaQualifiedNames.getClassName(protoType.getDescriptor())
            .equals(javaType.getName());
      case PROTO_ENUM:
        SoyProtoEnumType protoEnumType = (SoyProtoEnumType) soyType;
        return JavaQualifiedNames.getClassName(protoEnumType.getDescriptor())
            .equals(javaType.getName());
      default:
        return false;
    }
  }

  private static boolean protoTypesAreCompatible(String javaType, SoyType soyType) {
    switch (soyType.getKind()) {
      case PROTO:
        SoyProtoType protoType = (SoyProtoType) soyType;
        return JavaQualifiedNames.getClassName(protoType.getDescriptor()).equals(javaType);
      case PROTO_ENUM:
        SoyProtoEnumType protoEnumType = (SoyProtoEnumType) soyType;
        return JavaQualifiedNames.getClassName(protoEnumType.getDescriptor()).equals(javaType);
      default:
        return false;
    }
  }
}
