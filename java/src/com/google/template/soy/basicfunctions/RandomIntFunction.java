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

import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptPluginContext;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValue;
import com.google.template.soy.plugin.javascript.restricted.JavaScriptValueFactory;
import com.google.template.soy.plugin.javascript.restricted.SoyJavaScriptSourceFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.TypedSoyFunction;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Soy function that generates a random integer in the range [0, n-1].
 *
 */
@SoyFunctionSignature(
    name = "randomInt",
    // TODO(b/70946095): param should be an 'int', not a 'number'
    value = @Signature(returnType = "int", parameterTypes = "number"))
public final class RandomIntFunction extends TypedSoyFunction
    implements SoyJavaSourceFunction, SoyJavaScriptSourceFunction, SoyPySrcFunction {

  @Override
  public JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    return factory.callNamespaceFunction("soy", "soy.$$randomInt", args.get(0));
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    PyExpr arg = args.get(0);
    // Subtract 1 from the argument as the python randint function is inclusive on both sides.
    return new PyExpr("random.randint(0, " + arg.getText() + " - 1)", Integer.MAX_VALUE);
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method RANDOM_INT_FN =
        JavaValueFactory.createMethod(BasicFunctionsRuntime.class, "randomInt", long.class);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    return factory.callStaticMethod(Methods.RANDOM_INT_FN, args.get(0).asSoyInt());
  }
}
