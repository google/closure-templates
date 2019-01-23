/*
 * Copyright 2011 Google Inc.
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

import com.google.template.soy.data.SoyMap;
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
import com.google.template.soy.shared.restricted.SoyPureFunction;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Soy function that gets the keys in a map.
 *
 * <p>The keys are returned as a list with no guarantees on the order (may be different on each run
 * or for each backend).
 *
 * <p>This enables iteration over the keys in a map, e.g. {@code {for $key in keys($myMap)} ...
 * {/for}}
 *
 */
@SoyFunctionSignature(
    name = "mapKeys",
    // Note: the return type is overridden in ResolveTypeExpressionsPass
    value = @Signature(parameterTypes = "map<?, any>", returnType = "list<?>"))
@SoyPureFunction
public final class MapKeysFunction
    implements SoyJavaSourceFunction, SoyJavaScriptSourceFunction, SoyPythonSourceFunction {

  @Override
  public JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    // TODO(lukes) this could be callModuleFunction but other parts of soy don't generate aliased
    // requires so we can't generate one here without create a 'multiple require' error
    // this could be handled via more clever require handling in the compiler.
    return factory.callNamespaceFunction("soy.map", "soy.map.$$getMapKeys", args.get(0));
  }

  @Override
  public PythonValue applyForPythonSource(
      PythonValueFactory factory, List<PythonValue> args, PythonPluginContext context) {
    return args.get(0).getProp("keys").call();
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method MAP_KEYS_FN =
        JavaValueFactory.createMethod(BasicFunctionsRuntime.class, "mapKeys", SoyMap.class);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    return factory.callStaticMethod(Methods.MAP_KEYS_FN, args.get(0));
  }
}
