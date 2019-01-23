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

package com.google.template.soy.bidifunctions;

import com.google.template.soy.data.SoyValue;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Soy function that maybe inserts a bidi mark character (LRM or RLM) for the current global bidi
 * directionality. The function requires the text string preceding the point where the bidi mark
 * character is to be inserted. If the preceding text string would change the bidi directionality
 * going forward, then the bidi mark is inserted to restore the global bidi directionality.
 * Otherwise, nothing is inserted.
 *
 */
@SoyFunctionSignature(
    name = "bidiMarkAfter",
    value = {
      // TODO(b/70946095): should take a string and a bool
      @Signature(returnType = "string", parameterTypes = "?"),
      @Signature(
          returnType = "string",
          parameterTypes = {"?", "?"}),
    })
final class BidiMarkAfterFunction
    implements SoyJavaSourceFunction, SoyJavaScriptSourceFunction, SoyPythonSourceFunction {

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method MARK_AFTER =
        JavaValueFactory.createMethod(
            BidiFunctionsRuntime.class,
            "bidiMarkAfter",
            BidiGlobalDir.class,
            SoyValue.class,
            boolean.class);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    JavaValue html = args.size() == 1 ? factory.constant(false) : args.get(1).asSoyBoolean();
    return factory.callStaticMethod(Methods.MARK_AFTER, context.getBidiDir(), args.get(0), html);
  }

  @Override
  public JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    List<JavaScriptValue> fnArgs = new ArrayList<>(args.size() + 1);
    fnArgs.add(context.getBidiDir());
    fnArgs.addAll(args);
    return factory.callNamespaceFunction(
        "soy", "soy.$$bidiMarkAfter", fnArgs.toArray(new JavaScriptValue[0]));
  }

  @Override
  public PythonValue applyForPythonSource(
      PythonValueFactory factory, List<PythonValue> args, PythonPluginContext context) {
    return factory
        .global("bidi.mark_after")
        .call(
            context.getBidiDir(),
            args.get(0),
            args.size() == 2 ? args.get(1) : factory.constant(false));
  }
}
