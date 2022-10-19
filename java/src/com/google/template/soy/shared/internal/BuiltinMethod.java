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
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.Descriptors.FileDescriptor.Syntax;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.internal.proto.ProtoUtils;
import com.google.template.soy.shared.restricted.SoyMethod;
import com.google.template.soy.types.BoolType;
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
import java.util.function.Function;
import java.util.function.Predicate;

/** Enum of built-in functions supported in Soy expressions. */
public enum BuiltinMethod implements SoyMethod {
  GET_EXTENSION("getExtension", 1) {

    @Override
    public boolean appliesToBase(SoyType baseType) {
      Preconditions.checkArgument(!SoyTypes.isNullable(baseType));
      return baseType.getKind() == SoyType.Kind.PROTO;
    }

    @Override
    public SoyType getReturnType(
        String methodName,
        SoyType baseType,
        List<ExprNode> params,
        SoyTypeRegistry soyTypeRegistry,
        ErrorReporter errorReporter) {
      Preconditions.checkArgument(!SoyTypes.isNullable(baseType));
      Preconditions.checkArgument(params.size() == 1);
      SoyProtoType protoType = (SoyProtoType) baseType;
      ExprNode param = params.get(0);

      if (param.getType().getKind() != SoyType.Kind.PROTO_EXTENSION) {
        if (param.getType().getKind() != SoyType.Kind.UNKNOWN) {
          // Bad refs or typos are Kind=UNKNOWN and will be an error elsewhere.
          errorReporter.report(param.getSourceLocation(), GET_EXTENSION_BAD_ARG);
        }
        return UnknownType.getInstance();
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
        return UnknownType.getInstance();
      }
      return protoType.getFieldType(fieldName);
    }
  },

