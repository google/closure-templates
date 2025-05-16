/*
 * Copyright 2022 Google Inc.
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

import com.google.template.soy.data.restricted.NumberData;
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
import com.google.template.soy.shared.restricted.SoyMethodSignature;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Soy method for removing all duplicate elements from a list.
 *
 * <p>Usage: {@code list.uniq()}
 */
@SoyMethodSignature(
    name = "flat",
    baseType = "list<any>",
    value = {
      // returnType is made intelligent in ResolveExpressionTypesPass
      @Signature(returnType = "list<?>"),
      @Signature(parameterTypes = "number", returnType = "list<?>")
    })
@SoyPureFunction
public class ListFlatMethod
    implements SoyJavaSourceFunction, SoyJavaScriptSourceFunction, SoyPythonSourceFunction {

  @Override
  public JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    if (args.size() == 1) {
      return factory.callNamespaceFunction("soy", "soy.$$listFlat", args.get(0));
    } else {
      return factory.callNamespaceFunction("soy", "soy.$$listFlat", args.get(0), args.get(1));
    }
  }

  @Override
  public PythonValue applyForPythonSource(
      PythonValueFactory factory, List<PythonValue> args, PythonPluginContext context) {
    if (args.size() == 1) {
      return factory.global("runtime.list_flat").call(args.get(0));
    } else {
      return factory.global("runtime.list_flat").call(args.get(0), args.get(1));
    }
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method LIST_FLAT_FN =
        JavaValueFactory.createMethod(BasicFunctionsRuntime.class, "listFlat", List.class);
    static final Method LIST_FLAT_ARG1_FN =
        JavaValueFactory.createMethod(
            BasicFunctionsRuntime.class, "listFlat", List.class, NumberData.class);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    if (args.size() == 1) {
      return factory.callStaticMethod(Methods.LIST_FLAT_FN, args.get(0));
    } else {
      return factory.callStaticMethod(Methods.LIST_FLAT_ARG1_FN, args.get(0), args.get(1));
    }
  }
}
