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

package com.google.template.soy.jbcsrc.shared;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SoyValue;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Runtime type for function pointers. */
@AutoValue
public abstract class JbcSrcFunctionValue extends SoyValue {

  public static JbcSrcFunctionValue create(
      RenderContext renderContext, String className, String methodName) {
    MethodHandle methodHandle =
        renderContext.getTemplates().getExternMethod(className + "#" + methodName + "#*");
    if (methodHandle.type().parameterCount() > 0
        && methodHandle.type().parameterType(0).equals(RenderContext.class)) {
      methodHandle = MethodHandles.insertArguments(methodHandle, 0, renderContext);
    }
    return new AutoValue_JbcSrcFunctionValue(methodHandle);
  }

  public static JbcSrcFunctionValue create(
      RenderContext renderContext, Class<?> clazz, String methodName) {
    try {
      Method method = getOnlyStaticMethodNamed(clazz, methodName);
      method.setAccessible(true);
      MethodHandle methodHandle = MethodHandles.lookup().unreflect(method);
      if (method.getParameterTypes().length > 0
          && method.getParameterTypes()[0].equals(RenderContext.class)) {
        methodHandle = MethodHandles.insertArguments(methodHandle, 0, renderContext);
      }
      return new AutoValue_JbcSrcFunctionValue(methodHandle);
    } catch (ReflectiveOperationException e) {
      throw new VerifyException(e);
    }
  }

  abstract MethodHandle getHandle();

  public JbcSrcFunctionValue bind(ImmutableList<?> args) {
    if (args.isEmpty()) {
      return this;
    }
    return new AutoValue_JbcSrcFunctionValue(
        MethodHandles.insertArguments(getHandle(), 0, Iterables.toArray(args, Object.class)));
  }

  /** Must be boxed because we can't know whether this depends on an async extern. */
  public Object call(ImmutableList<?> args) throws Throwable {
    int paramCount = getHandle().type().parameterCount();
    if (args.size() > paramCount) {
      // Ignore extra arguments.
      args = args.subList(0, paramCount);
    }
    return getHandle().invokeWithArguments(args);
  }

  @Override
  public SoyValue checkNullishFunction() {
    return this;
  }

  @Override
  public final boolean coerceToBoolean() {
    return true;
  }

  @Override
  public final String coerceToString() {
    return String.format("** FOR DEBUGGING ONLY: %s **", toString());
  }

  @Override
  public final void render(LoggingAdvisingAppendable appendable) {
    throw new IllegalStateException(
        "Cannot print function types; this should have been caught during parsing.");
  }

  @Override
  public String getSoyTypeName() {
    return "function";
  }

  public static Method getOnlyStaticMethodNamed(Class<?> clazz, String name) {
    Method method = null;
    for (Method declaredMethod : clazz.getDeclaredMethods()) {
      if (Modifier.isStatic(declaredMethod.getModifiers())
          && declaredMethod.getName().equals(name)) {
        Preconditions.checkArgument(
            method == null, "Multiple methods %s %s", method, declaredMethod);
        method = declaredMethod;
      }
    }
    return Preconditions.checkNotNull(method);
  }
}
