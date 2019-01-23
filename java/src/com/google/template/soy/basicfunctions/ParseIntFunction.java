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
 * Soy function that converts a string to an integer.
 *
 * <p>This function accepts a single string. If the string is a valid base 10 integer, then the
 * function will return that integer. Otherwise, it will return {@code null}.
 *
 * <p>Ex: <code>
 *   {parseInt('10') + 20}  // evaluates to 30
 *   {parseInt('garbage') ?: -1}  // evaluates to -1
 * </code>
 */
@SoyPureFunction
@SoyFunctionSignature(
    name = "parseInt",
    value =
        @Signature(
            parameterTypes = {"string"},
            // TODO(b/70946095): should be nullable
            returnType = "int"))
public final class ParseIntFunction
    implements SoyJavaSourceFunction, SoyJavaScriptSourceFunction, SoyPythonSourceFunction {
  @Override
  public JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    // TODO(user): parseInt('123abc', 10) == 123; JS parseInt tries to parse as much as it can.
    return factory.callNamespaceFunction("soy", "soy.$$parseInt", args.get(0));
  }

  @Override
  public PythonValue applyForPythonSource(
      PythonValueFactory factory, List<PythonValue> args, PythonPluginContext context) {
    return factory.global("runtime.parse_int").call(args.get(0));
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method PARSE_INT =
        JavaValueFactory.createMethod(BasicFunctionsRuntime.class, "parseInt", String.class);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    return factory.callStaticMethod(Methods.PARSE_INT, args.get(0));
  }
}
