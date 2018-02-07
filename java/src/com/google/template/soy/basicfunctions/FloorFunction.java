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
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
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
import javax.inject.Singleton;
import org.objectweb.asm.Type;

/**
 * Soy function that takes the floor of a number.
 *
 */
@Singleton
@SoyPureFunction
@SoyFunctionSignature(
  name = "floor",
  value = {
    @Signature(
      parameterTypes = {"number"},
      returnType = "int"
    )
  }
)
public final class FloorFunction extends TypedSoyFunction
    implements SoyJavaFunction, SoyJsSrcFunction, SoyPySrcFunction, SoyJbcSrcFunction {

  @Inject
  FloorFunction() {}

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    return IntegerData.forValue(BasicFunctionsRuntime.floor(args.get(0)));
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr arg = args.get(0);

    return new JsExpr("Math.floor(" + arg.getText() + ")", Integer.MAX_VALUE);
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    PyExpr arg = args.get(0);

    return new PyExpr("int(math.floor(" + arg.getText() + "))", Integer.MAX_VALUE);
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class JbcSrcMethods {
    static final MethodRef FLOOR_FN =
        MethodRef.create(BasicFunctionsRuntime.class, "floor", SoyValue.class).asNonNullable();
    static final MethodRef MATH_FLOOR =
        MethodRef.create(Math.class, "floor", double.class).asCheap();
  }

  @Override
  public SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args) {
    SoyExpression argument = args.get(0);
    switch (argument.resultType().getSort()) {
      case Type.LONG:
        return argument;
      case Type.DOUBLE:
        return SoyExpression.forInt(
            BytecodeUtils.numericConversion(
                JbcSrcMethods.MATH_FLOOR.invoke(argument), Type.LONG_TYPE));
      default:
        return SoyExpression.forInt(JbcSrcMethods.FLOOR_FN.invoke(argument.box()));
    }
  }
}
