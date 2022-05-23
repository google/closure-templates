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

package com.google.template.soy.plugin.java.restricted;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Primitives;

/**
 * The signature of a method. This allows identifying a method without requiring a compile-time
 * dependency on its declaring class.
 */
@AutoValue
public abstract class MethodSignature {

  private static final ImmutableMap<String, Class<?>> PRIMITIVE_TYPE_INDEX =
      Primitives.allPrimitiveTypes().stream().collect(toImmutableMap(Class::getName, c -> c));

  /**
   * The fully qualified class name of the method, as returned by Class.getName.
   *
   * <p>This is the equivalent of {@link java.lang.reflect.Method#getDeclaringClass
   * Method.getDeclaringClass}().{@link Class#getName getName}(), except it doesn't require a
   * compile-time dependency on the declaring class.
   */
  public abstract String fullyQualifiedClassName();

  /**
   * The name of the method.
   *
   * <p>This is the equivalent of {@link java.lang.reflect.Method#getName}, except it doesn't
   * require a compile-time dependency on the declaring class.
   */
  public abstract String methodName();

  /**
   * The return type of the method.
   *
   * <p>This is the equivalent of {@link java.lang.reflect.Method#getReturnType}, except it doesn't
   * require a compile-time dependency on the declaring class.
   */
  public abstract Class<?> returnType();

  /**
   * The arguments of the method.
   *
   * <p>This is the equivalent of {@link java.lang.reflect.Method#getParameterTypes}, except it
   * doesn't require a compile-time dependency on the declaring class.
   */
  public abstract ImmutableList<Class<?>> arguments();

  /** True if this signature refers to a method on an interface. */
  public abstract boolean inInterface();

  MethodSignature() {} // package-private cxtor to users can't manually instantiate this.

  /** Constructs a new MethodSignature for a method on a class. */
  public static MethodSignature create(
      String classFqn, String method, Class<?> returnType, Class<?>... args) {
    return new AutoValue_MethodSignature(
        classFqn, method, returnType, ImmutableList.copyOf(args), /* inInterface= */ false);
  }

  public static MethodSignature create(
      String classFqn, String method, String returnTypeName, String... argTypeNames)
      throws ClassNotFoundException {
    Class<?> returnType = forName(returnTypeName);
    Class<?>[] args = new Class<?>[argTypeNames.length];
    for (int i = 0; i < argTypeNames.length; i++) {
      args[i] = forName(argTypeNames[i]);
    }
    return create(classFqn, method, returnType, args);
  }

  /** Constructs a new MethodSignature for a method on an interface. */
  public static MethodSignature createInterfaceMethod(
      String classFqn, String method, Class<?> returnType, Class<?>... args) {
    return new AutoValue_MethodSignature(
        classFqn, method, returnType, ImmutableList.copyOf(args), /* inInterface= */ true);
  }

  public static Class<?> forName(String className) throws ClassNotFoundException {
    Class<?> primitive = PRIMITIVE_TYPE_INDEX.get(className);
    return primitive != null ? primitive : Class.forName(className);
  }
}
