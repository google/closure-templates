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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcFunction;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.JsExprUtils;
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
 * A function that determines the index of the first occurrence of a string within another string.
 *
 * <p><code>strIndexOf(expr1, expr2, expr3)</code> requires <code>expr1</code>, <code>expr2</code>
 * to be of type string or {@link com.google.template.soy.data.SanitizedContent} and <code>expr3
 * </code> of type boolean. <code>expr3</code> is optional for case insensitive compare.
 *
 * <p>It returns the index within the string <code>expr1</code> of the first occurrence of the
 * specified substring <code>expr2</code>. If no such index exists, then <code>-1</code>is returned.
 * <code>strIndexOf</code> by default is case sensitive and the string indices are zero based. If
 * <code>expr3</code> is specified and is <code>true</code>, then case insensitive compare is done.
 *
 */
@Singleton
@SoyPureFunction
final class StrIndexOfFunction
    implements SoyJavaFunction, SoyJsSrcFunction, SoyPySrcFunction, SoyJbcSrcFunction {

  @Inject
  StrIndexOfFunction() {}

  @Override
  public String getName() {
    return "strIndexOf";
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
        "First argument to strIndexOf() function is not StringData or SanitizedContent: %s",
        arg0);

    Preconditions.checkArgument(
        arg1 instanceof StringData || arg1 instanceof SanitizedContent,
        "Second argument to strIndexOf() function is not StringData or SanitizedContent: %s",
        arg1);

    if (arg2 != null) {
      Preconditions.checkArgument(
              arg2 instanceof BooleanData,
              "Third argument to strIndexOf() function is not BooleanData: %s",
              arg2);
    }

    String strArg0 = arg0.coerceToString();
    String strArg1 = arg1.coerceToString();

    if (arg2 != null && arg2.coerceToBoolean()) {
      strArg0 = strArg0.toUpperCase();
      strArg1 = strArg1.toUpperCase();
    }

    return IntegerData.forValue(strArg0.indexOf(strArg1));
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

    return new JsExpr("(" + arg0 + ").indexOf(" + arg1 + ")", Integer.MAX_VALUE);
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    // Coerce SanitizedContent args to strings.
    String arg0 = args.get(0).toPyString().getText();
    String arg1 = args.get(1).toPyString().getText();
    PyExpr arg2 = args.size() == 3 ? args.get(2) : null;

    String exprText;
    if (arg2 != null && arg2.toPyString().getText().toUpperCase().equals("TRUE")) {
      exprText = "(" + arg0 + ".upper().find(" + arg1 + ".upper()))";
    } else {
      exprText = "(" + arg0 + ").find(" + arg1 + ")";
    }

    return new PyExpr(exprText, Integer.MAX_VALUE);
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class JbcSrcMethods {
    static final MethodRef STRING_INDEX_OF =
        MethodRef.create(String.class, "indexOf", String.class);
  }

  @Override
  public SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args) {
    SoyExpression left = args.get(0);
    SoyExpression right = args.get(1);
    return SoyExpression.forInt(
        BytecodeUtils.numericConversion(
            left.unboxAs(String.class)
                .invoke(JbcSrcMethods.STRING_INDEX_OF, right.unboxAs(String.class)),
            Type.LONG_TYPE));
  }
}
