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
 * Soy function that maybe inserts an HTML attribute for bidi directionality ('dir=ltr' or
 * 'dir=rtl'). The function requires the text string that will make up the body of the associated
 * HTML tag pair. If the text string is detected to require different directionality than the
 * current global directionality, then the appropriate HTML attribute is inserted. Otherwise,
 * nothing is inserted.
 *
 */
@SoyFunctionSignature(
    name = "bidiDirAttr",
    value = {
      // TODO(b/70946095): should take a string
      @Signature(returnType = "attributes", parameterTypes = "?"),
      @Signature(
          returnType = "attributes",
          // TODO(b/70946095): should take a string and a bool
          parameterTypes = {"?", "?"})
    })
final class BidiDirAttrFunction
    implements SoyJavaSourceFunction, SoyJavaScriptSourceFunction, SoyPythonSourceFunction {

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method DIR_ATTR_SANITIZED =
        JavaValueFactory.createMethod(
            BidiFunctionsRuntime.class,
            "bidiDirAttrSanitized",
            BidiGlobalDir.class,
            SoyValue.class,
            boolean.class);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    JavaValue html = args.size() == 1 ? factory.constant(false) : args.get(1).asSoyBoolean();
    return factory.callStaticMethod(
        Methods.DIR_ATTR_SANITIZED, context.getBidiDir(), args.get(0), html);
  }

  @Override
  public JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    List<JavaScriptValue> fnArgs = new ArrayList<>();
    fnArgs.add(context.getBidiDir());
    fnArgs.addAll(args);
    return factory.callNamespaceFunction(
        "soy", "soy.$$bidiDirAttr", fnArgs.toArray(new JavaScriptValue[0]));
  }

  @Override
  public PythonValue applyForPythonSource(
      PythonValueFactory factory, List<PythonValue> args, PythonPluginContext context) {
    return factory
        .global("bidi.dir_attr")
        .call(
            context.getBidiDir(),
            args.get(0),
            args.size() == 2 ? args.get(1) : factory.constant(false));
  }
}
