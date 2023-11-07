/*
 * Copyright 2020 Google Inc.
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

package com.google.template.soy.shared.internal;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableListMultimap.flatteningToImmutableListMultimap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Ascii;
import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.internal.proto.ProtoUtils;
import com.google.template.soy.shared.restricted.SoyMethod;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.ProtoExtensionImportType;
import com.google.template.soy.types.RecordType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.TemplateBindingUtil;
import com.google.template.soy.types.UnknownType;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/** Enum of built-in functions supported in Soy expressions. */
public enum BuiltinMethod implements SoyMethod {
  GET_EXTENSION("getExtension", 1) {

    @Override
    public boolean appliesToBase(SoyType baseType) {
      return isExtendableMessageType(baseType);
    }

    @Override
    public SoyType getReturnType(
        String methodName,
        SoyType baseType,
        List<ExprNode> params,
        SoyTypeRegistry soyTypeRegistry,
        ErrorReporter errorReporter) {
      SoyProtoType protoType = (SoyProtoType) baseType;
      var extType = getExtensionType(protoType, Iterables.getOnlyElement(params), errorReporter);
      if (extType.isEmpty()) {
        return UnknownType.getInstance();
      }
      // getExtension is incorrectly typed as non-nullable even though it can be null for singular
      // message fields.
      return SoyTypes.tryRemoveNullish(protoType.getFieldType(extType.get().getFieldName()));
    }
  },
  GET_READONLY_EXTENSION("getReadonlyExtension", 1) {

    @Override
    public boolean appliesToBase(SoyType baseType) {
      return isExtendableMessageType(baseType);
    }

    @Override
    public SoyType getReturnType(
        String methodName,
        SoyType baseType,
        List<ExprNode> params,
        SoyTypeRegistry soyTypeRegistry,
        ErrorReporter errorReporter) {
      SoyProtoType protoType = (SoyProtoType) baseType;
      var extType = getExtensionType(protoType, Iterables.getOnlyElement(params), errorReporter);
      if (extType.isEmpty()) {
        return UnknownType.getInstance();
      }
      if (extType.get().getDescriptor().getJavaType() != FieldDescriptor.JavaType.MESSAGE
          || extType.get().getDescriptor().isRepeated()) {
        errorReporter.report(
            params.get(0).getSourceLocation(),
            GET_READONLY_EXTENSION_MAY_ONLY_BE_CALLED_ON_MESSAGE_EXTENSIONS);
      }
      return SoyTypes.tryRemoveNullish(protoType.getFieldType(extType.get().getFieldName()));
    }
  },
  HAS_EXTENSION("hasExtension", 1) {

    @Override
    public boolean appliesToBase(SoyType baseType) {
      return isExtendableMessageType(baseType);
    }

    @Override
    public SoyType getReturnType(
        String methodName,
        SoyType baseType,
        List<ExprNode> params,
        SoyTypeRegistry soyTypeRegistry,
        ErrorReporter errorReporter) {
      SoyProtoType protoType = (SoyProtoType) baseType;
      var extType = getExtensionType(protoType, Iterables.getOnlyElement(params), errorReporter);
      if (extType.isEmpty()) {
        return UnknownType.getInstance();
      }
      if (extType.get().getDescriptor().isRepeated()) {
        errorReporter.report(
            params.get(0).getSourceLocation(),
            HAS_EXTENSION_MAY_ONLY_BE_CALLED_ON_SINGULAR_EXTENSIONS);
      }
      return BoolType.getInstance();
    }
  },

