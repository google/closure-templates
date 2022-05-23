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
package com.google.template.soy.types;

import static com.google.common.base.Functions.identity;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.soytree.FunctionTypeP;
import com.google.template.soy.soytree.SoyTypeP;
import java.util.Collection;
import java.util.Map;

/** Function type, containing a list of named, typed parameters and a return type. */
@AutoValue
public abstract class FunctionType extends SoyType {

  public static FunctionType of(Collection<Parameter> parameters, SoyType returnType) {
    return new AutoValue_FunctionType(returnType, ImmutableList.copyOf(parameters));
  }

  public abstract SoyType getReturnType();

  public abstract ImmutableList<Parameter> getParameters();

  public final ImmutableMap<String, SoyType> getParameterMap() {
    return getParameters().stream().collect(toImmutableMap(Parameter::getName, Parameter::getType));
  }

  /**
   * Represents minimal information about a template parameter.
   *
   * <p>This only represents normal parameters. Information about injected params or state variables
   * is not recorded.
   */
  @AutoValue
  public abstract static class Parameter {

    public static Parameter of(String name, SoyType type) {
      return new AutoValue_FunctionType_Parameter(name, type);
    }

    public abstract String getName();

    public abstract SoyType getType();
  }

  @Override
  public final Kind getKind() {
    return Kind.FUNCTION;
  }

  @Override
  final boolean doIsAssignableFromNonUnionType(
      SoyType srcType, UnknownAssignmentPolicy unknownPolicy) {
    if (srcType.getKind() != Kind.FUNCTION) {
      return false;
    }

    FunctionType srcFunction = (FunctionType) srcType;

    Map<String, Parameter> thisParams =
        getParameters().stream().collect(toImmutableMap(Parameter::getName, identity()));
    Map<String, Parameter> srcParams =
        srcFunction.getParameters().stream()
            .collect(toImmutableMap(Parameter::getName, identity()));

    // The source template type's arguments must be a superset of this type's arguments (possibly
    // containing some optional parameters omitted from this type).
    for (Parameter thisParam : getParameters()) {
      if (!srcParams.containsKey(thisParam.getName())) {
        return false;
      }
    }

    for (Parameter srcParam : srcFunction.getParameters()) {
      Parameter thisParam = thisParams.get(srcParam.getName());
      // Check that each argument of the source type is assignable FROM the corresponding
      // argument of this type. This is because the parameter types are constraints; assignability
      // of a template type is only possible when the constraints of the from-type are narrower.
      if (!srcParam.getType().isAssignableFromInternal(thisParam.getType(), unknownPolicy)) {
        return false;
      }
    }
    return this.getReturnType().isAssignableFromStrict(srcFunction.getReturnType());
  }

  @Override
  public final String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("(");
    boolean first = true;
    for (Parameter parameter : getParameters()) {
      if (first) {
        first = false;
      } else {
        sb.append(", ");
      }
      String name = parameter.getName();
      sb.append(name);
      sb.append(": ");
      sb.append(parameter.getType());
    }
    sb.append(") => ");
    sb.append(getReturnType());
    return sb.toString();
  }

  @Override
  final void doToProto(SoyTypeP.Builder builder) {
    FunctionTypeP.Builder templateBuilder =
        builder.getFunctionBuilder().setReturnType(getReturnType().toProto());
    for (Parameter parameter : getParameters()) {
      templateBuilder.addParameters(
          FunctionTypeP.Parameter.newBuilder()
              .setName(parameter.getName())
              .setType(parameter.getType().toProto()));
    }
  }

  @Override
  public final <T> T accept(SoyTypeVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
