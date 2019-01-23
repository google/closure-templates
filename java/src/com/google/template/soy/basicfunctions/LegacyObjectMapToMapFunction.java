/*
 * Copyright 2017 Google Inc.
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

package com.google.template.soy.basicfunctions;

import com.google.template.soy.data.SoyValue;
import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptPluginContext;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValue;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValueFactory;
import com.google.template.soy.plugin.javascript.restricted.SoyJavaScriptSourceFunction;
import com.google.template.soy.plugin.python.restricted.PythonPluginContext;
import com.google.template.soy.plugin.python.restricted.PythonValue;
import com.google.template.soy.plugin.python.restricted.PythonValueFactory;
import com.google.template.soy.plugin.python.restricted.SoyPythonSourceFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Converts values of type {@code legacy_object_map} to values of type {@code experimental_map}.
 *
 * <p>(This is the inverse of {@link MapToLegacyObjectMapFunction}.)
 *
 * <p>The two map types are designed to be incompatible in the Soy type system; the long-term plan
 * is to migrate all {@code legacy_object_map}s to {@code experimental_map}s, rename {@code
 * experimental_map} to {@code map}, and delete {@code legacy_object_map}. To allow template-level
 * migrations of {@code legacy_object_map} parameters to {@code experimental_map}, we need plugins
 * to convert between the two maps, so that converting one template doesn't require converting its
 * transitive callees.
 */
@SoyFunctionSignature(
    name = "legacyObjectMapToMap",
    // Note: the return type is overridden in ResolveExpressionTypePass
    value = @Signature(parameterTypes = "?", returnType = "?"))
public final class LegacyObjectMapToMapFunction
    implements SoyJavaSourceFunction, SoyPythonSourceFunction, SoyJavaScriptSourceFunction {

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method LEGACY_OBJECT_MAP_TO_MAP =
        JavaValueFactory.createMethod(
            BasicFunctionsRuntime.class, "legacyObjectMapToMap", SoyValue.class);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    return factory.callStaticMethod(Methods.LEGACY_OBJECT_MAP_TO_MAP, args.get(0));
  }

  @Override
  public JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    return factory.callModuleFunction("soy.newmaps", "$$legacyObjectMapToMap", args.get(0));
  }

  @Override
  public PythonValue applyForPythonSource(
      PythonValueFactory factory, List<PythonValue> args, PythonPluginContext context) {
    // TODO(b/69064788): The runtime representations of legacy_object_map and
    // experimental_map should be different in every backend, just as they are different in JS.
    // However, based on the low usage of pysrc and its existing incompatibilities, we are going
    // to try to complete the map migration without touching the pysrc implementation.
    // If this is feasible, there will be a brief period where legacy_object_map and map are wrongly
    // interoperable in pysrc in limited situations (the type checker will still rule out many
    // situations). If this turns out to be infeasible and we need two map types for a long time,
    // we will need to change pysrc after all.
    return args.get(0);
  }
}