  HAS_PROTO_FIELD("has[X]", 0) {
    @Override
    public boolean appliesToBase(SoyType baseType) {
      Preconditions.checkArgument(!SoyTypes.isNullish(baseType));
      return SoyTypes.isKindOrUnionOfKind(baseType, SoyType.Kind.PROTO);
    }

    @Override
    public boolean appliesTo(String methodName, SoyType baseType) {
      var fieldName = getHasserFieldName(methodName);
      return fieldName.isPresent()
          && appliesToProto(fieldName.get(), baseType, this::acceptFieldDescriptor);
    }

    private boolean acceptFieldDescriptor(FieldDescriptor fd) {
      if (fd.isExtension()) {
        return false;
      }
      return fd.hasPresence();
    }

    @Override
    public SoyType getReturnType(
        String methodName,
        SoyType baseType,
        List<ExprNode> params,
        SoyTypeRegistry soyTypeRegistry,
        ErrorReporter errorReporter) {
      return BoolType.getInstance();
    }

    @Override
    ImmutableCollection<String> expandMethodNames(SoyType baseType, List<SoyType> argTypes) {
      return expandMethodNamesForProto(
          baseType, this::acceptFieldDescriptor, BuiltinMethod::fieldToHasMethodName);
    }
  },

  GET_PROTO_FIELD("get[X]", 0) {
    @Override
    public boolean appliesToBase(SoyType baseType) {
      Preconditions.checkArgument(!SoyTypes.isNullish(baseType));
      return SoyTypes.isKindOrUnionOfKind(baseType, SoyType.Kind.PROTO);
    }

    @Override
    public boolean appliesTo(String methodName, SoyType baseType) {
      var fieldName = getGetterFieldName(methodName);
      return fieldName.isPresent()
          && appliesToProto(fieldName.get(), baseType, this::acceptFieldDescriptor);
    }

    private boolean acceptFieldDescriptor(FieldDescriptor fd) {
      if (fd.isExtension()) {
        // should be impossible
        return false;
      }
      if (fd.getJavaType() == JavaType.MESSAGE) {
        return true;
      }
      return true;
    }

    @Override
    public SoyType getReturnType(
        String methodName,
        SoyType baseType,
        List<ExprNode> params,
        SoyTypeRegistry soyTypeRegistry,
        ErrorReporter errorReporter) {
      String fieldName = getGetterFieldName(methodName).get();
      return computeTypeForProtoFieldName(baseType, fieldName, soyTypeRegistry);
    }

    @Override
    ImmutableCollection<String> expandMethodNames(SoyType baseType, List<SoyType> argTypes) {
      return expandMethodNamesForProto(
          baseType, this::acceptFieldDescriptor, BuiltinMethod::protoFieldToGetMethodName);
    }
  },

  GET_PROTO_FIELD_OR_UNDEFINED("get[X]OrUndefined", 0) {
    @Override
    public boolean appliesToBase(SoyType baseType) {
      Preconditions.checkArgument(!SoyTypes.isNullish(baseType));
      return SoyTypes.isKindOrUnionOfKind(baseType, SoyType.Kind.PROTO);
    }

    @Override
    public boolean appliesTo(String methodName, SoyType baseType) {
      var fieldName = getGetOrUndefinedFieldName(methodName);
      return fieldName.isPresent()
          && appliesToProto(fieldName.get(), baseType, this::acceptFieldDescriptor);
    }

    private boolean acceptFieldDescriptor(FieldDescriptor fd) {
      if (fd.isExtension() || fd.isRepeated()) {
        return false;
      }
      if (fd.getJavaType() == JavaType.MESSAGE) {
        // Jspb doesn't have OrUndefined accessors for messages, so neither does soy.
        return false;
      }
      return fd.hasPresence();
    }

    @Override
    public SoyType getReturnType(
        String methodName,
        SoyType baseType,
        List<ExprNode> params,
        SoyTypeRegistry soyTypeRegistry,
        ErrorReporter errorReporter) {
      return SoyTypes.makeUndefinable(
          computeTypeForProtoFieldName(
              baseType, getGetOrUndefinedFieldName(methodName).get(), soyTypeRegistry));
    }

    @Override
    ImmutableCollection<String> expandMethodNames(SoyType baseType, List<SoyType> argTypes) {
      return expandMethodNamesForProto(
          baseType, this::acceptFieldDescriptor, BuiltinMethod::fieldToGetOrUndefinedMethodName);
    }
  },

