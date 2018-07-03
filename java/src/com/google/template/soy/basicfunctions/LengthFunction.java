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

import com.google.template.soy.data.SoyList;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcFunction;
import com.google.template.soy.jssrc.restricted.JsExpr;
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
 * Soy function that gets the length of a list.
 *
 */
@SoyPureFunction
@SoyFunctionSignature(
    name = "length",
    value =
        @Signature(
            parameterTypes = {"list<any>"},
            returnType = "int"))
public final class LengthFunction extends TypedSoyFunction
    implements SoyJavaSourceFunction, SoyJsSrcFunction, SoyPySrcFunction, SoyJbcSrcFunction {

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr arg = args.get(0);

    String exprText =
        arg.getPrecedence() == Integer.MAX_VALUE
            ? arg.getText() + ".length"
            : "(" + arg.getText() + ").length";
    return new JsExpr(exprText, Integer.MAX_VALUE);
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    PyExpr arg = args.get(0);

    return new PyExpr("len(" + arg.getText() + ")", Integer.MAX_VALUE);
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final MethodRef LIST_SIZE_REF = MethodRef.create(List.class, "size");
    static final MethodRef SOYLIST_LENGTH_REF = MethodRef.create(SoyList.class, "length");

    static final Method DELEGATE_SOYLIST_LENGTH =
        JavaValueFactory.createMethod(BasicFunctionsRuntime.class, "length", SoyList.class);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    return factory.callStaticMethod(Methods.DELEGATE_SOYLIST_LENGTH, args.get(0));
  }

  @Override
  public SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args) {
    SoyExpression soyExpression = args.get(0);
    Expression lengthExpressionAsInt;
    if (soyExpression.isBoxed()) {
      lengthExpressionAsInt =
          soyExpression.checkedCast(SoyList.class).invoke(Methods.SOYLIST_LENGTH_REF);
    } else {
      lengthExpressionAsInt = soyExpression.checkedCast(List.class).invoke(Methods.LIST_SIZE_REF);
    }
    return SoyExpression.forInt(
        BytecodeUtils.numericConversion(lengthExpressionAsInt, Type.LONG_TYPE));
  }
}
