/*
 * Copyright 2018 Google Inc.
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

import com.google.template.soy.data.SoyList;
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

/** Soy function that concatenates two or more lists together. */
@SoyMethodSignature(
    name = "concat",
    baseType = "list<any>",
    value = {
      // Note: These signatures exist solely to inform the # of parameters we allow.
      // The return type is overridden in ResolveExpressionTypePass.
      // ConcatLists would be varadic if soy allowed varadic functions. Instead we're giving the
      // function a high enough upper limit that it's close enough to being varadic in practice.
      @Signature(parameterTypes = "list<any>", returnType = "list<any>"),
      @Signature(
          parameterTypes = {"list<any>", "list<any>"},
          returnType = "list<any>"),
      @Signature(
          parameterTypes = {"list<any>", "list<any>", "list<any>"},
          returnType = "list<any>"),
      @Signature(
          parameterTypes = {"list<any>", "list<any>", "list<any>", "list<any>"},
          returnType = "list<any>"),
      @Signature(
          parameterTypes = {"list<any>", "list<any>", "list<any>", "list<any>", "list<any>"},
          returnType = "list<any>"),
      @Signature(
          parameterTypes = {
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>"
          },
          returnType = "list<any>"),
      @Signature(
          parameterTypes = {
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>"
          },
          returnType = "list<any>"),
      @Signature(
          parameterTypes = {
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>"
          },
          returnType = "list<any>"),
      @Signature(
          parameterTypes = {
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>"
          },
          returnType = "list<any>"),
      @Signature(
          parameterTypes = {
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>"
          },
          returnType = "list<any>"),
      @Signature(
          parameterTypes = {
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>",
            "list<any>"
          },
          returnType = "list<any>")
    })
@SoyPureFunction
public final class ConcatListsFunction
    implements SoyJavaSourceFunction, SoyJavaScriptSourceFunction, SoyPythonSourceFunction {

  @Override
  public JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    return args.get(0).invokeMethod("concat", args.subList(1, args.size()));
  }

  @Override
  public PythonValue applyForPythonSource(
      PythonValueFactory factory, List<PythonValue> args, PythonPluginContext context) {
    PythonValue accum = args.get(0);
    for (int i = 1; i < args.size(); i++) {
      accum = accum.plus(args.get(i));
    }
    return accum;
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method CONCAT_LISTS_FN_2 =
        JavaValueFactory.createMethod(
            BasicFunctionsRuntime.class, "concatLists", SoyList.class, SoyList.class);
    static final Method CONCAT_LISTS_FN_3 =
        JavaValueFactory.createMethod(
            BasicFunctionsRuntime.class,
            "concatLists",
            SoyList.class,
            SoyList.class,
            SoyList.class);
    static final Method CONCAT_LISTS_FN_MANY =
        JavaValueFactory.createMethod(
            BasicFunctionsRuntime.class, "concatLists", SoyList.class, List.class);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    // It is important to evaluate the first argument first as it is the 'receiver' and so may
    // contain a nullcheck,  e.g. `list?.concat(other)`
    switch (args.size()) {
      case 2:
        return factory.callStaticMethod(Methods.CONCAT_LISTS_FN_2, args.get(0), args.get(1));
      case 3:
        return factory.callStaticMethod(
            Methods.CONCAT_LISTS_FN_3, args.get(0), args.get(1), args.get(2));
      default:
        return factory.callStaticMethod(
            Methods.CONCAT_LISTS_FN_MANY,
            args.get(0),
            factory.listOf(args.subList(1, args.size())));
    }
  }
}