  HAS_PROTO_FIELD("has[X]", 0) {
    @Override
    public boolean appliesToBase(SoyType baseType) {
      Preconditions.checkArgument(!SoyTypes.isNullable(baseType));
      return SoyTypes.isKindOrUnionOfKind(baseType, SoyType.Kind.PROTO);
    }

    @Override
    public boolean appliesTo(String methodName, SoyType baseType) {
      return matchesName(methodName)
          && appliesToProto(methodName, baseType, this::acceptFieldDescriptor);
    }

    private boolean acceptFieldDescriptor(FieldDescriptor fd) {
      if (fd.isExtension() || fd.isRepeated()) {
        return false;
      }
      if (ProtoUtils.getContainingOneof(fd) != null) {
        return true;
      }
      if (fd.getJavaType() == JavaType.MESSAGE) {
        return false;
      }
      if (fd.getFile().getSyntax() == Syntax.PROTO3) {
        return fd.hasOptionalKeyword();
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
      return BoolType.getInstance();
    }

    boolean matchesName(String methodName) {
      if (methodName.length() <= 3) {
        return false;
      }
      if (!methodName.startsWith("has")) {
        return false;
      }
      char firstChar = methodName.charAt(3);
      return Ascii.isUpperCase(firstChar);
    }

    @Override
    ImmutableCollection<String> expandMethodNames(SoyType baseType, List<SoyType> argTypes) {
      return expandMethodNamesForProto(baseType, BuiltinMethod::fieldToHasMethodName);
    }
  },

  GET_PROTO_FIELD("get[X]", 0) {
    @Override
    public boolean appliesToBase(SoyType baseType) {
      Preconditions.checkArgument(!SoyTypes.isNullable(baseType));
      return SoyTypes.isKindOrUnionOfKind(baseType, SoyType.Kind.PROTO);
    }

    @Override
    public boolean appliesTo(String methodName, SoyType baseType) {
      return matchesName(methodName)
          && appliesToProto(methodName, baseType, this::acceptFieldDescriptor);
    }

    private boolean acceptFieldDescriptor(FieldDescriptor fd) {
      if (fd.isExtension()) {
        // should be impossibe
        return false;
      }
      if (fd.isRepeated()) {
        // TODO(b/230787876): add support for repeated field and map getters when we are ready to
        // migrate
        return false;
      }
      if (fd.getJavaType() == JavaType.MESSAGE) {
        // TODO(b/230787876): add support for message getters when we are ready to migrate
        return false;
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
      String fieldName = methodToFieldName(methodName);
      ImmutableList<SoyType> types =
          SoyTypes.expandUnions(baseType).stream()
              .map(type -> ((SoyProtoType) type).getFieldType(fieldName))
              .collect(toImmutableList());
      return SoyTypes.computeLowestCommonType(soyTypeRegistry, types);
    }

    boolean matchesName(String methodName) {
      if (methodName.length() <= 3) {
        return false;
      }
      if (!methodName.startsWith("get") || methodName.endsWith("OrUndefined")) {
        return false;
      }
      char firstChar = methodName.charAt(3);
      return Ascii.isUpperCase(firstChar);
    }

    @Override
    ImmutableCollection<String> expandMethodNames(SoyType baseType, List<SoyType> argTypes) {
      return expandMethodNamesForProto(baseType, BuiltinMethod::protoFieldToGetMethodName);
    }
  },

  GET_PROTO_FIELD_OR_UNDEFINED("get[X]OrUndefined", 0) {
    @Override
    public boolean appliesToBase(SoyType baseType) {
      Preconditions.checkArgument(!SoyTypes.isNullable(baseType));
      return SoyTypes.isKindOrUnionOfKind(baseType, SoyType.Kind.PROTO);
    }

    @Override
    public boolean appliesTo(String methodName, SoyType baseType) {
      return matchesName(methodName)
          && appliesToProto(methodName, baseType, this::acceptFieldDescriptor);
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
      return SoyTypes.makeNullable(
          GET_PROTO_FIELD.getReturnType(
              methodName, baseType, params, soyTypeRegistry, errorReporter));
    }

    boolean matchesName(String methodName) {
      if (methodName.length() <= 14) {
        return false;
      }
      if (!methodName.startsWith("get") || !methodName.endsWith("OrUndefined")) {
        return false;
      }
      char firstChar = methodName.charAt(3);
      return Ascii.isUpperCase(firstChar);
    }

    @Override
    ImmutableCollection<String> expandMethodNames(SoyType baseType, List<SoyType> argTypes) {
      return expandMethodNamesForProto(baseType, BuiltinMethod::fieldToGetOrUndefinedMethodName);
    }
  },

  BIND("bind", 1) {

    @Override
    public boolean appliesToBase(SoyType baseType) {
      Preconditions.checkArgument(!SoyTypes.isNullable(baseType));
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
      Preconditions.checkArgument(!SoyTypes.isNullable(baseType));
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
  private static final SoyErrorKind BIND_PARAMETER_MUST_BE_RECORD_LITERAL =
      SoyErrorKind.of("Parameter to bind() must be a record literal.");

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
        public ImmutableMultimap<SoyMethod, String> matchForBaseAndArgs(
            SoyType baseType, List<SoyType> argTypes) {
          return Arrays.stream(values())
              .filter(m -> m.appliesToBase(baseType) && m.appliesToArgs(argTypes))
              .collect(
                  flatteningToImmutableListMultimap(
                      m -> m, m -> m.expandMethodNames(baseType, argTypes).stream()));
        }
      };

  public static String getProtoFieldNameFromMethodCall(MethodCallNode node) {
    return methodToFieldName(node.getMethodName().identifier());
  }

  public static String getProtoExtensionIdFromMethodCall(MethodCallNode node) {
    ExprNode arg = node.getChild(1);
    return ((ProtoExtensionImportType) arg.getType()).getFieldName();
  }

  protected ImmutableCollection<String> expandMethodNamesForProto(
      SoyType baseType, Function<String, String> fieldToMethodName) {
    if (!appliesToBase(baseType)) {
      return ImmutableList.of();
    }
    // If a union, we can pick any one of the types and look at all field names, as long as we check
    // appliesTo with the entire union baseType.
    SoyProtoType protoType = (SoyProtoType) SoyTypes.expandUnions(baseType).get(0);
    return protoType.getFieldNames().stream()
        .map(fieldToMethodName)
        .filter(methodName -> appliesTo(methodName, baseType))
        .collect(toImmutableSet());
  }

  protected boolean appliesToProto(
      String methodName, SoyType baseType, Predicate<FieldDescriptor> acceptField) {
    if (!appliesToBase(baseType)) {
      return false;
    }
    String fieldName = methodToFieldName(methodName);
    for (SoyType type : SoyTypes.expandUnions(baseType)) {
      SoyProtoType protoType = (SoyProtoType) type;
      if (!protoType.getFieldNames().contains(fieldName)
          || !acceptField.test(protoType.getFieldDescriptor(fieldName))) {
        return false;
      }
    }
    return true;
  }

  private static String methodToFieldName(String methodName) {
    Preconditions.checkArgument(
        methodName.length() >= 4 && (methodName.startsWith("get") || methodName.startsWith("has")));
    if (methodName.endsWith("OrUndefined")) {
      methodName = methodName.substring(3, methodName.length() - "OrUndefined".length());
    } else {
      methodName = methodName.substring(3);
    }
    return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, methodName);
  }

  private static String fieldToHasMethodName(String fieldName) {
    return "has" + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, fieldName);
  }

  public static String protoFieldToGetMethodName(String fieldName) {
    return "get" + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, fieldName);
  }

  private static String fieldToGetOrUndefinedMethodName(String fieldName) {
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

  protected abstract SoyType getReturnType(
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
        return ImmutableList.of(
            ProtoUtils.getQualifiedOuterClassname(
                ((SoyProtoType) SoyTypes.removeNull(methodNode.getBaseExprChild().getType()))
                    .getFieldDescriptor(getProtoExtensionIdFromMethodCall(methodNode))));
      case HAS_PROTO_FIELD:
      case GET_PROTO_FIELD:
      case GET_PROTO_FIELD_OR_UNDEFINED:
      case BIND:
        return ImmutableList.of();
    }
    throw new AssertionError(this);
  }
}
