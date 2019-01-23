/*
 * Copyright 2009 Google Inc.
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
 * Soy function that rounds a number to a specified number of digits before or after the decimal
 * point.
 *
 * <p>TODO(b/112835292): No one should use the 2 parameter overload, it is inaccurate because
 * floating point != decimal, instead they should use an i18n friendly number formatting routine. We
 * should deprecated the 2 argument overload by adding a new function {@code brokenRound()} and then
 * we can encourage people to migrate to a less broken approach. (or we could just add a pow
 * function and inline it).
 *
 */
@SoyFunctionSignature(
    name = "round",
    value = {
      // TODO(b/70946095): these should take number values and return either an int or a number
      @Signature(returnType = "?", parameterTypes = "?"),
      @Signature(
          returnType = "?",
          parameterTypes = {"?", "?"}),
    })
@SoyPureFunction
public final class RoundFunction
    implements SoyJavaSourceFunction, SoyJavaScriptSourceFunction, SoyPythonSourceFunction {

  @Override
  public JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    if (args.size() == 1) {
      return factory.global("Math").invokeMethod("round", args.get(0));
    }
    return factory.callNamespaceFunction("soy", "soy.$$round", args.get(0), args.get(1));
  }

  @Override
  public PythonValue applyForPythonSource(
      PythonValueFactory factory, List<PythonValue> args, PythonPluginContext context) {
    return factory
        .global("runtime.soy_round")
        .call(args.get(0), args.size() > 1 ? args.get(1) : factory.constant(0));
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method BOXED_ROUND_FN =
        JavaValueFactory.createMethod(BasicFunctionsRuntime.class, "round", SoyValue.class);

    static final Method BOXED_ROUND_WITH_NUM_DIGITS_AFTER_POINT_FN =
        JavaValueFactory.createMethod(
            BasicFunctionsRuntime.class, "round", SoyValue.class, int.class);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    if (args.size() == 1) {
      return factory.callStaticMethod(Methods.BOXED_ROUND_FN, args.get(0));
    } else {
      return factory.callStaticMethod(
          Methods.BOXED_ROUND_WITH_NUM_DIGITS_AFTER_POINT_FN, args.get(0), args.get(1).asSoyInt());
    }
  }
}
