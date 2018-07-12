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
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcFunction;
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
 * Soy function that maybe inserts an HTML attribute for bidi directionality ('dir=ltr' or
 * 'dir=rtl'). The function requires the text string that will make up the body of the associated
 * HTML tag pair. If the text string is detected to require different directionality than the
 * current global directionality, then the appropriate HTML attribute is inserted. Otherwise,
 * nothing is inserted.
 *
 */
@SoyFunctionSignature(
    name = "bidiDirAttr",
    value = {
      // TODO(b/70946095): should take a string
      @Signature(returnType = "attributes", parameterTypes = "?"),
      @Signature(
          returnType = "attributes",
          // TODO(b/70946095): should take a string and a bool
          parameterTypes = {"?", "?"})
    })
final class BidiDirAttrFunction extends TypedSoyFunction
    implements SoyJavaSourceFunction,
        SoyLibraryAssistedJsSrcFunction,
        SoyPySrcFunction,
        SoyJbcSrcFunction {

  /** Supplier for the current bidi global directionality. */
  private final Supplier<BidiGlobalDir> bidiGlobalDirProvider;

  /** @param bidiGlobalDirProvider Supplier for the current bidi global directionality. */
  BidiDirAttrFunction(Supplier<BidiGlobalDir> bidiGlobalDirProvider) {
    this.bidiGlobalDirProvider = bidiGlobalDirProvider;
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method DIR_ATTR_SANITIZED_NO_HTML =
        JavaValueFactory.createMethod(
            BidiFunctionsRuntime.class,
            "bidiDirAttrSanitized",
            BidiGlobalDir.class,
            SoyValue.class);
    static final Method DIR_ATTR_SANITIZED_MAYBE_HTML =
        JavaValueFactory.createMethod(
            BidiFunctionsRuntime.class,
            "bidiDirAttrSanitized",
            BidiGlobalDir.class,
            SoyValue.class,
            boolean.class);
    static final MethodRef DIR_ATTR_REF =
        MethodRef.create(
                BidiFunctionsRuntime.class,
                "bidiDirAttr",
                BidiGlobalDir.class,
                SoyValue.class,
                boolean.class)
            .asNonNullable();
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    if (args.size() == 1) {
      return factory.callStaticMethod(
          Methods.DIR_ATTR_SANITIZED_NO_HTML, context.getBidiDir(), args.get(0));
    }
    return factory.callStaticMethod(
        Methods.DIR_ATTR_SANITIZED_MAYBE_HTML, context.getBidiDir(), args.get(0), args.get(1));
  }

  @Override
  public SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args) {
    return SoyExpression.forSanitizedString(
        Methods.DIR_ATTR_REF.invoke(
            context.getBidiGlobalDir(),
            args.get(0).box(),
            args.size() > 1 ? args.get(1).unboxAs(boolean.class) : BytecodeUtils.constant(false)),
        SanitizedContentKind.ATTRIBUTES);
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr value = args.get(0);
    JsExpr isHtml = (args.size() == 2) ? args.get(1) : null;

    String callText =
        "soy.$$bidiDirAttr("
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
        "bidi.dir_attr("
            + bidiGlobalDirProvider.get().getCodeSnippet()
            + ", "
            + value.getText()
            + (isHtml != null ? ", " + isHtml.getText() : "")
            + ")";

    return new PyExpr(callText, Integer.MAX_VALUE);
  }
}
