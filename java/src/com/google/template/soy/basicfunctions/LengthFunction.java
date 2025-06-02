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
import com.google.template.soy.plugin.java.internal.SoyJavaExternFunction;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptPluginContext;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValue;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValueFactory;
import com.google.template.soy.plugin.javascript.restricted.SoyJavaScriptSourceFunction;
import com.google.template.soy.plugin.python.restricted.PythonPluginContext;
import com.google.template.soy.plugin.python.restricted.PythonValue;
import com.google.template.soy.plugin.python.restricted.PythonValueFactory;
import com.google.template.soy.plugin.python.restricted.SoyPythonSourceFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFieldSignature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.shared.restricted.TypedSoyFunction;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

/** Soy function that gets the length of a list. */
@SoyPureFunction
@SoyFunctionSignature(
    name = "length",
    value =
        @Signature(
            parameterTypes = {"list<any>"},
            returnType = "int"))
@SoyFieldSignature(name = "length", baseType = "list<any>", returnType = "int")
public final class LengthFunction extends TypedSoyFunction
    implements SoyJavaScriptSourceFunction, SoyPythonSourceFunction, SoyJavaExternFunction {

  @Override
  public JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    return args.get(0).accessProperty("length");
  }

  @Override
  public PythonValue applyForPythonSource(
      PythonValueFactory factory, List<PythonValue> args, PythonPluginContext context) {
    return factory.global("len").call(args.get(0));
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method SOY_VALUE_LENGTH =
        JavaValueFactory.createMethod(BasicFunctionsRuntime.class, "length", SoyValue.class);
    static final Method COLLECTION_SIZE = JavaValueFactory.createMethod(Collection.class, "size");
  }

  @Override
  public Method getExternJavaMethod(List<RuntimeType> argTypes) {
    return argTypes.get(0) == RuntimeType.SOY_VALUE
        ? Methods.SOY_VALUE_LENGTH
        : Methods.COLLECTION_SIZE;
  }

  @Override
  public boolean adaptArgs() {
    return false;
  }
}
