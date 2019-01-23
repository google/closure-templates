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

import com.google.template.soy.data.SanitizedContent;
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
 * A function that determines if a given string contains another given string.
 *
 * <p><code>strContains(expr1, expr2)</code> requires <code>expr1</code> and <code>expr2</code> to
 * be of type string or {@link SanitizedContent}. It evaluates to <code>true</code> iff <code>expr1
 * </code> contains <code>expr2</code>. <code>strContains</code> is case sensitive.
 *
 */
@SoyFunctionSignature(
    name = "strContains",
    value =
        @Signature(
            returnType = "bool",
            parameterTypes = {"string", "string"}))
@SoyPureFunction
final class StrContainsFunction
    implements SoyJavaSourceFunction, SoyJavaScriptSourceFunction, SoyPythonSourceFunction {

  @Override
  public JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    return factory.callNamespaceFunction(
        "soy", "soy.$$strContains", args.get(0).coerceToString(), args.get(1).coerceToString());
  }

  @Override
  public PythonValue applyForPythonSource(
      PythonValueFactory factory, List<PythonValue> args, PythonPluginContext context) {
    return args.get(1).coerceToString().in(args.get(0).coerceToString());
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method STR_CONTAINS =
        JavaValueFactory.createMethod(
            BasicFunctionsRuntime.class, "strContains", SoyValue.class, String.class);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    return factory.callStaticMethod(
        Methods.STR_CONTAINS, args.get(0), args.get(1).coerceToSoyString());
  }
}
