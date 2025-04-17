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
import com.google.template.soy.base.internal.FunctionalInterfaceUtil;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Runtime type for function pointers. */
@AutoValue
public abstract class JbcSrcFunctionValue extends SoyValue {

  private static final MethodHandle ADAPT_RETURN;
  private static final MethodHandle ADAPT_ARG_TO_SOY_VALUE;

  static {
    try {
      MethodHandles.Lookup lookup = MethodHandles.lookup();

      ADAPT_RETURN =
          lookup.findStatic(
              JbcSrcFunctionValue.class,
              "adaptReturn",
              MethodType.methodType(Object.class, Object.class, Class.class));
      ADAPT_ARG_TO_SOY_VALUE =
          lookup.findStatic(
              JbcSrcFunctionValue.class,
              "adaptArgToSoyValue",
              MethodType.methodType(SoyValue.class, Object.class, Class.class));
    } catch (ReflectiveOperationException e) {
      throw new VerifyException(e);
    }
  }

  public static JbcSrcFunctionValue create(MethodHandle methodHandle) {
    return new AutoValue_JbcSrcFunctionValue(methodHandle);
  }

  public abstract MethodHandle getHandle();

  public JbcSrcFunctionValue bind(ImmutableList<?> args) {
    if (args.isEmpty()) {
      return this;
    }
    return create(
        MethodHandles.insertArguments(
            getHandle(), hasRenderContext() ? 1 : 0, Iterables.toArray(args, Object.class)));
  }

  private boolean hasRenderContext() {
    return getHandle().type().parameterCount() > 0
        && getHandle().type().parameterType(0).equals(RenderContext.class);
  }

  public JbcSrcFunctionValue withRenderContext(RenderContext renderContext) {
    if (!hasRenderContext()) {
      return this;
    }
    return create(MethodHandles.insertArguments(getHandle(), 0, renderContext));
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

  public <T> T asInstance(Class<T> iface) {
    Method functionalMethod = FunctionalInterfaceUtil.getMethod(iface);
    if (functionalMethod == null) {
      throw new IllegalArgumentException("Not a functional interface: " + iface);
    }

    MethodHandle handle = getHandle();
    // This handle is a compiled extern method. So parameters and return type may be anything from
    // java primitives, to java objects, to soy values.
    //   (int, List, SoyValue): SoyValue

    int paramCount = functionalMethod.getParameterCount();
    MethodHandle[] argFilters = new MethodHandle[paramCount];
    for (int i = 0; i < paramCount; i++) {
      argFilters[i] = null;
      Class<?> expected = handle.type().parameterType(i);
      Class<?> provided = functionalMethod.getParameterTypes()[i];
      if (SoyValue.class.isAssignableFrom(expected) && !SoyValue.class.isAssignableFrom(provided)) {
        argFilters[i] = MethodHandles.insertArguments(ADAPT_ARG_TO_SOY_VALUE, 1, provided);
      }
    }
    handle = MethodHandles.filterArguments(handle, 0, argFilters);
    // If the handle previously had SoyValue parameters, now the parameters match the functional
    // interface params.
    //   (int, List, String): SoyValue

    MethodHandle returnFilter =
        MethodHandles.insertArguments(ADAPT_RETURN, 1, functionalMethod.getReturnType());
    returnFilter =
        returnFilter.asType(returnFilter.type().changeParameterType(0, handle.type().returnType()));
    handle = MethodHandles.filterReturnValue(handle, returnFilter);
    //   (int, List, String): long

    return MethodHandleProxies.asInterfaceInstance(iface, handle);
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
    return String.format("** FOR DEBUGGING ONLY: %s **", getHandle());
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
    return Preconditions.checkNotNull(method, "%s#%s", clazz.getName(), name);
  }

  /** Adapt return value of Soy extern to what Java expects. */
  public static Object adaptReturn(Object val, Class<?> returnType) {
    // TODO(b/407056315): This is probably incomplete.
    if (returnType == int.class || returnType == Integer.class) {
      return ((Number) val).intValue();
    }
    if (returnType == float.class || returnType == Float.class) {
      return ((Number) val).floatValue();
    }
    return val;
  }

  /** Adapt value passed to Java extern from what Java passes. */
  public static SoyValue adaptArgToSoyValue(Object val, Class<?> paramType) {
    return SoyValueConverter.INSTANCE.convert(val).resolve();
  }
}
