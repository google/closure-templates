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
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
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
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * Soy function that maybe inserts a bidi mark character (LRM or RLM) for the current global bidi
 * directionality. The function requires the text string preceding the point where the bidi mark
 * character is to be inserted. If the preceding text string would change the bidi directionality
 * going forward, then the bidi mark is inserted to restore the global bidi directionality.
 * Otherwise, nothing is inserted.
 *
 */
@SoyFunctionSignature(
  name = "bidiMarkAfter",
  value = {
    // TODO(b/70946095): should take a string and a bool
    @Signature(returnType = "string", parameterTypes = "?"),
    @Signature(
      returnType = "string",
      parameterTypes = {"?", "?"}
    ),
  }
)
@Singleton
final class BidiMarkAfterFunction extends TypedSoyFunction
    implements SoyJavaFunction,
        SoyLibraryAssistedJsSrcFunction,
        SoyPySrcFunction,
        SoyJbcSrcFunction {

  /** Provider for the current bidi global directionality. */
  private final Provider<BidiGlobalDir> bidiGlobalDirProvider;

  /** @param bidiGlobalDirProvider Provider for the current bidi global directionality. */
  @Inject
  BidiMarkAfterFunction(Provider<BidiGlobalDir> bidiGlobalDirProvider) {
    this.bidiGlobalDirProvider = bidiGlobalDirProvider;
  }

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    SoyValue value = args.get(0);
    boolean isHtml = args.size() == 2 && args.get(1).booleanValue();
    String markAfterKnownDir =
        BidiFunctionsRuntime.bidiMarkAfter(bidiGlobalDirProvider.get(), value, isHtml);
    return StringData.forValue(markAfterKnownDir);
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class JbcSrcMethods {
    static final MethodRef MARK_AFTER =
        MethodRef.create(
                BidiFunctionsRuntime.class,
                "bidiMarkAfter",
                BidiGlobalDir.class,
                SoyValue.class,
                boolean.class)
            .asNonNullable();
  }

  @Override
  public SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args) {
    return SoyExpression.forString(
        JbcSrcMethods.MARK_AFTER.invoke(
            context.getBidiGlobalDir(),
            args.get(0).box(),
            args.size() > 1 ? args.get(1).unboxAs(boolean.class) : BytecodeUtils.constant(false)));
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr value = args.get(0);
    JsExpr isHtml = (args.size() == 2) ? args.get(1) : null;

    String callText =
        "soy.$$bidiMarkAfter("
            + bidiGlobalDirProvider.get().getCodeSnippet()
            + ", "
            + value.getText()
            + (isHtml != null ? ", " + isHtml.getText() : "")
            + ")";

    return new JsExpr(callText, Integer.MAX_VALUE);
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.<String>builder()
        .addAll(bidiGlobalDirProvider.get().getNamespace().asSet())
        .add("soy")
        .build();
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    PyExpr value = args.get(0);
    PyExpr isHtml = (args.size() == 2) ? args.get(1) : null;

    String callText =
        "bidi.mark_after("
            + bidiGlobalDirProvider.get().getCodeSnippet()
            + ", "
            + value.getText()
            + (isHtml != null ? ", " + isHtml.getText() : "")
            + ")";

    return new PyExpr(callText, Integer.MAX_VALUE);
  }
}