  GET_READONLY_PROTO_FIELD("getReadonly[X]", 0) {
    @Override
    public boolean appliesToBase(SoyType baseType) {
      Preconditions.checkArgument(!SoyTypes.isNullish(baseType));
      return SoyTypes.isKindOrUnionOfKind(baseType, SoyType.Kind.PROTO);
    }

    @Override
    public boolean appliesTo(String methodName, SoyType baseType) {
      var fieldName = getGetReadonlyFieldName(methodName);
      return fieldName.isPresent()
          && appliesToProto(fieldName.get(), baseType, this::acceptFieldDescriptor);
    }

    private boolean acceptFieldDescriptor(FieldDescriptor fd) {
      if (fd.isExtension() || fd.isRepeated()) {
        return false;
      }
      return fd.getJavaType() == JavaType.MESSAGE;
    }

    @Override
    public SoyType getReturnType(
        String methodName,
        SoyType baseType,
        List<ExprNode> params,
        SoyTypeRegistry soyTypeRegistry,
        ErrorReporter errorReporter) {
      return SoyTypes.tryRemoveNullish(
          computeTypeForProtoFieldName(
              baseType, getGetReadonlyFieldName(methodName).get(), soyTypeRegistry));
    }

    @Override
    ImmutableCollection<String> expandMethodNames(SoyType baseType, List<SoyType> argTypes) {
      return expandMethodNamesForProto(
          baseType, this::acceptFieldDescriptor, BuiltinMethod::protoFieldToGetReadonlyMethodName);
    }
  },

  /** Soy method that gets a single value of a map. */
  MAP_GET("get", 1) {

    @Override
    public boolean appliesToBase(SoyType baseType) {
      Preconditions.checkArgument(!SoyTypes.isNullish(baseType));
      return SoyTypes.isKindOrUnionOfKind(baseType, SoyType.Kind.MAP);
    }

    @Override
    public SoyType getReturnType(
        String methodName,
        SoyType baseType,
        List<ExprNode> params,
        SoyTypeRegistry soyTypeRegistry,
        ErrorReporter errorReporter) {
      Preconditions.checkArgument(!SoyTypes.isNullish(baseType));
      SoyType keyType = SoyTypes.getMapKeysType(baseType);

      ExprNode arg = params.get(0);
      if (baseType.equals(MapType.EMPTY_MAP)) {
        errorReporter.report(arg.getParent().getSourceLocation(), EMPTY_MAP_ACCESS);
      } else if (!keyType.isAssignableFromLoose(arg.getType())) {
        // TypeScript allows get with 'any' typed key.
        errorReporter.report(
            arg.getSourceLocation(), METHOD_INVALID_PARAM_TYPES, "get", arg.getType(), keyType);
      }

      if (baseType.equals(MapType.EMPTY_MAP)) {
        return NullType.getInstance();
      }
      return SoyTypes.makeUndefinable(SoyTypes.getMapValuesType(baseType));
    }
  },

  BIND("bind", 1) {

    @Override
    public boolean appliesToBase(SoyType baseType) {
      Preconditions.checkArgument(!SoyTypes.isNullish(baseType));
      return SoyTypes.isKindOrUnionOfKinds(
          baseType, ImmutableSet.of(SoyType.Kind.TEMPLATE, SoyType.Kind.TEMPLATE_TYPE));
    }

    @Override
    public SoyType getReturnType(
        String methodName,
        SoyType baseType,
        List<ExprNode> params,
        SoyTypeRegistry soyTypeRegistry,
        ErrorReporter errorReporter) {
      Preconditions.checkArgument(!SoyTypes.isNullish(baseType));
      Preconditions.checkArgument(params.size() == 1);
      ExprNode param = params.get(0);
      if (param.getKind() != ExprNode.Kind.RECORD_LITERAL_NODE) {
        errorReporter.report(param.getSourceLocation(), BIND_PARAMETER_MUST_BE_RECORD_LITERAL);
        return UnknownType.getInstance();
      }
      return TemplateBindingUtil.bindParameters(
          baseType,
          (RecordType) param.getType(),
          soyTypeRegistry,
          errorReporter.bind(param.getSourceLocation()));
    }
  };

