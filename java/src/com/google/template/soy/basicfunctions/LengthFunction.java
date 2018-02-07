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
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.Expression;
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
 * Soy function that gets the length of a list.
 *
 */
@Singleton
@SoyPureFunction
@SoyFunctionSignature(
  name = "length",
  value =
      @Signature(
        parameterTypes = {"list<any>"},
        returnType = "int"
      )
)
public final class LengthFunction extends TypedSoyFunction
    implements SoyJavaFunction, SoyJsSrcFunction, SoyPySrcFunction, SoyJbcSrcFunction {

  @Inject
  LengthFunction() {}

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    SoyValue arg = args.get(0);

    if (arg == null) {
      throw new IllegalArgumentException("Argument to length() function is null.");
    }

    if (!(arg instanceof SoyList)) {
      throw new IllegalArgumentException(
          "Argument to length() function is not a SoyList "
              + "(found type "
              + arg.getClass().getName()
              + ").");
    }
    return IntegerData.forValue(((SoyList) arg).length());
  }

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
  private static final class JbcSrcMethods {
    static final MethodRef LIST_SIZE = MethodRef.create(List.class, "size");
    static final MethodRef SOYLIST_LENGTH = MethodRef.create(SoyList.class, "length");
  }

  @Override
  public SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args) {
    SoyExpression soyExpression = args.get(0);
    Expression lengthExpressionAsInt;
    if (soyExpression.isBoxed()) {
      lengthExpressionAsInt =
          soyExpression.checkedCast(SoyList.class).invoke(JbcSrcMethods.SOYLIST_LENGTH);
    } else {
      lengthExpressionAsInt = soyExpression.checkedCast(List.class).invoke(JbcSrcMethods.LIST_SIZE);
    }
    return SoyExpression.forInt(
        BytecodeUtils.numericConversion(lengthExpressionAsInt, Type.LONG_TYPE));
  }
}
