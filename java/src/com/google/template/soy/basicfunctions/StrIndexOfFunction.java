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
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.SoyString;
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
 * A function that determines the index of the first occurrence of a string within another string.
 *
 * <p><code>strIndexOf(expr1, expr2)</code> requires <code>expr1</code> and <code>expr2</code> to be
 * of type string or {@link com.google.template.soy.data.SanitizedContent}.
 *
 * <p>It returns the index within the string <code>expr1</code> of the first occurrence of the
 * specified substring <code>expr2</code>. If no such index exists, then <code>-1</code>is returned.
 * <code>strIndexOf</code> is case sensitive and the string indices are zero based.
 *
 */
@SoyFunctionSignature(
  name = "strIndexOf",
  value = {
    @Signature(
      returnType = "int",
      // TODO(b/62134073): should be string, string
      parameterTypes = {"?", "?"}
    ),
  }
)
@Singleton
@SoyPureFunction
final class StrIndexOfFunction extends TypedSoyFunction
    implements SoyJavaFunction, SoyJsSrcFunction, SoyPySrcFunction, SoyJbcSrcFunction {

  @Inject
  StrIndexOfFunction() {}

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    SoyValue arg0 = args.get(0);
    SoyValue arg1 = args.get(1);

    Preconditions.checkArgument(
        arg0 instanceof SoyString,
        "First argument to strIndexOf() function is not StringData or SanitizedContent: %s",
        arg0);

    Preconditions.checkArgument(
        arg1 instanceof SoyString,
        "Second argument to strIndexOf() function is not StringData or SanitizedContent: %s",
        arg1);

    String strArg0 = arg0.coerceToString();
    String strArg1 = arg1.coerceToString();

    return IntegerData.forValue(strArg0.indexOf(strArg1));
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    // Coerce SanitizedContent args to strings.
    String arg0 = JsExprUtils.toString(args.get(0)).getText();
    String arg1 = JsExprUtils.toString(args.get(1)).getText();

    return new JsExpr("(" + arg0 + ").indexOf(" + arg1 + ")", Integer.MAX_VALUE);
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    // Coerce SanitizedContent args to strings.
    String arg0 = args.get(0).toPyString().getText();
    String arg1 = args.get(1).toPyString().getText();

    return new PyExpr("(" + arg0 + ").find(" + arg1 + ")", Integer.MAX_VALUE);
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
