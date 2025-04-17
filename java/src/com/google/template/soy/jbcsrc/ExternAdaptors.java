/*
 * Copyright 2025 Google Inc.
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

package com.google.template.soy.jbcsrc;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLogicalPath;
import com.google.template.soy.base.internal.TypeReference;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.plugin.java.internal.SoyJavaExternFunction;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.shared.restricted.SoySourceFunctionMethod;
import com.google.template.soy.soytree.FileMetadata.Extern;
import com.google.template.soy.soytree.FileMetadata.Extern.JavaImpl;
import com.google.template.soy.soytree.FileMetadata.Extern.JavaImpl.MethodType;
import com.google.template.soy.types.FunctionType;
import com.google.template.soy.types.FunctionType.Parameter;
import com.google.template.soy.types.SoyType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

class ExternAdaptors {
  private ExternAdaptors() {}

  /**
   * Adapts {@link SoySourceFunctionMethod} to {@link Extern} if the method's implementation is a
   * {@link SoyJavaExternFunction}.
   */
  public static Optional<Extern> asExtern(
      SoySourceFunctionMethod soyMethod, List<SoyExpression> args) {
    SoySourceFunction impl = soyMethod.getImpl();
    if (impl instanceof SoyJavaExternFunction) {
      return Optional.of(
          asExtern(
              (SoyJavaExternFunction) impl,
              soyMethod.getMethodName(),
              soyMethod.getReturnType(),
              ImmutableList.<SoyType>builder()
                  .add(soyMethod.getBaseType())
                  .addAll(soyMethod.getParamTypes())
                  .build()));
    }
    return Optional.empty();
  }

  /**
   * Adapts {@link SoyJavaSourceFunction} to {@link Extern} if the function's implementation is a
   * {@link SoyJavaExternFunction}.
   */
  public static Optional<Extern> asExtern(
      SoyJavaSourceFunction fn,
      List<SoyExpression> args,
      SoyType returnType,
      ImmutableList<SoyType> paramTypes) {
    if (fn instanceof SoyJavaExternFunction) {
      return Optional.of(asExtern((SoyJavaExternFunction) fn, "unused", returnType, paramTypes));
    }
    return Optional.empty();
  }

  private static Extern asExtern(
      SoyJavaExternFunction impl,
      String methodName,
      SoyType returnType,
      ImmutableList<SoyType> argTypes) {
    Method method = impl.getExternJavaMethod();
    MethodType methodType;
    if (Modifier.isStatic(method.getModifiers())) {
      if (method.getDeclaringClass().isInterface()) {
        methodType = MethodType.STATIC_INTERFACE;
      } else {
        methodType = MethodType.STATIC;
      }
    } else {
      if (method.getDeclaringClass().isInterface()) {
        methodType = MethodType.INTERFACE;
      } else {
        methodType = MethodType.INSTANCE;
      }
    }

    FunctionType functionType =
        FunctionType.of(
            argTypes.stream().map(t -> Parameter.of("unused", t)).collect(toImmutableList()),
            returnType);
    JavaImpl java =
        new JavaImpl() {
          @Override
          public String className() {
            return method.getDeclaringClass().getName();
          }

          @Override
          public String method() {
            return method.getName();
          }

          @Override
          public TypeReference returnType() {
            return TypeReference.create(method.getGenericReturnType());
          }

          @Override
          public ImmutableList<TypeReference> paramTypes() {
            return Arrays.stream(method.getGenericParameterTypes())
                .map(TypeReference::create)
                .collect(toImmutableList());
          }

          @Override
          public MethodType type() {
            return methodType;
          }

          @Override
          public boolean instanceFromContext() {
            return false;
          }
        };
    return new Extern() {
      @Override
      public SourceLogicalPath getPath() {
        return null;
      }

      @Override
      public String getName() {
        return methodName;
      }

      @Override
      public FunctionType getSignature() {
        return functionType;
      }

      @Override
      public JavaImpl getJavaImpl() {
        return java;
      }

      @Override
      public boolean hasAutoImpl() {
        return false;
      }

      @Override
      public boolean isJavaAsync() {
        return false;
      }
    };
  }
}
