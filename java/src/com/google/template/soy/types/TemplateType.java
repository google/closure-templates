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
package com.google.template.soy.types;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.Streams.stream;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.soytree.SoyTypeP;
import com.google.template.soy.types.SoyType.Kind;
import java.util.Objects;

/** Template type, containing a list of named, typed parameters and a return type. */
public final class TemplateType extends SoyType {

  /** The {name, type} pair that is a template argument. */
  @AutoValue
  public abstract static class Argument {
    public abstract String name();

    public abstract SoyType type();
  }

  private final ImmutableList<Argument> arguments;
  private final ImmutableMap<String, SoyType> argumentMap;
  private final SoyType returnType;

  private TemplateType(Iterable<Argument> arguments, SoyType returnType) {
    this.arguments = ImmutableList.copyOf(arguments);
    this.argumentMap = stream(arguments).collect(toImmutableMap(Argument::name, Argument::type));
    this.returnType = returnType;
  }

  public static Argument argumentOf(String name, SoyType type) {
    return new AutoValue_TemplateType_Argument(name, type);
  }

  public static TemplateType of(Iterable<Argument> arguments, SoyType returnType) {
    return new TemplateType(arguments, returnType);
  }

  @Override
  public Kind getKind() {
    return Kind.TEMPLATE;
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType srcType) {
    if (srcType.getKind() == Kind.TEMPLATE) {
      TemplateType srcTemplate = (TemplateType) srcType;
      // The source template must have the exact same template argument names, and each individual
      // argument type must be assignable, and the return type must be identical.
      if (!srcTemplate.argumentMap.keySet().equals(this.argumentMap.keySet())) {
        return false;
      }
      for (Argument srcArgument : srcTemplate.arguments) {
        SoyType thisArgumentType = this.argumentMap.get(srcArgument.name());
        // Check that each argument of the source type is assignable FROM the corresponding argument
        // of this type. This is because the parameter types are constraints; assignability of a
        // template type is only possible when the constraints of the from-type are narrower.
        if (!srcArgument.type().isAssignableFrom(thisArgumentType)) {
          return false;
        }
      }
      if (!srcTemplate.returnType.equals(this.returnType)) {
        return false;
      }
      return true;
    }
    return false;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("(");
    boolean first = true;
    for (Argument argument : arguments) {
      if (first) {
        first = false;
      } else {
        sb.append(", ");
      }
      sb.append(argument.name());
      sb.append(": ");
      sb.append(argument.type());
    }
    sb.append(") => ");
    sb.append(returnType);
    return sb.toString();
  }

  @Override
  void doToProto(SoyTypeP.Builder builder) {
    SoyTypeP.TemplateTypeP.Builder templateBuilder = builder.getTemplateBuilder();
    for (Argument argument : arguments) {
      templateBuilder.putArgument(argument.name(), argument.type().toProto());
    }
    templateBuilder.setReturnType(returnType.toProto().getPrimitive());
  }

  @Override
  public boolean equals(Object other) {
    return other != null
        && other.getClass() == this.getClass()
        && ((TemplateType) other).arguments.equals(arguments)
        && ((TemplateType) other).returnType.equals(returnType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.getClass(), arguments, returnType);
  }
}
