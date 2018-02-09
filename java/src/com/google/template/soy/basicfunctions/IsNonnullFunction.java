/*
 * Copyright 2012 Google Inc.
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

import com.google.common.collect.Lists;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcFunction;
import com.google.template.soy.jssrc.dsl.SoyJsPluginUtils;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.shared.restricted.TypedSoyFunction;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

/**
 * Soy function that checks whether its argument is a defined nonnull value.
 *
 */
@SoyFunctionSignature(
  name = "isNonnull",
  value =
      @Signature(
        // TODO(b/70946095): should return bool
        returnType = "?",
        parameterTypes = {"?"}
      )
)
@Singleton
@SoyPureFunction
class IsNonnullFunction extends TypedSoyFunction
    implements SoyJavaFunction, SoyJsSrcFunction, SoyPySrcFunction, SoyJbcSrcFunction {

  @Inject
  IsNonnullFunction() {}

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    SoyValue arg = args.get(0);
    return BooleanData.forValue(!(arg instanceof UndefinedData || arg instanceof NullData));
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr arg = args.get(0);
    JsExpr nullJsExpr = new JsExpr("null", Integer.MAX_VALUE);
    // Note: In JavaScript, "x != null" is equivalent to "x !== undefined && x !== null".
    return SoyJsPluginUtils.genJsExprUsingSoySyntax(
        Operator.NOT_EQUAL, Lists.<JsExpr>newArrayList(arg, nullJsExpr));
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    // Note: This check could blow up if the variable was never created at all. However, this should
    // not be possible as a variable not found in the function is assumed to be part of opt_data.
    return PyExprUtils.genPyNotNullCheck(args.get(0));
  }

  @Override
  public SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args) {
    final SoyExpression arg = args.get(0);
    if (BytecodeUtils.isPrimitive(arg.resultType())) {
      return SoyExpression.TRUE;
    }
    // This is what javac generates for 'someObject != null'
    return SoyExpression.forBool(
        new Expression(Type.BOOLEAN_TYPE, arg.features()) {
          @Override
          protected void doGen(CodeBuilder adapter) {
            arg.gen(adapter);
            Label isNull = new Label();
            adapter.ifNull(isNull);
            // non-null
            adapter.pushBoolean(true);
            Label end = new Label();
            adapter.goTo(end);
            adapter.mark(isNull);
            adapter.pushBoolean(false);
            adapter.mark(end);
          }
        });
  }
}
