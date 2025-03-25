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

package com.google.template.soy.sharedpasses.render;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Arrays.stream;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.FunctionalInterfaceUtil;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.soytree.ExternNode;
import com.google.template.soy.types.SoyType;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

/**
 * Adapts a {@link TofuFunctionValue} to an instance of a functional interface for passing to an
 * extern implementation.
 */
class FunctionAdapter {

  private static final MethodHandle VISIT_EXTERN;
  private static final MethodHandle ADAPT_TO_JAVA_VALUE;
  private static final MethodHandle CONVERT_ARGS;

  static {
    try {
      MethodHandles.Lookup lookup = MethodHandles.lookup();

      VISIT_EXTERN =
          lookup.findVirtual(
              EvalVisitor.class,
              "visitExtern",
              MethodType.methodType(
                  TofuJavaValue.class,
                  ExternNode.class,
                  ImmutableList.class,
                  ImmutableList.class,
                  SoyType.class,
                  SourceLocation.class,
                  boolean.class));

      ADAPT_TO_JAVA_VALUE =
          lookup.findStatic(
              TofuValueFactory.class,
              "adaptValueToJava",
              MethodType.methodType(
                  Object.class, TofuJavaValue.class, Class.class, SoyType.class, boolean.class));

      CONVERT_ARGS =
          lookup.findStatic(
              FunctionAdapter.class,
              "convertArgs",
              MethodType.methodType(ImmutableList.class, Object[].class));

    } catch (ReflectiveOperationException e) {
      throw new VerifyException(e);
    }
  }

  private final EvalVisitor evalVisitor;

  public FunctionAdapter(EvalVisitor evalVisitor) {
    this.evalVisitor = evalVisitor;
  }

  public Object adapt(TofuFunctionValue functionPtr, Class<?> iface) {
    Method functionalMethod = FunctionalInterfaceUtil.getMethod(iface);
    if (functionalMethod == null) {
      throw RenderException.create("Not a functional interface: " + iface);
    }

    // The number of arguments we expect callers to pass.
    int paramCount = functionPtr.getParamCount() - functionPtr.getBoundArgsCount();

    MethodHandle handle = VISIT_EXTERN.bindTo(evalVisitor);
    // handle = TofuJavaValue(TofuJavaValue.class, Extern.class, ImmutableList.class,
    //              ImmutableList.class, SoyType.class, SourceLocation.class, boolean.class)
    handle =
        MethodHandles.insertArguments(handle, 0, functionPtr.getImpl(), functionPtr.getBoundArgs());
    // handle = TofuJavaValue(ImmutableList.class, SoyType.class, SourceLocation.class,
    //              boolean.class)
    handle = MethodHandles.insertArguments(handle, 1, null, SourceLocation.UNKNOWN, true);
    // handle = TofuJavaValue(ImmutableList.class)

    MethodHandle passedArgs = CONVERT_ARGS.asCollector(Object[].class, paramCount);
    handle = MethodHandles.collectArguments(handle, 0, passedArgs);
    // handle = TofuJavaValue(Object * paramCount)

    MethodHandle filter =
        MethodHandles.insertArguments(
            ADAPT_TO_JAVA_VALUE, 1, functionalMethod.getReturnType(), null, true);
    handle = MethodHandles.filterReturnValue(handle, filter);
    // handle = Object(Object * paramCount)
    return MethodHandleProxies.asInterfaceInstance(iface, handle);
  }

  private static ImmutableList<TofuJavaValue> convertArgs(Object... args) {
    return stream(args)
        .map(o -> TofuJavaValue.forSoyValue(javaToSoy(o), SourceLocation.UNKNOWN))
        .collect(toImmutableList());
  }

  private static SoyValue javaToSoy(Object o) {
    return SoyValueConverter.INSTANCE.convert(o).resolve();
  }
}
