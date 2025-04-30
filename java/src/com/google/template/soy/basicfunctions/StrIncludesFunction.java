/*
 * Copyright 2012 Google Inc.
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

/** A function that determines if a given string contains another given string. */
@SoyMethodSignature(
    name = "includes",
    baseType = "string",
    value = {
      @Signature(parameterTypes = "string", returnType = "bool"),
      @Signature(
          parameterTypes = {"string", "float|int"},
          returnType = "bool")
    })
@SoyPureFunction
public final class StrIncludesFunction
    implements SoyJavaSourceFunction, SoyJavaScriptSourceFunction, SoyPythonSourceFunction {
  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method STR_CONTAINS =
        JavaValueFactory.createMethod(
            BasicFunctionsRuntime.class, "strContains", SoyValue.class, String.class);
    static final Method STR_CONTAINS_FROM_INDEX =
        JavaValueFactory.createMethod(
            BasicFunctionsRuntime.class,
            "strContainsFromIndex",
            String.class,
            String.class,
            NumberData.class);
  }

  @Override
  public JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    return args.get(0).invokeMethod("includes", args.subList(1, args.size()));
  }

  @Override
  public PythonValue applyForPythonSource(
      PythonValueFactory factory, List<PythonValue> args, PythonPluginContext context) {
    if (args.size() == 3) {
      return args.get(1)
          .coerceToString()
          .in(
              factory
                  .global("runtime.str_substring")
                  .call(args.get(0), args.get(2), factory.constantNull()));
    }
    return args.get(1).in(args.get(0));
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    if (args.size() == 3) {
      return factory.callStaticMethod(
          Methods.STR_CONTAINS_FROM_INDEX, args.get(0), args.get(1), args.get(2));
    }
    return factory.callStaticMethod(
        Methods.STR_CONTAINS, args.get(0), args.get(1).coerceToSoyString());
  }
}
