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

import com.google.inject.Provider;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcFunction;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.JsExprUtils;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.LocaleString;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.TypedSoyFunction;
import com.ibm.icu.util.ULocale;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/** A function that changes strings to lower case. */
@SoyFunctionSignature(
    name = "strToLowerCase",
    value = @Signature(parameterTypes = {"string"}, returnType = "string")
)
@Singleton
public final class StrToLowerCaseFunction extends TypedSoyFunction
    implements SoyJavaFunction,
        SoyJsSrcFunction,
        SoyPySrcFunction,
        SoyJbcSrcFunction  {
  private final Provider<String> localeStringProvider;

  @Inject
  StrToLowerCaseFunction(@LocaleString Provider<String> localeStringProvider) {
    this.localeStringProvider = localeStringProvider;
  }

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    String stringValue = args.get(0).toString();
    ULocale uLocale = new ULocale(localeStringProvider.get());
    return StringData.forValue(BasicFunctionsRuntime.strToLowerCase(stringValue, uLocale));
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    String arg = JsExprUtils.toString(args.get(0)).getText();
    return new JsExpr("(" + arg + ").toLocaleLowerCase()", Integer.MAX_VALUE);
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    String arg = args.get(0).toPyString().getText();
    // TODO(b/68013322): Update this to use PyIcu's UnicodeString.toLower once we have access to the
    // current locale.
    return new PyExpr("(" + arg + ").lower()", Integer.MAX_VALUE);
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class JbcSrcMethods {
    static final MethodRef STR_TO_LOWER_CASE_FN = MethodRef.create(
        BasicFunctionsRuntime.class, "strToLowerCase", String.class, ULocale.class);
  }

  @Override
  public SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args) {
    return SoyExpression.forString(
        JbcSrcMethods.STR_TO_LOWER_CASE_FN.invoke(
            args.get(0).unboxAs(String.class),
            context.getULocale()));
  }
}
