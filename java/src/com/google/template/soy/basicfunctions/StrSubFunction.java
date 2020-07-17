/*
 * Copyright 2013 Google Inc.
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
import com.google.template.soy.shared.restricted.SoyMethodSignature;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import java.lang.reflect.Method;
import java.util.List;

/**
 * A function that returns a substring of a given string.
 *
 * <p><code>strSub(expr1, expr2, expr3)</code> requires <code>expr1</code> to be of type string or
 * {@link com.google.template.soy.data.SanitizedContent} and <code>expr2</code> and <code>expr3
 * </code> to be of type integer. <code>expr3</code> is optional.
 *
 * <p>This function returns a new string that is a substring of <code>expr1</code>. The returned
 * substring begins at the index specified by <code>expr2</code>. If <code>expr3</code> is not
 * specified, the substring will extend to the end of <code>expr1</code>. Otherwise it will extend
 * to the character at index <code>expr3 - 1</code>.
 *
 */
@SoyFunctionSignature(
    name = "strSub",
    value = {
      @Signature(
          returnType = "string",
          parameterTypes = {"string", "int"}),
      @Signature(
          returnType = "string",
          parameterTypes = {"string", "int", "int"}),
    })
@SoyMethodSignature(
    name = "substring",
    baseType = "string",
    value = {
      @Signature(parameterTypes = "int", returnType = "string"),
      @Signature(
          parameterTypes = {"int", "int"},
          returnType = "string"),
    })
@SoyPureFunction
final class StrSubFunction
    implements SoyJavaSourceFunction, SoyJavaScriptSourceFunction, SoyPythonSourceFunction {

  @Override
  public JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    return args.get(0)
        .coerceToString()
        .invokeMethod("substring", args.subList(1, args.size()).toArray(new JavaScriptValue[0]));
  }

  @Override
  public PythonValue applyForPythonSource(
      PythonValueFactory factory, List<PythonValue> args, PythonPluginContext context) {
    // Coerce SanitizedContent args to strings.
    PythonValue str = args.get(0).coerceToString();
    return factory
        .global("runtime.str_substring")
        .call(str, args.get(1), args.size() == 3 ? args.get(2) : factory.constantNull());
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method STR_SUB_START =
        JavaValueFactory.createMethod(
            BasicFunctionsRuntime.class, "strSub", SoyValue.class, int.class);
    static final Method STR_SUB_START_END =
        JavaValueFactory.createMethod(
            BasicFunctionsRuntime.class, "strSub", SoyValue.class, int.class, int.class);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    if (args.size() == 2) {
      return factory.callStaticMethod(Methods.STR_SUB_START, args.get(0), args.get(1).asSoyInt());
    }
    return factory.callStaticMethod(
        Methods.STR_SUB_START_END, args.get(0), args.get(1).asSoyInt(), args.get(2).asSoyInt());
  }
}
