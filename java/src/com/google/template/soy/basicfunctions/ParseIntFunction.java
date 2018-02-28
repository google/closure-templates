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
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.IntegerData;
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
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.SoyTypes;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Soy function that converts a string to an integer.
 *
 * <p>This function accepts a single string. If the string is a valid base 10 integer, then the
 * function will return that integer. Otherwise, it will return {@code null}.
 *
 * <p>Ex: <code>
 *   {parseInt('10') + 20}  // evaluates to 30
 *   {parseInt('garbage') ?: -1}  // evaluates to -1
 * </code>
 */
@Singleton
@SoyPureFunction
@SoyFunctionSignature(
  name = "parseInt",
  value =
      @Signature(
        parameterTypes = {"string"},
        // TODO(b/70946095): should be nullable
        returnType = "int"
      )
)
public final class ParseIntFunction extends TypedSoyFunction
    implements SoyJavaFunction,
        SoyLibraryAssistedJsSrcFunction,
        SoyPySrcFunction,
        SoyJbcSrcFunction {

  @Inject
  ParseIntFunction() {}

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    String toParse = args.get(0).stringValue();

    try {
      // Java backends can handle full 64 bit numbers.
      return IntegerData.forValue(Long.parseLong(toParse));
    } catch (NumberFormatException e) {
      return NullData.INSTANCE;
    }
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    // TODO(user): parseInt('123abc', 10) == 123; JS parseInt tries to parse as much as it can.
    String arg = args.get(0).getText();
    return new JsExpr(String.format("soy.$$parseInt(%s)", arg), Integer.MAX_VALUE);
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.of("soy");
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    String arg = args.get(0).getText();
    return new PyExpr(String.format("runtime.parse_int(%s)", arg), Integer.MAX_VALUE);
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class JbcSrcMethods {
    static final MethodRef PARSE_INT =
        MethodRef.create(BasicFunctionsRuntime.class, "parseInt", String.class);
  }

  @Override
  public SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args) {
    return SoyExpression.forSoyValue(
        SoyTypes.makeNullable(IntType.getInstance()),
        JbcSrcMethods.PARSE_INT.invoke(args.get(0).unboxAs(String.class)));
  }
}
