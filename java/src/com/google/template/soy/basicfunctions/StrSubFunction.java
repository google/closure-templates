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
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.JsExprUtils;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A function that returns a substring of a given string.
 *
 * <p><code>strSub(expr1, expr2, expr3)</code> requires <code>expr1</code> to be of type string or
 * {@link com.google.template.soy.data.SanitizedContent} and <code>expr2</code> and <code>expr3
 * </code> to be of type integer. <code>expr3</code> is optional.
 *
 * <p>This function returns a new string that is a substring of <code>expr1</code>. The returned
 * substring begins at the index specified by <code>expr2</code>. If <code>expr3</code> is not
 * specified, the substring will extend to the end of <code>expr1</code>. Otherwise it will extend
 * to the character at index <code>expr3 - 1</code>.
 *
 */
@Singleton
@SoyPureFunction
final class StrSubFunction implements SoyJavaFunction, SoyJsSrcFunction, SoyPySrcFunction {

  @Inject
  StrSubFunction() {}

  @Override
  public String getName() {
    return "strSub";
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
        "First argument to strSub() function is not StringData or SanitizedContent: %s",
        arg0);

    Preconditions.checkArgument(
        arg1 instanceof IntegerData,
        "Second argument to strSub() function is not IntegerData: %s",
        arg1);

    if (arg2 != null) {
      Preconditions.checkArgument(
          arg2 instanceof IntegerData,
          "Third argument to strSub() function is not IntegerData: %s",
          arg2);
    }

    String strArg0 = arg0.coerceToString();
    int intArg1 = arg1.integerValue();

    if (arg2 != null) {
      return StringData.forValue(strArg0.substring(intArg1, arg2.integerValue()));
    } else {
      return StringData.forValue(strArg0.substring(intArg1));
    }
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    // Coerce SanitizedContent args to strings.
    String arg0 = JsExprUtils.toString(args.get(0)).getText();
    JsExpr arg1 = args.get(1);
    JsExpr arg2 = args.size() == 3 ? args.get(2) : null;

    return new JsExpr(
        "("
            + arg0
            + ").substring("
            + arg1.getText()
            + (arg2 != null ? "," + arg2.getText() : "")
            + ")",
        Integer.MAX_VALUE);
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    // Coerce SanitizedContent args to strings.
    String base = args.get(0).toPyString().getText();
    PyExpr start = args.get(1);
    PyExpr end = args.size() == 3 ? args.get(2) : null;

    return new PyStringExpr(
        "(" + base + ")[" + start.getText() + ":" + (end != null ? end.getText() : "") + "]");
  }
}
