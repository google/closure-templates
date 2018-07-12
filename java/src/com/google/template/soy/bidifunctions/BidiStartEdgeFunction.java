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
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
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
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.TypedSoyFunction;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Soy function that gets the name of the start edge ('left' or 'right') for the current global bidi
 * directionality.
 *
 */
@SoyFunctionSignature(name = "bidiStartEdge", value = @Signature(returnType = "string"))
final class BidiStartEdgeFunction extends TypedSoyFunction
    implements SoyJavaSourceFunction,
        SoyLibraryAssistedJsSrcFunction,
        SoyPySrcFunction,
        SoyJbcSrcFunction {

  /** Supplier for the current bidi global directionality. */
  private final Supplier<BidiGlobalDir> bidiGlobalDirProvider;

  /** @param bidiGlobalDirProvider Supplier for the current bidi global directionality. */
  BidiStartEdgeFunction(Supplier<BidiGlobalDir> bidiGlobalDirProvider) {
    this.bidiGlobalDirProvider = bidiGlobalDirProvider;
  }

  // lazy singleton pattern, allows other backends to avoid the work.
  private static final class Methods {
    static final Method START_EDGE =
        JavaValueFactory.createMethod(
            BidiFunctionsRuntime.class, "bidiStartEdge", BidiGlobalDir.class);
    static final MethodRef START_EDGE_REF = MethodRef.create(START_EDGE).asCheap().asNonNullable();
  }

  @Override
  public JavaValue applyForJavaSource(
      JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
    return factory.callStaticMethod(Methods.START_EDGE, context.getBidiDir());
  }

  @Override
  public SoyExpression computeForJbcSrc(JbcSrcPluginContext context, List<SoyExpression> args) {
    return SoyExpression.forString(Methods.START_EDGE_REF.invoke(context.getBidiGlobalDir()));
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    BidiGlobalDir bidiGlobalDir = bidiGlobalDirProvider.get();
    if (bidiGlobalDir.isStaticValue()) {
      return new JsExpr(
          (bidiGlobalDir.getStaticValue() < 0) ? "'right'" : "'left'", Integer.MAX_VALUE);
    }
    return new JsExpr(
        "(" + bidiGlobalDir.getCodeSnippet() + ") < 0 ? 'right' : 'left'",
        Operator.CONDITIONAL.getPrecedence());
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.copyOf(bidiGlobalDirProvider.get().getNamespace().asSet());
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    BidiGlobalDir bidiGlobalDir = bidiGlobalDirProvider.get();
    return new PyExpr(
        "'right' if (" + bidiGlobalDir.getCodeSnippet() + ") < 0 else 'left'",
        PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL));
  }
}
