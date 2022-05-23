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

package com.google.template.soy.invocationbuilders.javatypes;

import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.data.BaseSoyTemplateImpl.AbstractBuilder;
import com.google.template.soy.data.BaseSoyTemplateImpl.AbstractBuilderWithAccumulatorParameters;
import com.google.template.soy.data.SoyTemplateParam;
import java.lang.reflect.Method;

/**
 * Collection of Java members that can be used in generated code. Validates that the members exist
 * during code generation (before code compilation). Can also be used to abstract out where these
 * members reside.
 */
public final class CodeGenUtils {

  private CodeGenUtils() {}

  public static final Member SET_PARAM_INTERNAL =
      MethodImpl.method(AbstractBuilder.class, "setParamInternal");
  public static final Member ADD_TO_LIST_PARAM =
      MethodImpl.method(AbstractBuilderWithAccumulatorParameters.class, "addToListParam");
  public static final Member INIT_LIST_PARAM =
      MethodImpl.method(AbstractBuilderWithAccumulatorParameters.class, "initListParam");
  public static final Member AS_RECORD = castFunction("asRecord");

  public static final Member STANDARD_P =
      MethodImpl.fullyQualifiedMethod(SoyTemplateParam.class, "standard");
  public static final Member INDIRECT_P =
      MethodImpl.fullyQualifiedMethod(SoyTemplateParam.class, "indirect");
  public static final Member INJECTED_P =
      MethodImpl.fullyQualifiedMethod(SoyTemplateParam.class, "injected");

  static Member castFunction(String name) {
    return MethodImpl.method(AbstractBuilder.class, name);
  }

  /** A field or method that can be printed in code generation. */
  @Immutable
  public interface Member {
    @Override
    String toString();
  }

  @Immutable
  private static class MethodImpl implements Member {
    private final String name;

    private MethodImpl(java.lang.reflect.Method method, boolean qualified) {
      this.name = (qualified ? method.getDeclaringClass().getName() + "." : "") + method.getName();
    }

    private static MethodImpl method(Class<?> type, String methodName) {
      return new MethodImpl(findAnyMethod(type, methodName), /*qualified=*/ false);
    }

    private static MethodImpl fullyQualifiedMethod(Class<?> type, String methodName) {
      return new MethodImpl(findAnyMethod(type, methodName), /*qualified=*/ true);
    }

    @Override
    public String toString() {
      return name;
    }
  }

  private static Method findAnyMethod(Class<?> type, String methodName) {
    for (Method method : type.getDeclaredMethods()) {
      if (methodName.equals(method.getName())) {
        return method;
      }
    }
    throw new IllegalArgumentException("Can't find a method named: " + methodName + " in: " + type);
  }
}
