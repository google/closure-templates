/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.bidifunctions;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
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
import com.google.template.soy.shared.restricted.TypedSoyFunction;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.objectweb.asm.Type;

/**
 * Soy function that gets the bidi directionality of a text string (1 for LTR, -1 for RTL, or 0 for
 * none).
 *
 */
@SoyFunctionSignature(
  name = "bidiTextDir",
  value = {
    // TODO(b/70946095): should take a string
    @Signature(returnType = "int", parameterTypes = "?"),
    // TODO(b/70946095): should take a string and a bool
    @Signature(
      returnType = "int",
      parameterTypes = {"?", "?"}
    )
  }
)
@Singleton
final class BidiTextDirFunction extends TypedSoyFunction
    implements SoyJavaFunction,
        SoyLibraryAssistedJsSrcFunction,
        SoyPySrcFunction,
        SoyJbcSrcFunction {

  @Inject
  BidiTextDirFunction() {}

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    SoyValue value = args.get(0);
    return IntegerData.forValue(
        BidiFunctionsRuntime.bidiTextDir(value, args.size() == 2 && args.get(1).booleanValue()));
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class JbcSrcMethods {
    static final MethodRef BIDI_TEXT_DIR =
        MethodRef.create(BidiFunctionsRuntime.class, "bidiTextDir", SoyValue.class, boolean.class);
  }

  @Override
  public SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args) {
    return SoyExpression.forInt(
        BytecodeUtils.numericConversion(
            JbcSrcMethods.BIDI_TEXT_DIR.invoke(
                args.get(0).box(),
                args.size() > 1
                    ? args.get(1).unboxAs(boolean.class)
                    : BytecodeUtils.constant(false)),
            Type.LONG_TYPE));
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr value = args.get(0);
    JsExpr isHtml = (args.size() == 2) ? args.get(1) : null;

    String callText =
        (isHtml != null)
            ? "soy.$$bidiTextDir(" + value.getText() + ", " + isHtml.getText() + ")"
            : "soy.$$bidiTextDir(" + value.getText() + ")";
    return new JsExpr(callText, Integer.MAX_VALUE);
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.<String>of("soy");
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    PyExpr value = args.get(0);
    PyExpr isHtml = (args.size() == 2) ? args.get(1) : null;

    String callText =
        (isHtml != null)
            ? "bidi.text_dir(" + value.getText() + ", " + isHtml.getText() + ")"
            : "bidi.text_dir(" + value.getText() + ")";
    return new PyExpr(callText, Integer.MAX_VALUE);
  }
}
