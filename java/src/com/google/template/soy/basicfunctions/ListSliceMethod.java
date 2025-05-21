/*
 * Copyright 2020 Google Inc.
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
 * Soy method for creating a sublist of a list.
 *
 * <p>Usage: {@code list.slice(2)} or {@code list.slice(2, 4)}
 */
@SoyMethodSignature(
    name = "slice",
    baseType = "list<any>",
    value = {
      @Signature(returnType = "list<any>"),
      @Signature(parameterTypes = "number", returnType = "list<any>"),
      @Signature(
          parameterTypes = {"number", "number"},
          returnType = "list<any>")
    })
@SoyPureFunction
public class ListSliceMethod
    implements SoyJavaSourceFunction, SoyJavaScriptSourceFunction, SoyPythonSourceFunction {

  @Override
  public JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    return args.get(0).invokeMethod("slice", args.subList(1, args.size()));
  }

  @Override
  public PythonValue applyForPythonSource(
      PythonValueFactory factory, List<PythonValue> args, PythonPluginContext context) {
    return factory
        .global("runtime.list_slice")
        .call(
            args.get(0),
            args.size() > 1 ? args.get(1) : factory.constantNull(),
            args.size() == 3 ? args.get(2) : factory.constantNull());
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method LIST_SLICE_FN =
        JavaValueFactory.createMethod(
            BasicFunctionsRuntime.class,
            "listSlice",
            List.class,
            NumberData.class,
            NumberData.class);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    return factory.callStaticMethod(
        Methods.LIST_SLICE_FN,
        args.get(0),
        args.size() > 1 ? args.get(1) : factory.constant(0),
        args.size() == 3 ? args.get(2) : factory.constantNull());
  }
}
