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

import com.google.common.base.Preconditions;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcFunction;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.JsExprUtils;
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

/**
 * A function that determines if a given string contains another given string.
 *
 * <p><code>strContains(expr1, expr2)</code> requires <code>expr1</code> and <code>expr2</code> to
 * be of type string or {@link SanitizedContent}. It evaluates to <code>true</code> iff <code>expr1
 * </code> contains <code>expr2</code>. <code>strContains</code> is case sensitive.
 *
 */
@SoyFunctionSignature(
  name = "strContains",
  value = {
    @Signature(
      // TODO(b/62134073): should be string, string and return a bool
      returnType = "?",
      parameterTypes = {"?", "?"}
    ),
  }
)
@Singleton
@SoyPureFunction
final class StrContainsFunction extends TypedSoyFunction
    implements SoyJavaFunction, SoyJsSrcFunction, SoyPySrcFunction, SoyJbcSrcFunction {

  @Inject
  StrContainsFunction() {}

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    SoyValue arg0 = args.get(0);
    SoyValue arg1 = args.get(1);

    Preconditions.checkArgument(
        arg0 instanceof StringData || arg0 instanceof SanitizedContent,
        "First argument to strContains() function is not StringData or SanitizedContent: %s",
        arg0);

    String strArg0 = arg0.coerceToString();
    String strArg1 = arg1.coerceToString();

    return BooleanData.forValue(strArg0.contains(strArg1));
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    // Coerce SanitizedContent args to strings.
    String arg0 = JsExprUtils.toString(args.get(0)).getText();
    String arg1 = JsExprUtils.toString(args.get(1)).getText();

    String exprText = "(" + arg0 + ").indexOf(" + arg1 + ") != -1";

    return new JsExpr(exprText, Operator.NOT_EQUAL.getPrecedence());
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    // Coerce SanitizedContent args to strings.
    String arg0 = args.get(0).toPyString().getText();
    String arg1 = args.get(1).toPyString().getText();

    String exprText = "(" + arg0 + ").find(" + arg1 + ") != -1";
    return new PyExpr(exprText, PyExprUtils.pyPrecedenceForOperator(Operator.NOT_EQUAL));
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class JbcSrcMethods {
    static final MethodRef STRING_CONTAINS =
        MethodRef.create(String.class, "contains", CharSequence.class);
  }

  @Override
  public SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args) {
    SoyExpression left = args.get(0);
    SoyExpression right = args.get(1);
    return SoyExpression.forBool(
        left.unboxAs(String.class).invoke(JbcSrcMethods.STRING_CONTAINS, right.coerceToString()));
  }
}
