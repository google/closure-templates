/*
 * Copyright 2018 Google Inc.
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

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcFunction;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.JsExprUtils;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcFunction;
import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.shared.restricted.TypedSoyFunction;
import java.lang.reflect.Method;
import java.util.List;

/** A function that changes strings to lower case. */
@SoyFunctionSignature(
    name = "strToAsciiLowerCase",
    value =
        @Signature(
            parameterTypes = {"string"},
            returnType = "string"))
@SoyPureFunction
public final class StrToAsciiLowerCaseFunction extends TypedSoyFunction
    implements SoyJavaSourceFunction,
        SoyLibraryAssistedJsSrcFunction,
        SoyPySrcFunction,
        SoyJbcSrcFunction {

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.of("soy");
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    String arg = JsExprUtils.toString(args.get(0)).getText();
    return new JsExpr("soy.$$strToAsciiLowerCase(" + arg + ")", Integer.MAX_VALUE);
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    String arg = args.get(0).toPyString().getText();
    return new PyExpr("runtime.str_to_ascii_lower_case(" + arg + ")", Integer.MAX_VALUE);
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method ASCII_TO_LOWER_CASE_FN =
        JavaValueFactory.createMethod(Ascii.class, "toLowerCase", String.class);
    static final MethodRef ASCII_TO_LOWER_CASE_FN_REF = MethodRef.create(ASCII_TO_LOWER_CASE_FN);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    return factory.callStaticMethod(Methods.ASCII_TO_LOWER_CASE_FN, args.get(0));
  }

  @Override
  public SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args) {
    return SoyExpression.forString(
        Methods.ASCII_TO_LOWER_CASE_FN_REF.invoke(args.get(0).unboxAs(String.class)));
  }
}
