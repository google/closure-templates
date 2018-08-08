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
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.JsExprUtils;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.shared.restricted.TypedSoyFunction;
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
          // TODO(b/62134073): should be string, int
          parameterTypes = {"?", "?"}),
      @Signature(
          returnType = "string",
          // TODO(b/62134073): should be string, int, int
          parameterTypes = {"?", "?", "?"}),
    })
@SoyPureFunction
final class StrSubFunction extends TypedSoyFunction
    implements SoyJavaSourceFunction, SoyJsSrcFunction, SoyPySrcFunction {

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    // Coerce SanitizedContent args to strings.
    String arg0 = JsExprUtils.toString(args.get(0)).getText();
    JsExpr arg1 = args.get(1);
    JsExpr arg2 = args.size() == 3 ? args.get(2) : null;

    return new JsExpr(
        "("
            + arg0
            + ").substring("
            + arg1.getText()
            + (arg2 != null ? "," + arg2.getText() : "")
            + ")",
        Integer.MAX_VALUE);
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    // Coerce SanitizedContent args to strings.
    String base = args.get(0).toPyString().getText();
    PyExpr start = args.get(1);
    PyExpr end = args.size() == 3 ? args.get(2) : null;

    return new PyStringExpr(
        "(" + base + ")[" + start.getText() + ":" + (end != null ? end.getText() : "") + "]");
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
