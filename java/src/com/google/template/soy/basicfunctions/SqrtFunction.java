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

import com.google.inject.Singleton;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcFunction;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.shared.restricted.TypedSoyFunction;
import java.util.List;
import javax.inject.Inject;

/** Computes the sqrt of a Number expression. */
@SoyFunctionSignature(
  name = "sqrt",
  value = @Signature(returnType = "number", parameterTypes = {"number"})
)
@Singleton
@SoyPureFunction
public class SqrtFunction extends TypedSoyFunction
    implements SoyJavaFunction, SoyJsSrcFunction, SoyPySrcFunction, SoyJbcSrcFunction {
  @Inject
  SqrtFunction() {}

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    SoyValue arg = args.get(0);
    return FloatData.forValue(Math.sqrt(arg.numberValue()));
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr arg = args.get(0);
    return new JsExpr("Math.sqrt(" + arg.getText() + ")", Integer.MAX_VALUE);
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    return new PyExpr(String.format("runtime.sqrt(%s)", args.get(0).getText()), Integer.MAX_VALUE);
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class JbcSrcMethods {
    static final MethodRef MATH_SQRT = MethodRef.create(Math.class, "sqrt", double.class);
  }

  @Override
  public SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args) {
    return SoyExpression.forFloat(JbcSrcMethods.MATH_SQRT.invoke(args.get(0).coerceToDouble()));
  }
}
