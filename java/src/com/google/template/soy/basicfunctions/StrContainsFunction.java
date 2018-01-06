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
import com.google.common.collect.ImmutableSet;
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
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyPureFunction;

import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A function that determines if a given string contains another given string.
 * <p>
 * <p><code>strContains(expr1, expr2)</code> requires <code>expr1</code>, <code>expr2</code> to be
 * of type string or {@link SanitizedContent} and <code>expr3</code> of type boolean. <code>expr3
 * </code> is optional for case insensitive compare. It evaluates to <code>true</code> iff <code>
 * expr1
 * </code> contains <code>expr2</code>. <code>strContains</code>. <code>expr3</code> is optional for
 * case insensitive compare.
 * <p>
 * <p>If <code>expr3</code> is specified and is <code>true</code>, then case insensitive compare is
 * done.
 */
@Singleton
@SoyPureFunction
final class StrContainsFunction
    implements SoyJavaFunction, SoyJsSrcFunction, SoyPySrcFunction, SoyJbcSrcFunction {

  @Inject
  StrContainsFunction() {
  }

  @Override
  public String getName() {
    return "strContains";
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(2, 3);
  }

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    SoyValue arg0 = args.get(0);
    SoyValue arg1 = args.get(1);
    SoyValue arg2 = args.size() == 3 ? args.get(2) : null;

    Preconditions.checkArgument(
        arg0 instanceof StringData || arg0 instanceof SanitizedContent,
        "First argument to strContains() function is not StringData or SanitizedContent: %s",
        arg0);

    if (arg2 != null) {
      Preconditions.checkArgument(
          arg2 instanceof BooleanData,
          "Third argument to strContains() function is not BooleanData: %s",
          arg2);
    }

    String strArg0 = arg0.coerceToString();
    String strArg1 = arg1.coerceToString();

    if (arg2 != null && arg2.coerceToBoolean()) {
      strArg0 = strArg0.toUpperCase();
      strArg1 = strArg1.toUpperCase();
    }

    return BooleanData.forValue(strArg0.contains(strArg1));
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    // Coerce SanitizedContent args to strings.
    String arg0 = JsExprUtils.toString(args.get(0)).getText();
    String arg1 = JsExprUtils.toString(args.get(1)).getText();
    JsExpr arg2 = args.size() == 3 ? args.get(2) : null;

    if (arg2 != null && arg2.getText().toUpperCase().equals("TRUE")) {
      arg0 = arg0.toUpperCase();
      arg1 = arg1.toUpperCase();
    }

    String exprText = "(" + arg0 + ").indexOf(" + arg1 + ") != -1";

    return new JsExpr(exprText, Operator.NOT_EQUAL.getPrecedence());
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    // Coerce SanitizedContent args to strings.
    String arg0 = args.get(0).toPyString().getText();
    String arg1 = args.get(1).toPyString().getText();
    PyExpr arg2 = args.size() == 3 ? args.get(2) : null;

    String exprText;
    if (arg2 != null && arg2.toPyString().getText().toUpperCase().equals("TRUE")) {
      exprText = "(" + arg0 + ".upper().find(" + arg1 + ".upper())) != -1";
    } else {
      exprText = "(" + arg0 + ").find(" + arg1 + ") != -1";
    }

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
