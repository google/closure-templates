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

import static com.google.template.soy.javasrc.restricted.SoyJavaSrcFunctionUtils.toStringJavaExpr;
import static com.google.template.soy.shared.restricted.SoyJavaRuntimeFunctionUtils.toSoyData;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.internal.i18n.SoyBidiUtils;
import com.google.template.soy.javasrc.restricted.JavaCodeUtils;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.javasrc.restricted.SoyJavaSrcFunction;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.tofu.restricted.SoyAbstractTofuFunction;

import java.util.List;
import java.util.Set;


/**
 * Soy function that gets the name of the end edge ('left' or 'right') for the current global bidi
 * directionality.
 *
 */
@Singleton
class BidiEndEdgeFunction extends SoyAbstractTofuFunction
    implements SoyJsSrcFunction, SoyJavaSrcFunction {


  /** Provider for the current bidi global directionality. */
  private final Provider<BidiGlobalDir> bidiGlobalDirProvider;


  /**
   * @param bidiGlobalDirProvider Provider for the current bidi global directionality.
   */
  @Inject
  BidiEndEdgeFunction(Provider<BidiGlobalDir> bidiGlobalDirProvider) {
    this.bidiGlobalDirProvider = bidiGlobalDirProvider;
  }


  @Override public String getName() {
    return "bidiEndEdge";
  }


  @Override public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(0);
  }


  @Override public SoyData compute(List<SoyData> args) {

    return toSoyData((bidiGlobalDirProvider.get().getStaticValue() < 0) ? "left" : "right");
  }


  @Override public JsExpr computeForJsSrc(List<JsExpr> args) {

    BidiGlobalDir bidiGlobalDir = bidiGlobalDirProvider.get();
    if (bidiGlobalDir.isStaticValue()) {
      return new JsExpr(
          (bidiGlobalDir.getStaticValue() < 0) ? "'left'" : "'right'", Integer.MAX_VALUE);
    }
    return new JsExpr(
        "(" + bidiGlobalDir.getCodeSnippet() + ") < 0 ? 'left' : 'right'",
        Operator.CONDITIONAL.getPrecedence());
  }


  @Override public JavaExpr computeForJavaSrc(List<JavaExpr> args) {

    BidiGlobalDir bidiGlobalDir = bidiGlobalDirProvider.get();
    if (bidiGlobalDir.isStaticValue()) {
      return toStringJavaExpr(JavaCodeUtils.genNewStringData(
          (bidiGlobalDir.getStaticValue() < 0) ? "\"left\"" : "\"right\""));
    }

    String bidiFunctionName = SoyBidiUtils.class.getName() + ".getBidiFormatter(" +
        bidiGlobalDir.getCodeSnippet() + ").endEdge";
    return toStringJavaExpr(JavaCodeUtils.genNewStringData(
        JavaCodeUtils.genFunctionCall(bidiFunctionName)));
  }

}
