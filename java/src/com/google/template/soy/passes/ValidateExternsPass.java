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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.html.types.SafeHtml;
import com.google.common.html.types.SafeHtmlProto;
import com.google.common.html.types.SafeUrl;
import com.google.common.html.types.SafeUrlProto;
import com.google.common.html.types.TrustedResourceUrl;
import com.google.common.html.types.TrustedResourceUrlProto;
import com.google.common.primitives.Primitives;
import com.google.protobuf.Message;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.data.PartialSoyTemplate;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyTemplate;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyVisualElement;
import com.google.template.soy.data.TemplateValue;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.internal.proto.JavaQualifiedNames;
import com.google.template.soy.plugin.java.MethodChecker;
import com.google.template.soy.plugin.java.ReadMethodData;
import com.google.template.soy.plugin.java.restricted.MethodSignature;
import com.google.template.soy.soytree.ExternNode;
import com.google.template.soy.soytree.JavaImplNode;
import com.google.template.soy.soytree.JsImplNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.types.FunctionType;
import com.google.template.soy.types.IterableType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.RecordType;
import com.google.template.soy.types.SetType;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.UnionType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
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
  private static final SoyErrorKind INCOMPATIBLE_PARAM_TYPE =
      SoyErrorKind.of("Soy type ''{1}'' is not coercible to Java type ''{0}''.");
  private static final SoyErrorKind INCOMPATIBLE_RETURN_TYPE =
      SoyErrorKind.of("Java type ''{0}'' is not coercible to Soy type ''{1}''.");
  private static final SoyErrorKind OVERLOAD_RETURN_CONFLICT =
      SoyErrorKind.of(
          "Overloaded extern must have the same return type as the earlier extern defined on {0}.");
  private static final SoyErrorKind OVERLOAD_PARAM_CONFLICT =
      SoyErrorKind.of(
          "Overloaded extern parameters are ambiguous with the earlier extern defined on {0}.");
  private static final SoyErrorKind JS_IMPL_OVERLOADS_MUST_MATCH =
      SoyErrorKind.of("Overloads for the same extern symbol must have the same jsimpl.");
  private static final SoyErrorKind NO_SUCH_JAVA_CLASS =
      SoyErrorKind.of(
          "Java implementation class not loaded."
          );
  private static final SoyErrorKind NOT_PUBLIC =
      SoyErrorKind.of("Both the Java class and method must be public.");
  private static final SoyErrorKind NO_SUCH_JAVA_METHOD_NAME =
      SoyErrorKind.of(
          "No method ''{0}'' exists on implementation class.{1}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind JAVA_METHOD_SIG_MISMATCH =
      SoyErrorKind.of(
          "Method ''{0}'' of implementation class does not match the provided arguments. Available"
              + " signatures: {1}.");
  private static final SoyErrorKind JAVA_METHOD_TYPE_MISMATCH =
      SoyErrorKind.of("Attribute ''type'' should have value ''{0}''.");
  private static final SoyErrorKind JAVA_METHOD_RETURN_TYPE_MISMATCH =
      SoyErrorKind.of("Return type of method ''{0}'' must be one of [{1}].");
  private static final SoyErrorKind IMPLICIT_PARAM_ORDER =
      SoyErrorKind.of("Implicit Java parameter {0} must come at the end of the parameter list.");

  // Additions to this should be minimal as this circumvents Soy's compile time VE checks. Please
  private static final ImmutableSetMultimap<String, String> ALLOWED_VE_EXTERNS =
      ImmutableSetMultimap.of(
          );

  private final ErrorReporter errorReporter;
  private final MethodChecker checker;
  private final boolean validateJavaMethods;

  ValidateExternsPass(
      ErrorReporter errorReporter, MethodChecker checker, boolean validateJavaMethods) {
    this.errorReporter = errorReporter;
    this.checker = checker;
    this.validateJavaMethods = validateJavaMethods;
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

        if (!jsImplsEqual(first.getJsImpl(), second.getJsImpl())) {
          errorReporter.report(second.typeNode().sourceLocation(), JS_IMPL_OVERLOADS_MUST_MATCH);
        }
      }
    }
  }

  private boolean jsImplsEqual(Optional<JsImplNode> first, Optional<JsImplNode> second) {
    boolean moduleEquals = first.map(JsImplNode::module).equals(second.map(JsImplNode::module));
    boolean functionEquals =
        first.map(JsImplNode::function).equals(second.map(JsImplNode::function));
    return moduleEquals && functionEquals;
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
          INCOMPATIBLE_RETURN_TYPE,
          () -> java.getAttributeValueLocation(JavaImplNode.RETURN),
          extern,
          Mode.EXTENDS);
    }

    List<String> paramTypes = new ArrayList<>(java.params());
    boolean inTail = true;
    for (int i = paramTypes.size() - 1; i >= 0; i--) {
      if (JavaImplNode.isParamImplicit(paramTypes.get(i))) {
        paramTypes.remove(i);
        if (!inTail) {
          errorReporter.report(
              java.getAttributeValueLocation(JavaImplNode.PARAMS),
              IMPLICIT_PARAM_ORDER,
              paramTypes.get(i));
        }
      } else {
        inTail = false;
      }
    }

    // Verify that the soy arity type and the java arity are equal.
    if (paramTypes.size() != requiredParamCount) {
      errorReporter.report(
          java.getAttributeValueLocation(JavaImplNode.PARAMS), ARITY_MISMATCH, requiredParamCount);
    } else {
      for (int i = 0; i < paramTypes.size(); i++) {
        String paramType = paramTypes.get(i);
        validateTypes(
            paramType,
            extern.getType().getParameters().get(i).getType(),
            INCOMPATIBLE_PARAM_TYPE,
            () -> java.getAttributeValueLocation(JavaImplNode.PARAMS),
            extern,
            Mode.SUPER);
      }
    }

    if (!validateJavaMethods) {
      // Any validations beyond this require looking at the actual implementation of the Java
      // method. We only want to do this in some cases, like when compiling for JBCSRC. If we're
      // compiling for something that doesn't use the Java implementations (like JS or the header
      // compiler) then we don't need the Java implementations so we don't do those validations here
      // and then we don't have to require them.
      return;
    }

    MethodChecker.Response response =
        checker.findMethod(java.className(), java.methodName(), java.returnType(), java.params());
    switch (response.getCode()) {
      case EXISTS:
        ReadMethodData method = response.getMethod();
        if (method.instanceMethod() == java.isStatic()
            || method.classIsInterface() != java.isInterface()) {
          String actualType;
          if (method.instanceMethod()) {
            if (method.classIsInterface()) {
              actualType = JavaImplNode.TYPE_INTERFACE;
            } else {
              actualType = JavaImplNode.TYPE_INSTANCE;
            }
          } else {
            if (method.classIsInterface()) {
              actualType = JavaImplNode.TYPE_STATIC_INTERFACE;
            } else {
              actualType = JavaImplNode.TYPE_STATIC;
            }
          }
          SourceLocation loc = java.getAttributeValueLocation(JavaImplNode.TYPE);
          if (loc.equals(SourceLocation.UNKNOWN)) {
            loc = java.getSourceLocation();
          }
          errorReporter.report(loc, JAVA_METHOD_TYPE_MISMATCH, actualType);
        }
        break;
      case NO_SUCH_CLASS:
        errorReporter.report(
            java.getAttributeValueLocation(JavaImplNode.CLASS), NO_SUCH_JAVA_CLASS);
        break;
      case NOT_PUBLIC:
        errorReporter.report(java.getAttributeValueLocation(JavaImplNode.METHOD), NOT_PUBLIC);
        break;
      case NO_SUCH_METHOD_SIG:
        errorReporter.report(
            java.getAttributeValueLocation(JavaImplNode.PARAMS),
            JAVA_METHOD_SIG_MISMATCH,
            java.methodName(),
            String.join(", ", response.getSuggesions()));
        break;
      case NO_SUCH_RETURN_TYPE:
        errorReporter.report(
            java.getAttributeValueLocation(JavaImplNode.RETURN),
            JAVA_METHOD_RETURN_TYPE_MISMATCH,
            java.methodName(),
            String.join(", ", response.getSuggesions()));
        break;
      case NO_SUCH_METHOD_NAME:
        String didYouMean =
            SoyErrors.getDidYouMeanMessage(response.getSuggesions(), java.methodName());
        errorReporter.report(
            java.getAttributeValueLocation(JavaImplNode.METHOD),
            NO_SUCH_JAVA_METHOD_NAME,
            java.methodName(),
            didYouMean);
        break;
    }
  }

  private enum Mode {
    EXTENDS,
    SUPER
  }

  private void validateTypes(
      String javaTypeName,
      SoyType soyType,
      SoyErrorKind compatibleErrorKind,
      Supplier<SourceLocation> loc,
      ExternNode extern,
      Mode mode) {
    Class<?> javaType = getType(javaTypeName);
    if (javaType != null) {
      // Verify that the soy param type and the java param type are compatible.
      if (!typesAreCompatible(javaType, soyType, extern, mode)) {
        errorReporter.report(loc.get(), compatibleErrorKind, javaType.getName(), soyType);
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

  private static final ImmutableSet<SoyType.Kind> ALLOWED_PARAMETERIZED_TYPES =
      ImmutableSet.of(
          SoyType.Kind.INT,
          SoyType.Kind.FLOAT,
          SoyType.Kind.STRING,
          SoyType.Kind.BOOL,
          SoyType.Kind.MESSAGE,
          SoyType.Kind.PROTO,
          SoyType.Kind.PROTO_ENUM);

  private static final ImmutableSet<SoyType.Kind> ALLOWED_UNION_MEMBERS =
      ImmutableSet.<SoyType.Kind>builder()
          .addAll(ALLOWED_PARAMETERIZED_TYPES)
          .add(SoyType.Kind.HTML)
          .add(SoyType.Kind.TRUSTED_RESOURCE_URI)
          .add(SoyType.Kind.URI)
          .build();

  private static final ImmutableSet<SoyType.Kind> ALLOWED_RECORD_MEMBERS =
      ImmutableSet.<SoyType.Kind>builder()
          .addAll(ALLOWED_UNION_MEMBERS)
          .add(SoyType.Kind.CSS)
          .add(SoyType.Kind.ANY)
          .add(SoyType.Kind.UNKNOWN)
          .add(SoyType.Kind.NULL)
          .add(SoyType.Kind.UNDEFINED)
          .build();

  private static boolean typesAreCompatible(
      Class<?> javaType, SoyType soyType, ExternNode extern, Mode mode) {
    boolean nullable = SoyTypes.isNullish(soyType);
    boolean isPrimitive = Primitives.allPrimitiveTypes().contains(javaType);
    if (nullable && isPrimitive) {
      return false;
    }

    soyType = SoyTypes.tryRemoveNullish(soyType);
    javaType = Primitives.wrap(javaType);
    switch (soyType.getKind()) {
      case INT:
        return javaType == Integer.class || javaType == Long.class;
      case FLOAT:
        return javaType == Double.class || javaType == Float.class;
      case STRING:
        return javaType == String.class;
      case BOOL:
        return javaType == Boolean.class;
      case UNION:
        if (soyType.equals(SoyTypes.NUMBER_TYPE)) {
          return javaType == Number.class || javaType == Double.class;
        }
        if (((UnionType) soyType)
            .getMembers().stream().anyMatch(t -> !ALLOWED_UNION_MEMBERS.contains(t.getKind()))) {
          return false;
        }
      // fallthrough
      case ANY:
      case UNKNOWN:
        return javaType == Object.class || javaType == SoyValue.class;
      case ITERABLE:
        return mode == Mode.EXTENDS
            ? Iterable.class.isAssignableFrom(javaType)
            : (isAllowedParameterizedType(((IterableType) soyType).getElementType(), extern)
                    && javaType == Iterable.class)
                || javaType == SoyValue.class;
      case LIST:
        return mode == Mode.EXTENDS
            ? Iterable.class.isAssignableFrom(javaType)
            : (isAllowedParameterizedType(((ListType) soyType).getElementType(), extern)
                    && !javaType.equals(Object.class)
                    && javaType.isAssignableFrom(ImmutableList.class))
                || javaType == SoyValue.class;
      case SET:
        if (!isAllowedParameterizedType(((SetType) soyType).getElementType(), extern)) {
          return false;
        }
        return mode == Mode.EXTENDS
            ? Iterable.class.isAssignableFrom(javaType)
            : (!javaType.equals(Object.class) && javaType.isAssignableFrom(ImmutableSet.class));
      case MAP:
        MapType mapType = (MapType) soyType;
        if (!ALLOWED_PARAMETERIZED_TYPES.contains(mapType.getKeyType().getKind())
            || !ALLOWED_PARAMETERIZED_TYPES.contains(mapType.getValueType().getKind())) {
          return false;
        }
        return mode == Mode.EXTENDS
            ? Map.class.isAssignableFrom(javaType)
            : javaType == Map.class || javaType == ImmutableMap.class;
      case RECORD:
        RecordType recordType = (RecordType) soyType;
        if (!recordType.getMembers().stream()
            .flatMap(m -> SoyTypes.expandUnions(m.checkedType()).stream())
            .map(SoyType::getKind)
            .allMatch(ALLOWED_RECORD_MEMBERS::contains)) {
          return false;
        }
        return mode == Mode.EXTENDS
            ? Map.class.isAssignableFrom(javaType)
            : javaType == Map.class || javaType == ImmutableMap.class;
      case MESSAGE:
        return mode == Mode.EXTENDS
            ? Message.class.isAssignableFrom(javaType)
            : javaType == Message.class;
      case URI:
        return javaType == SafeUrl.class || javaType == SafeUrlProto.class;
      case TRUSTED_RESOURCE_URI:
        return javaType == TrustedResourceUrl.class || javaType == TrustedResourceUrlProto.class;
      case ATTRIBUTES:
        return javaType == SanitizedContent.class;
      case HTML:
        return javaType == SafeHtml.class || javaType == SafeHtmlProto.class;
      case PROTO:
        SoyProtoType protoType = (SoyProtoType) soyType;
        return JavaQualifiedNames.getClassName(protoType.getDescriptor())
            .equals(javaType.getName());
      case PROTO_ENUM:
        SoyProtoEnumType protoEnumType = (SoyProtoEnumType) soyType;
        return JavaQualifiedNames.getClassName(protoEnumType.getDescriptor())
            .equals(javaType.getName());
      case VE:
        return isAllowedVeExtern(extern) && javaType == SoyVisualElement.class;
      case TEMPLATE:
        TemplateType templateType = (TemplateType) soyType;
        return javaType == TemplateValue.class
            || (javaType == SoyTemplate.class
                && templateType.getParameters().stream().noneMatch(p -> p.isRequired()))
            || (javaType == PartialSoyTemplate.class
                && templateType.getParameters().stream().anyMatch(p -> p.isRequired()));
      default:
        return false;
    }
  }

  private static boolean isAllowedParameterizedType(SoyType type, ExternNode extern) {
    return ALLOWED_PARAMETERIZED_TYPES.contains(type.getKind())
        || SoyTypes.NUMBER_TYPE.equals(type);
  }

  private static boolean isAllowedVeExtern(ExternNode extern) {
    return ALLOWED_VE_EXTERNS.containsEntry(
        extern.getSourceLocation().getFilePath().path(), extern.getIdentifier().identifier());
  }

  private static boolean protoTypesAreCompatible(String javaType, SoyType soyType) {
    soyType = SoyTypes.tryRemoveNullish(soyType);
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
