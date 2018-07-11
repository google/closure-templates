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

import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcFunction;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.JsExprUtils;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.shared.restricted.TypedSoyFunction;
import java.lang.reflect.Method;
import java.util.List;
import org.objectweb.asm.Type;

/**
 * A function that determines the length of a string.
 *
 * <p><code>strLen(expr1)</code> requires <code>expr1</code> to be of type string or {@link
 * com.google.template.soy.data.SanitizedContent}.
 *
 * <p>TODO(lukes,dcphillips): This function has inconsistent behavior between the backends when it
 * comes to astral plane codepoints. Python is the only backend doing it right.
 *
 */
@SoyFunctionSignature(
    name = "strLen",
    value = {
      @Signature(
          returnType = "int",
          // TODO(b/62134073): should be string
          parameterTypes = {"?"}),
    })
@SoyPureFunction
final class StrLenFunction extends TypedSoyFunction
    implements SoyJavaSourceFunction, SoyJsSrcFunction, SoyPySrcFunction, SoyJbcSrcFunction {

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    // Coerce SanitizedContent args to strings.
    String arg0 = JsExprUtils.toString(args.get(0)).getText();

    return new JsExpr("(" + arg0 + ").length", Integer.MAX_VALUE);
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    return new PyExpr("len(" + args.get(0).toPyString().getText() + ")", Integer.MAX_VALUE);
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final MethodRef STRING_LENGTH_REF = MethodRef.create(String.class, "length");
    static final Method STR_LEN =
        JavaValueFactory.createMethod(BasicFunctionsRuntime.class, "strLen", String.class);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    return factory.callStaticMethod(Methods.STR_LEN, args.get(0));
  }

  @Override
  public SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args) {
    return SoyExpression.forInt(
        BytecodeUtils.numericConversion(
            args.get(0).unboxAs(String.class).invoke(Methods.STRING_LENGTH_REF), Type.LONG_TYPE));
  }
}
