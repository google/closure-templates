/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import java.util.List;

final class IntegrationTestSourceFunctions {
  private IntegrationTestSourceFunctions() {}

  public static List<SoySourceFunction> sourceFunctions() {
    ImmutableList.Builder<SoySourceFunction> fns = ImmutableList.builder();
    for (Class<?> clazz : IntegrationTestSourceFunctions.class.getDeclaredClasses()) {
      if (SoyJavaSourceFunction.class.isAssignableFrom(clazz)) {
        try {
          fns.add(
              clazz.asSubclass(SoyJavaSourceFunction.class).getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException e) {
          throw new RuntimeException(e);
        }
      }
    }
    return fns.build();
  }

  @SoyFunctionSignature(name = "returnBidiDir", value = @Signature(returnType = "?"))
  static final class ReturnsBidiDir implements SoyJavaSourceFunction {
    @Override
    public JavaValue applyForJavaSource(
        JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
      return context.getBidiDir();
    }
  }

  @SoyFunctionSignature(name = "passesFewerParams", value = @Signature(returnType = "?"))
  static final class PassesFewerParams implements SoyJavaSourceFunction {
    @Override
    public JavaValue applyForJavaSource(
        JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
      return factory.callStaticMethod(IntegrationTestRuntime.METHOD);
    }
  }

  @SoyFunctionSignature(
      name = "passesMoreParams",
      value = @Signature(parameterTypes = "?", returnType = "?"))
  static final class PassesMoreParams implements SoyJavaSourceFunction {
    @Override
    public JavaValue applyForJavaSource(
        JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
      return factory.callStaticMethod(IntegrationTestRuntime.METHOD, args.get(0), args.get(0));
    }
  }

  @SoyFunctionSignature(
      name = "wrongSoyReturnTypeNoMethod",
      value = @Signature(parameterTypes = "string", returnType = "int"))
  static final class WrongSoyReturnTypeNoMethod implements SoyJavaSourceFunction {
    @Override
    public JavaValue applyForJavaSource(
        JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
      return args.get(0);
    }
  }

  @SoyFunctionSignature(
      name = "wrongSoyReturnTypeWithMethod",
      value = @Signature(returnType = "int"))
  static final class WrongSoyReturnTypeWithMethod implements SoyJavaSourceFunction {
    @Override
    public JavaValue applyForJavaSource(
        JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
      return factory.callStaticMethod(IntegrationTestRuntime.RETURN_STRING);
    }
  }

  @SoyFunctionSignature(name = "invalidReturnType", value = @Signature(returnType = "?"))
  static final class InvalidReturnType implements SoyJavaSourceFunction {
    @Override
    public JavaValue applyForJavaSource(
        JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
      return factory.callStaticMethod(IntegrationTestRuntime.RETURN_ITERABLE);
    }
  }

  @SoyFunctionSignature(
      name = "wrongParameterTypeSoyExpr",
      value =
          @Signature(
              parameterTypes = {"int", "string"},
              returnType = "?"))
  static final class WrongParameterTypeSoyExpr implements SoyJavaSourceFunction {
    @Override
    public JavaValue applyForJavaSource(
        JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
      return factory.callStaticMethod(IntegrationTestRuntime.ACCEPTS_INT, args.get(1));
    }
  }

  @SoyFunctionSignature(name = "wrongParameterTypeExpr", value = @Signature(returnType = "?"))
  static final class WrongParameterTypeExpr implements SoyJavaSourceFunction {
    @Override
    public JavaValue applyForJavaSource(
        JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
      return factory.callStaticMethod(IntegrationTestRuntime.ACCEPTS_INT, context.getBidiDir());
    }
  }

  @SoyFunctionSignature(name = "returnNull", value = @Signature(returnType = "?"))
  static final class ReturnNull implements SoyJavaSourceFunction {
    @Override
    public JavaValue applyForJavaSource(
        JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
      return null;
    }
  }

  @SoyFunctionSignature(name = "passesNull", value = @Signature(returnType = "?"))
  static final class PassesNull implements SoyJavaSourceFunction {
    @Override
    public JavaValue applyForJavaSource(
        JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
      return factory.callStaticMethod(IntegrationTestRuntime.ACCEPTS_INT, (JavaValue) null);
    }
  }

  @SoyFunctionSignature(name = "passesNullArray", value = @Signature(returnType = "?"))
  static final class PassesNullArray implements SoyJavaSourceFunction {
    @Override
    public JavaValue applyForJavaSource(
        JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
      return factory.callStaticMethod(IntegrationTestRuntime.ACCEPTS_INT, (JavaValue[]) null);
    }
  }

  @SoyFunctionSignature(name = "passesNullMethod", value = @Signature(returnType = "?"))
  static final class PassesNullMethod implements SoyJavaSourceFunction {
    @Override
    public JavaValue applyForJavaSource(
        JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
      return factory.callStaticMethod(null);
    }
  }

  @SoyFunctionSignature(name = "multipleProblems", value = @Signature(returnType = "?"))
  static final class MultipleProblems implements SoyJavaSourceFunction {
    @Override
    public JavaValue applyForJavaSource(
        JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
      return factory.callStaticMethod(
          IntegrationTestRuntime.RETURN_ITERABLE_AND_ACCEPT_INT, context.getBidiDir());
    }
  }
}
