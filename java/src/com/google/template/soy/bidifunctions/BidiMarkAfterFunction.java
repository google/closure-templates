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

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcFunction;
import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.TypedSoyFunction;
import java.lang.reflect.Method;
import java.util.List;

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
          parameterTypes = {"?", "?"}),
    })
final class BidiMarkAfterFunction extends TypedSoyFunction
    implements SoyJavaSourceFunction, SoyLibraryAssistedJsSrcFunction, SoyPySrcFunction {

  /** Supplier for the current bidi global directionality. */
  private final Supplier<BidiGlobalDir> bidiGlobalDirProvider;

  /** @param bidiGlobalDirProvider Supplier for the current bidi global directionality. */
  BidiMarkAfterFunction(Supplier<BidiGlobalDir> bidiGlobalDirProvider) {
    this.bidiGlobalDirProvider = bidiGlobalDirProvider;
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method MARK_AFTER_NO_HTML =
        JavaValueFactory.createMethod(
            BidiFunctionsRuntime.class, "bidiMarkAfter", BidiGlobalDir.class, SoyValue.class);
    static final Method MARK_AFTER_MAYBE_HTML =
        JavaValueFactory.createMethod(
            BidiFunctionsRuntime.class,
            "bidiMarkAfter",
            BidiGlobalDir.class,
            SoyValue.class,
            boolean.class);
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    if (args.size() == 1) {
      return factory.callStaticMethod(
          Methods.MARK_AFTER_NO_HTML, context.getBidiDir(), args.get(0));
    }
    return factory.callStaticMethod(
        Methods.MARK_AFTER_MAYBE_HTML,
        context.getBidiDir(),
        args.get(0),
        args.get(1).asSoyBoolean());
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
