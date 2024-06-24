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
import com.google.template.soy.shared.restricted.SoyPureFunction;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Soy function that converts a string to an integer.
 *
 * <p>This function accepts a single string or a string and a int radix. If just a single string is
 * given and it is a valid base 10 integer, then the function will return that integer. If a string
 * and a radix is given and the string is in the base of radix, and the radix is >2 and <36, then
 * the function will return a integer. Otherwise, it will return {@code null}.
 *
 * <p>Ex: <code>
 *   {parseInt('10') + 20}  // evaluates to 30
 *   {parseInt('10',2) + 20}  // evaluates to 22
 *   {parseInt('garbage') ?? -1}  // evaluates to -1
 * </code>
 */
@SoyPureFunction
@SoyFunctionSignature(
    name = "parseInt",
    value = {
      @Signature(
          parameterTypes = {"string"},
          // TODO(b/70946095): should be nullable
          returnType = "int"),
      @Signature(
          parameterTypes = {"string", "int|float|undefined"},
          returnType = "int|null")
    })
final class ParseIntFunction
    implements SoyJavaSourceFunction, SoyJavaScriptSourceFunction, SoyPythonSourceFunction {
  @Override
  public JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    // TODO(user): parseInt('123abc', 10) == 123; JS parseInt tries to parse as much as it can.

    return factory.callNamespaceFunction("soy", "soy.$$parseInt", args);
  }

  @Override
  public PythonValue applyForPythonSource(
      PythonValueFactory factory, List<PythonValue> args, PythonPluginContext context) {
    return factory
        .global("runtime.parse_int")
        .call(args.get(0), getRadixValue(args, factory.constant(10)));
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method PARSE_INT =
        JavaValueFactory.createMethod(
            BasicFunctionsRuntime.class, "parseInt", String.class, SoyValue.class);
  }

  private <T> T getRadixValue(List<T> args, T defaultValue) {
    return args.size() == 2 ? args.get(1) : defaultValue;
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    return factory.callStaticMethod(
        Methods.PARSE_INT, args.get(0), getRadixValue(args, factory.constantNull()));
  }
}
