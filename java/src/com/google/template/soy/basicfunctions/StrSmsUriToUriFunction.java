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

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcFunction;
import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.internal.Sanitizers;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.shared.restricted.TypedSoyFunction;
import java.lang.reflect.Method;
import java.util.List;

/** Soy function that converts an sms URI string to a sanitized URI. */
@SoyPureFunction
@SoyFunctionSignature(
    name = "strSmsUriToUri",
    value =
        @Signature(
            parameterTypes = {"string"},
            returnType = "uri"))
final class StrSmsUriToUriFunction extends TypedSoyFunction
    implements SoyJavaSourceFunction, SoyLibraryAssistedJsSrcFunction, SoyPySrcFunction {

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    String arg = args.get(0).getText();
    return new JsExpr(String.format("soy.$$strSmsUriToUri(String(%s))", arg), Integer.MAX_VALUE);
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.of("soy");
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    PyExpr arg = args.get(0);
    return new PyExpr("sanitize.sms_to_uri(str(" + arg.getText() + "))", Integer.MAX_VALUE);
  }

  // Lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method SMS_TO_URI =
        JavaValueFactory.createMethod(Sanitizers.class, "filterSmsUri", String.class);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    return factory.callStaticMethod(Methods.SMS_TO_URI, args.get(0));
  }
}
