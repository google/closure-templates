/*
 * Copyright 2023 Google Inc.
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

import com.google.errorprone.annotations.Immutable;
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
 * Joins items with the specified delimiter, filtering out falsey values and returns an attribute
 * key/value pair.
 */
@Immutable
@SoyPureFunction
@SoyFunctionSignature(
    name = "buildAttr",
    value = {
      @Signature(
          parameterTypes = {"string", "string | css | bool | null | undefined"},
          returnType = "attributes"),
      @Signature(
          parameterTypes = {
            "string",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined"
          },
          returnType = "attributes"),
      @Signature(
          parameterTypes = {
            "string",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined"
          },
          returnType = "attributes"),
      @Signature(
          parameterTypes = {
            "string",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined"
          },
          returnType = "attributes"),
      @Signature(
          parameterTypes = {
            "string",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined"
          },
          returnType = "attributes"),
      @Signature(
          parameterTypes = {
            "string",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined"
          },
          returnType = "attributes"),
      @Signature(
          parameterTypes = {
            "string",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined"
          },
          returnType = "attributes"),
      @Signature(
          parameterTypes = {
            "string",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined"
          },
          returnType = "attributes"),
      @Signature(
          parameterTypes = {
            "string",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined"
          },
          returnType = "attributes"),
      @Signature(
          parameterTypes = {
            "string",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined",
            "string | css | bool | null | undefined"
          },
          returnType = "attributes")
    })
public final class BuildAttrFunction
    implements SoyJavaSourceFunction, SoyJavaScriptSourceFunction, SoyPythonSourceFunction {

  @Override
  public JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    return factory.callNamespaceFunction("soy", "soy.$$buildAttr", args);
  }

  @Override
  public PythonValue applyForPythonSource(
      PythonValueFactory factory, List<PythonValue> args, PythonPluginContext context) {
    return factory.global("runtime.build_attr").call(args);
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method BUILD_ATTR_FN =
        JavaValueFactory.createMethod(
            BasicFunctionsRuntime.class, "buildAttr", String.class, List.class);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    return factory.callStaticMethod(
        Methods.BUILD_ATTR_FN, args.get(0), factory.listOf(args.subList(1, args.size())));
  }
}