  private static final SoyErrorKind GET_EXTENSION_BAD_ARG =
      SoyErrorKind.of(
          "The parameter of method ''getExtension'' must be an imported extension symbol.");
  private static final SoyErrorKind PROTO_EXTENSION_DOES_NOT_EXIST =
      SoyErrorKind.of(
          "Proto extension ''{0}'' does not extend ''{1}''.{2}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind
      GET_READONLY_EXTENSION_MAY_ONLY_BE_CALLED_ON_MESSAGE_EXTENSIONS =
          SoyErrorKind.of(
              "getReadonlyExtension may only be called on singular message extensions.",
              StyleAllowance.NO_CAPS);
  private static final SoyErrorKind HAS_EXTENSION_MAY_ONLY_BE_CALLED_ON_SINGULAR_EXTENSIONS =
      SoyErrorKind.of(
          "hasExtension may only be called on singular extensions.", StyleAllowance.NO_CAPS);
  private static final SoyErrorKind BIND_PARAMETER_MUST_BE_RECORD_LITERAL =
      SoyErrorKind.of("Parameter to bind() must be a record literal.");
  public static final SoyErrorKind METHOD_INVALID_PARAM_TYPES =
      SoyErrorKind.of("Method ''{0}'' called with parameter types ({1}) but expected ({2}).");
  private static final SoyErrorKind EMPTY_MAP_ACCESS =
      SoyErrorKind.of("Accessing item in empty map.");

  public static final SoyMethod.Registry REGISTRY =
      new Registry() {
        @Override
        public ImmutableList<? extends SoyMethod> matchForNameAndBase(
            String methodName, SoyType baseType) {
          return Arrays.stream(values())
              .filter(m -> m.appliesTo(methodName, baseType))
              .collect(toImmutableList());
        }

        @Override
        public ImmutableListMultimap<SoyMethod, String> matchForBaseAndArgs(
            SoyType baseType, List<SoyType> argTypes) {
          return Arrays.stream(values())
              .filter(m -> m.appliesToBase(baseType) && m.appliesToArgs(argTypes))
              .collect(
                  flatteningToImmutableListMultimap(
                      m -> m, m -> m.expandMethodNames(baseType, argTypes).stream()));
        }
      };

  public static String getProtoFieldNameFromMethodCall(MethodCallNode node) {
    var methodName = node.getMethodName().identifier();
    switch (((BuiltinMethod) node.getSoyMethod())) {
      case HAS_PROTO_FIELD:
        return getHasserFieldName(methodName).get();
      case GET_PROTO_FIELD:
        return getGetterFieldName(methodName).get();
      case GET_PROTO_FIELD_OR_UNDEFINED:
        return getGetOrUndefinedFieldName(methodName).get();
      case GET_READONLY_PROTO_FIELD:
        return getGetReadonlyFieldName(methodName).get();
      case GET_EXTENSION:
      case GET_READONLY_EXTENSION:
      case HAS_EXTENSION:
      case MAP_GET:
      case BIND:
        break;
    }
    throw new AssertionError("not a proto getter: " + node.getSoyMethod());
  }

  public static String getProtoExtensionIdFromMethodCall(MethodCallNode node) {
    ExprNode arg = node.getChild(1);
    return ((ProtoExtensionImportType) arg.getType()).getFieldName();
  }

  protected ImmutableCollection<String> expandMethodNamesForProto(
      SoyType baseType,
      Predicate<FieldDescriptor> acceptField,
      Function<String, String> fieldToMethodName) {
    if (!appliesToBase(baseType)) {
      return ImmutableList.of();
    }
    // If a union, we can pick any one of the types and look at all field names, as long as we check
    // appliesTo with the entire union baseType.
    SoyProtoType protoType = (SoyProtoType) SoyTypes.expandUnions(baseType).get(0);
    return protoType.getFieldNames().stream()
        .filter(name -> acceptField.test(protoType.getFieldDescriptor(name)))
        .map(fieldToMethodName)
        .collect(toImmutableSet());
  }

  protected boolean appliesToProto(
      String fieldName, SoyType baseType, Predicate<FieldDescriptor> acceptField) {
    if (!appliesToBase(baseType)) {
      return false;
    }
    for (SoyType type : SoyTypes.expandUnions(baseType)) {
      SoyProtoType protoType = (SoyProtoType) type;
      if (!protoType.getFieldNames().contains(fieldName)
          || !acceptField.test(protoType.getFieldDescriptor(fieldName))) {
        return false;
      }
    }
    return true;
  }

  private static String fieldToHasMethodName(String fieldName) {
    return "has" + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, fieldName);
  }

