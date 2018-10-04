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
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.shared.restricted.TypedSoyFunction;
import java.lang.reflect.Method;
import java.util.List;

/**
 * A function that determines the index of the first occurrence of a string within another string.
 *
 * <p><code>strIndexOf(expr1, expr2)</code> requires <code>expr1</code> and <code>expr2</code> to be
 * of type string or {@link com.google.template.soy.data.SanitizedContent}.
 *
 * <p>It returns the index within the string <code>expr1</code> of the first occurrence of the
 * specified substring <code>expr2</code>. If no such index exists, then <code>-1</code>is returned.
 * <code>strIndexOf</code> is case sensitive and the string indices are zero based.
 *
 */
@SoyFunctionSignature(
    name = "strIndexOf",
    value =
        @Signature(
            returnType = "int",
            // TODO(b/62134073): should be string, string
            parameterTypes = {"?", "?"}))
@SoyPureFunction
final class StrIndexOfFunction extends TypedSoyFunction
    implements SoyJavaSourceFunction, SoyJavaScriptSourceFunction, SoyPySrcFunction {

  @Override
  public JavaScriptValue applyForJavaScriptSource(
      JavaScriptValueFactory factory, List<JavaScriptValue> args, JavaScriptPluginContext context) {
    return args.get(0).coerceToString().invokeMethod("indexOf", args.get(1).coerceToString());
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    // Coerce SanitizedContent args to strings.
    String arg0 = args.get(0).toPyString().getText();
    String arg1 = args.get(1).toPyString().getText();

    return new PyExpr("(" + arg0 + ").find(" + arg1 + ")", Integer.MAX_VALUE);
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method INDEX_OF =
        JavaValueFactory.createMethod(
            BasicFunctionsRuntime.class, "strIndexOf", SoyValue.class, SoyValue.class);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    return factory.callStaticMethod(Methods.INDEX_OF, args.get(0), args.get(1));
  }
}
