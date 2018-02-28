/*
 * Copyright 2017 Google Inc.
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
import com.google.common.primitives.Doubles;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcFunction;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.shared.restricted.TypedSoyFunction;
import com.google.template.soy.types.FloatType;
import com.google.template.soy.types.SoyTypes;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Soy function that converts a string to a float.
 *
 * <p>This function accepts a single string. If the string is a valid float, then the function will
 * return that float. Otherwise, it will return {@code null}.
 *
 * <p>Ex: <code>
 *   {parseFloat('9.1') + 1}  // evaluates to 10.1
 *   {parseFloat('garbage') ?: 1.0}  // evaluates to 1.0
 * </code>
 */
@Singleton
@SoyPureFunction
@SoyFunctionSignature(
  name = "parseFloat",
  value =
      @Signature(
        parameterTypes = {"string"},
        // TODO(b/70946095): should be nullable
        returnType = "float"
      )
)
public final class ParseFloatFunction extends TypedSoyFunction
    implements SoyJavaFunction,
        SoyLibraryAssistedJsSrcFunction,
        SoyPySrcFunction,
        SoyJbcSrcFunction {

  @Inject
  ParseFloatFunction() {}

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    String stringValue = args.get(0).stringValue();
    Double d = Doubles.tryParse(stringValue);
    return (d == null || d.isNaN()) ? NullData.INSTANCE : FloatData.forValue(d);
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    // TODO(user): parseFloat('123abc') == 123; JS parseFloat tries to parse as much as it can.
    // That means parseFloat('1.1.1') == 1.1
    String arg = args.get(0).getText();
    return new JsExpr(String.format("soy.$$parseFloat(%s)", arg), Integer.MAX_VALUE);
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.of("soy");
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    return new PyExpr(
        String.format("runtime.parse_float(%s)", args.get(0).getText()), Integer.MAX_VALUE);
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class JbcSrcMethods {
    static final MethodRef PARSE_FLOAT =
        MethodRef.create(BasicFunctionsRuntime.class, "parseFloat", String.class);
  }

  @Override
  public SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args) {
    return SoyExpression.forSoyValue(
        SoyTypes.makeNullable(FloatType.getInstance()),
        JbcSrcMethods.PARSE_FLOAT.invoke(args.get(0).unboxAs(String.class)));
  }
}
