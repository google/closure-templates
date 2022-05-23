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
package com.google.template.soy.types.ast;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;

/** Node representing a function type, e.g. (p1:string) => int. */
@AutoValue
public abstract class FunctionTypeNode extends TypeNode {

  public static FunctionTypeNode create(
      SourceLocation sourceLocation, Iterable<Parameter> parameters, TypeNode returnType) {
    return new AutoValue_FunctionTypeNode(
        sourceLocation, ImmutableList.copyOf(parameters), returnType);
  }

  /** A single named, typed parameter to a template. */
  @AutoValue
  public abstract static class Parameter {
    public static Parameter create(
        SourceLocation nameLocation, String name, String sourceName, TypeNode type) {
      return new AutoValue_FunctionTypeNode_Parameter(nameLocation, name, sourceName, type);
    }

    public abstract SourceLocation nameLocation();

    public abstract String name();

    public abstract String sourceName();

    public abstract TypeNode type();

    @Override
    public final String toString() {
      return sourceName() + ": " + type();
    }

    Parameter copy() {
      return create(nameLocation(), name(), sourceName(), type().copy());
    }
  }

  public abstract ImmutableList<Parameter> parameters();

  public abstract TypeNode returnType();

  @Override
  public final String toString() {
    if (parameters().size() < 3) {
      return "(" + Joiner.on(", ").join(parameters()) + ") => " + returnType();
    }
    return "(\n  " + Joiner.on(",\n  ").join(parameters()) + "\n) => " + returnType();
  }

  @Override
  public <T> T accept(TypeNodeVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public FunctionTypeNode copy() {
    ImmutableList.Builder<Parameter> newParameters = ImmutableList.builder();
    for (Parameter parameter : parameters()) {
      newParameters.add(parameter.copy());
    }
    FunctionTypeNode copy = create(sourceLocation(), newParameters.build(), returnType().copy());
    copy.copyResolvedTypeFrom(this);
    return copy;
  }
}
