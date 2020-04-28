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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.internal.proto.ProtoUtils;
import com.google.template.soy.shared.restricted.SoyMethod;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import java.util.Arrays;
import java.util.List;

/** Enum of built-in functions supported in Soy expressions. */
public enum BuiltinMethod implements SoyMethod {
  GET_EXTENSION("getExtension", 1) {
    @Override
    public boolean appliesToBase(SoyType baseType) {
      Preconditions.checkArgument(!SoyTypes.isNullable(baseType));
      return baseType.getKind() == SoyType.Kind.PROTO;
    }

    @Override
    public SoyType getReturnType(String methodName, SoyType baseType, List<ExprNode> params) {
      Preconditions.checkArgument(!SoyTypes.isNullable(baseType));
      Preconditions.checkArgument(params.size() == 1);
      SoyProtoType protoType = (SoyProtoType) baseType;
      ExprNode param = params.get(0);
      if (param instanceof GlobalNode) {
        String fieldName = ((GlobalNode) param).getName();
        if (protoType.getExtensionFieldNames().contains(fieldName)) {
          return protoType.getFieldType(fieldName);
        }
      }
      throw new IllegalArgumentException("Bad parameter " + param);
    }
  };

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
        public ImmutableList<? extends SoyMethod> matchForBaseAndArgs(
            SoyType baseType, List<SoyType> argTypes) {
          return Arrays.stream(values())
              .filter(m -> m.appliesToBase(baseType) && m.appliesToArgs(argTypes))
              .collect(toImmutableList());
        }
      };

  public static String getProtoExtensionIdFromMethodCall(MethodCallNode node) {
    return ((StringNode) node.getChild(1)).getValue();
  }

  private final String name;
  private final int argCount;

  BuiltinMethod(String name, int argCount) {
    this.name = name;
    this.argCount = argCount;
  }

  public final boolean appliesTo(String methodName, SoyType baseType) {
    return matchesName(methodName) && appliesToBase(baseType);
  }

  protected boolean matchesName(String methodName) {
    return methodName.equals(name);
  }

  protected abstract boolean appliesToBase(SoyType baseType);

  protected abstract SoyType getReturnType(
      String methodName, SoyType baseType, List<ExprNode> params);

  public final SoyType getReturnType(MethodCallNode node) {
    String methodName = node.getMethodName().identifier();
    return getReturnType(methodName, node.getBaseType(/* nullSafe= */ true), node.getParams());
  }

  @Override
  public int getNumArgs() {
    return argCount;
  }

  @Override
  public boolean appliesToArgs(List<SoyType> argTypes) {
    switch (this) {
      case GET_EXTENSION:
        // Custom validation in ResolveExpressionTypesPass.
        return argTypes.size() == 1;
    }
    throw new AssertionError(this);
  }

  @Override
  public String getMethodName() {
    return name;
  }

  public List<String> getProtoDependencyTypes(MethodCallNode methodNode) {
    switch (this) {
      case GET_EXTENSION:
        return ImmutableList.of(
            ProtoUtils.getQualifiedOuterClassname(
                ((SoyProtoType) SoyTypes.removeNull(methodNode.getBaseExprChild().getType()))
                    .getFieldDescriptor(getProtoExtensionIdFromMethodCall(methodNode))));
    }
    return ImmutableList.of();
  }
}
