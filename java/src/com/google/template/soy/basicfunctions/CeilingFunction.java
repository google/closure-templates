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

import com.google.common.collect.ImmutableSet;
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
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.objectweb.asm.Type;

/**
 * Soy function that takes the ceiling of a number.
 *
 */
@Singleton
@SoyPureFunction
public final class CeilingFunction
    implements SoyJavaFunction, SoyJsSrcFunction, SoyPySrcFunction, SoyJbcSrcFunction {

  @Inject
  CeilingFunction() {}

  @Override
  public String getName() {
    return "ceiling";
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1);
  }

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    return IntegerData.forValue(BasicFunctionsRuntime.ceil(args.get(0)));
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr arg = args.get(0);

    return new JsExpr("Math.ceil(" + arg.getText() + ")", Integer.MAX_VALUE);
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    PyExpr arg = args.get(0);

    return new PyExpr("int(math.ceil(" + arg.getText() + "))", Integer.MAX_VALUE);
  }
  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class JbcSrcMethods {
    static final MethodRef CEIL_FN =
        MethodRef.create(BasicFunctionsRuntime.class, "ceil", SoyValue.class).asCheap();
    static final MethodRef MATH_CEIL = MethodRef.create(Math.class, "ceil", double.class).asCheap();
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
                JbcSrcMethods.MATH_CEIL.invoke(argument), Type.LONG_TYPE));
      default:
        return SoyExpression.forInt(JbcSrcMethods.CEIL_FN.invoke(argument.box()));
    }
  }
}