  public static String protoFieldToGetMethodName(String fieldName) {
    return "get" + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, fieldName);
  }

  public static String protoFieldToGetReadonlyMethodName(String fieldName) {
    return "getReadonly" + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, fieldName);
  }

  public static String fieldToGetOrUndefinedMethodName(String fieldName) {
    return "get" + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, fieldName) + "OrUndefined";
  }

  private final String name;
  private final int argCount;

  BuiltinMethod(String name, int argCount) {
    this.name = name;
    this.argCount = argCount;
  }

  public boolean appliesTo(String methodName, SoyType baseType) {
    return methodName.equals(name) && appliesToBase(baseType);
  }

  protected abstract boolean appliesToBase(SoyType baseType);

  public abstract SoyType getReturnType(
      String methodName,
      SoyType baseType,
      List<ExprNode> params,
      SoyTypeRegistry soyTypeRegistry,
      ErrorReporter errorReporter);

  public final SoyType getReturnType(
      MethodCallNode node, SoyTypeRegistry soyTypeRegistry, ErrorReporter errorReporter) {
    String methodName = node.getMethodName().identifier();
    return getReturnType(
        methodName,
        node.getBaseType(/* nullSafe= */ true),
        node.getParams(),
        soyTypeRegistry,
        errorReporter);
  }

  @Override
  public int getNumArgs() {
    return argCount;
  }

  @Override
  public boolean appliesToArgs(List<SoyType> argTypes) {
    return argTypes.size() == getNumArgs();
  }

  /**
   * Returns the list of identifiers by which this method might be called. Methods like {@link
   * #HAS_PROTO_FIELD} need to consult the `baseType` to calculate what method names are available.
   */
  ImmutableCollection<String> expandMethodNames(SoyType baseType, List<SoyType> argTypes) {
    return ImmutableList.of(name);
  }

  public List<String> getProtoDependencyTypes(MethodCallNode methodNode) {
    switch (this) {
      case GET_EXTENSION:
      case HAS_EXTENSION:
      case GET_READONLY_EXTENSION:
        return ImmutableList.of(
            ProtoUtils.getQualifiedOuterClassname(
                ((SoyProtoType) SoyTypes.tryRemoveNullish(methodNode.getBaseExprChild().getType()))
                    .getFieldDescriptor(getProtoExtensionIdFromMethodCall(methodNode))));
      case HAS_PROTO_FIELD:
      case GET_PROTO_FIELD:
      case GET_PROTO_FIELD_OR_UNDEFINED:
      case GET_READONLY_PROTO_FIELD:
      case MAP_GET:
      case BIND:
        return ImmutableList.of();
    }
    throw new AssertionError(this);
  }

  private static Optional<String> getGetReadonlyFieldName(String methodName) {
    // starts with getReadonly
    if (!methodName.startsWith("getReadonly")) {
      return Optional.empty();
    }
    // Followed by a capital
    String suffix = methodName.substring("getReadonly".length());
    if (suffix.length() > 0 && !Ascii.isUpperCase(suffix.charAt(0))) {
      return Optional.empty();
    }
    return Optional.of(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, suffix));
  }

  private static Optional<String> getHasserFieldName(String methodName) {
    if (!methodName.startsWith("has")) {
      return Optional.empty();
    }
    String suffix = methodName.substring(3);
    if (suffix.length() > 0 && !Ascii.isUpperCase(suffix.charAt(0))) {
      return Optional.empty();
    }
    return Optional.of(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, suffix));
  }

  private static Optional<String> getGetterFieldName(String methodName) {
    if (methodName.length() <= 3) {
      return Optional.empty();
    }

    if (!methodName.startsWith("get") || methodName.endsWith("OrUndefined")) {
      return Optional.empty();
    }
    String suffix = methodName.substring(3);
    if (suffix.length() > 0 && !Ascii.isUpperCase(suffix.charAt(0))) {
      return Optional.empty();
    }
    return Optional.of(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, suffix));
  }

  private static Optional<String> getGetOrUndefinedFieldName(String methodName) {
    if (!methodName.startsWith("get") || !methodName.endsWith("OrUndefined")) {
      return Optional.empty();
    }
    String middle = methodName.substring(3, methodName.length() - "OrUndefined".length());
    if (middle.length() > 0 && !Ascii.isUpperCase(middle.charAt(0))) {
      return Optional.empty();
    }
    return Optional.of(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, middle));
  }

  private static SoyType computeTypeForProtoFieldName(
      SoyType baseType, String fieldName, SoyTypeRegistry soyTypeRegistry) {
    ImmutableList<SoyType> types =
        SoyTypes.expandUnions(baseType).stream()
            .map(type -> ((SoyProtoType) type).getFieldType(fieldName))
            .collect(toImmutableList());
    return SoyTypes.computeLowestCommonType(soyTypeRegistry, types);
  }

  private static boolean isExtendableMessageType(SoyType baseType) {
    Preconditions.checkArgument(!SoyTypes.isNullish(baseType));
    return baseType.getKind() == SoyType.Kind.PROTO
        && ((SoyProtoType) baseType).getDescriptor().isExtendable();
  }

  private static Optional<ProtoExtensionImportType> getExtensionType(
      SoyProtoType protoType, ExprNode param, ErrorReporter errorReporter) {

    if (param.getType().getKind() != SoyType.Kind.PROTO_EXTENSION) {
      if (param.getType().getKind() != SoyType.Kind.UNKNOWN) {
        // Bad refs or typos are Kind=UNKNOWN and will be an error elsewhere.
        errorReporter.report(param.getSourceLocation(), GET_EXTENSION_BAD_ARG);
      }
      return Optional.empty();
    }

    ProtoExtensionImportType extType = (ProtoExtensionImportType) param.getType();
    // TODO(jcg): Have SoyProtoType understand ProtoExtensionImportType rather than looking up
    //            on string representation.
    ImmutableSet<String> fields = protoType.getExtensionFieldNames();
    String fieldName = extType.getFieldName();
    if (!fields.contains(fieldName)) {
      String extraErrorMessage =
          SoyErrors.getDidYouMeanMessageForProtoFields(
              fields, protoType.getDescriptor(), fieldName);
      errorReporter.report(
          param.getSourceLocation(),
          PROTO_EXTENSION_DOES_NOT_EXIST,
          fieldName,
          protoType.getDescriptor().getFullName(),
          extraErrorMessage);
      return Optional.empty();
    }
    return Optional.of(extType);
  }
}
