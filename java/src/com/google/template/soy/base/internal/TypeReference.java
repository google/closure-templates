/*
 * Copyright 2024 Google Inc.
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

package com.google.template.soy.base.internal;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.List;

/** A representation of a Java type, possibly parameterized. */
@AutoValue
public abstract class TypeReference {

  private static final TypeReference OBJECT = create("java.lang.Object");

  public static TypeReference create(String type) {
    return create(type, ImmutableList.of());
  }

  public static TypeReference create(String type, List<TypeReference> parameters) {
    if (type.equals("?")) {
      type = "java.lang.Object";
    }
    return new AutoValue_TypeReference(type, ImmutableList.copyOf(parameters));
  }

  public static TypeReference create(Type type) {
    if (type instanceof Class) {
      return TypeReference.create(((Class<?>) type).getName());
    } else if (type instanceof ParameterizedType) {
      ParameterizedType pType = (ParameterizedType) type;
      return TypeReference.create(
          ((Class<?>) pType.getRawType()).getName(),
          Arrays.stream(pType.getActualTypeArguments())
              .map(TypeReference::create)
              .collect(toImmutableList()));
    } else if (type instanceof WildcardType) {
      // Omit "? super" / "? extends" and match with CompiledJarsPluginSignatureReader$TypeVisitor
      WildcardType wt = (WildcardType) type;
      if (wt.getLowerBounds().length > 0) {
        return OBJECT;
      } else if (wt.getUpperBounds().length == 1) {
        return create(wt.getUpperBounds()[0]);
      }
    } else if (type instanceof TypeVariable<?>) {
      return OBJECT;
    }

    return TypeReference.create(type.toString());
  }

  /** The base type. */
  public abstract String className();

  /** The type parameters. */
  public abstract ImmutableList<TypeReference> parameters();

  public boolean isGeneric() {
    return !parameters().isEmpty();
  }

  public int arity() {
    return parameters().size();
  }

  public TypeReference getParameter(int i) {
    return parameters().get(i);
  }

  /** Returns true if `other` is the same as or a raw-types version of this. */
  public boolean isAssignableFrom(TypeReference other) {
    if (!className().equals(other.className())) {
      return false;
    }
    if (!other.isGeneric()) {
      return true;
    }
    if (arity() != other.arity()) {
      return false;
    }
    for (int i = 0; i < arity(); i++) {
      if (!getParameter(i).isAssignableFrom(other.getParameter(i))) {
        return false;
      }
    }
    return true;
  }

  public boolean isPrimitive() {
    String className = className();
    return className.indexOf('.') < 0 && Character.isLowerCase(className.charAt(0));
  }

  @Override
  public final String toString() {
    if (!isGeneric()) {
      return className();
    }
    return className() + "<" + Joiner.on(", ").join(parameters()) + ">";
  }
}
