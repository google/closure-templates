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
package com.google.template.soy.jbcsrc.shared;

import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.filterArguments;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.permuteArguments;
import static java.lang.invoke.MethodType.methodType;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueProvider;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Bootstrap methods for adapting map style calls to templates using positional parameters
 *
 * <p>Implemented as invokedynamic to avoid the redundant and likely unused gencode.
 */
public final class RecordToPositionalCallFactory {

  /**
   * A JVM bootstrap method for adapting positional {@code render} methods to the {@link
   * CompiledTemplate} interface. This is used for implementing the {@code static Compiledtemplate
   * template()} methods.
   *
   * <p>This roughly generates code that looks like
   *
   * <pre>{@code
   * render(data.getFieldProvider("p1")...data.getFieldProvider("pN"), ij, appendable, ctx)
   * }</pre>
   *
   * where {@code p1...pN} are the param names passed to this template and {@code render} is the
   * {@code implMethod}
   *
   * @param lookup An object that allows us to resolve classes/methods in the context of the
   *     callsite. Provided automatically by invokeDynamic JVM infrastructure
   * @param name The name of the invokeDynamic method being called. This is provided by
   *     invokeDynamic JVM infrastructure and currently unused.
   * @param type The type of the method being called. This will be the method signature of the
   *     callsite we produce. Provided automatically by invokeDynamic JVM infrastructure. For this
   *     method it is always (RenderContext)->CompiledTemplate.
   * @param implMethod The method implementing the template.
   * @param paramNames The sequence of parameter names of the implMethod, used to extract fields
   *     from the record.
   */
  public static CallSite bootstrapDelegate(
      MethodHandles.Lookup lookup,
      String name,
      MethodType type,
      MethodHandle implMethod,
      String... paramNames) {
    MethodHandle getFieldProvider = getFieldProviderHandle(lookup);
    // implMethod looks like
    // (SoyValueProvider1..SoyValueProviderN,SoyRecord,LoggingAdvisingAppendable,RenderContext)
    // we need to adapt it to (SoyRecord,SoyRecord,LoggingAdvisingAppendable,RenderContext)

    // First use the paramNames to generate a list of ordered SoyRecord->SoyValueProvider adapters
    MethodHandle[] argumentAdapters =
        stream(paramNames)
            .map(paramName -> insertArguments(getFieldProvider, 1, paramName))
            .toArray(MethodHandle[]::new);
    // handle is now (SoyRecord1...,SoyRecordN, SoyRecord,LoggingAdvisingAppendable,RenderContext)
    MethodHandle handle = filterArguments(implMethod, 0, argumentAdapters);
    if (argumentAdapters.length == 0) {
      return new ConstantCallSite(dropArguments(handle, 0, ImmutableList.of(SoyRecord.class)));
    } else if (argumentAdapters.length == 1) {
      return new ConstantCallSite(handle);
    } else {
      int[] reorder = new int[handle.type().parameterCount()];
      // We want to collapse all leading arguments and leave the trailing arguments in place
      // permuteArguments requires us to pass an array showing where every argument goes.
      // we want all leading arguments to collapse and all trailing arguments to persist
      int targetTypeArgument = 0;
      // because in java arrays are initialized to all zeros, we just need to put 1,2...N at the end
      // of the array starting at the end of the positional parameters.
      for (int i = paramNames.length - 1; i < handle.type().parameterCount(); i++) {
        reorder[i] = targetTypeArgument++;
      }
      return new ConstantCallSite(permuteArguments(handle, type, reorder));
    }
  }

  private static final MethodType GET_FIELD_PROVIDER_TYPE =
      methodType(SoyValueProvider.class, String.class);

  private static MethodHandle getFieldProviderHandle(MethodHandles.Lookup lookup) {
    try {
      return lookup.findVirtual(SoyRecord.class, "getFieldProvider", GET_FIELD_PROVIDER_TYPE);
    } catch (ReflectiveOperationException roe) {
      throw new AssertionError(roe);
    }
  }

  private RecordToPositionalCallFactory() {}
}
