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

import static com.google.template.soy.shared.restricted.SoyJavaRuntimeFunctionUtils.toSoyData;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.internal.i18n.SoyBidiUtils;
import com.google.template.soy.javasrc.restricted.JavaCodeUtils;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.javasrc.restricted.SoyJavaSrcFunction;
import com.google.template.soy.javasrc.restricted.SoyJavaSrcFunctionUtils;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.tofu.restricted.SoyAbstractTofuFunction;

import java.util.List;
import java.util.Set;


/**
 * Soy function that maybe inserts a bidi mark character (LRM or RLM) for the current global bidi
 * directionality. The function requires the text string preceding the point where the bidi mark
 * character is to be inserted. If the preceding text string would change the bidi directionality
 * going forward, then the bidi mark is inserted to restore the global bidi directionality.
 * Otherwise, nothing is inserted.
 *
 * @author Aharon Lanin
 * @author Kai Huang
 */
@Singleton
class BidiMarkAfterFunction extends SoyAbstractTofuFunction
    implements SoyJsSrcFunction, SoyJavaSrcFunction {


  /** Provider for the current bidi global directionality. */
  private final Provider<BidiGlobalDir> bidiGlobalDirProvider;


  /**
   * @param bidiGlobalDirProvider Provider for the current bidi global directionality.
   */
  @Inject
  BidiMarkAfterFunction(Provider<BidiGlobalDir> bidiGlobalDirProvider) {
    this.bidiGlobalDirProvider = bidiGlobalDirProvider;
  }


  @Override public String getName() {
    return "bidiMarkAfter";
  }


  @Override public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1, 2);
  }


  @Override public SoyData compute(List<SoyData> args) {
    String text = args.get(0).stringValue();
    //noinspection SimplifiableConditionalExpression
    boolean isHtml = (args.size() == 2) ? args.get(1).booleanValue() : false /* default */;

    int bidiGlobalDir = bidiGlobalDirProvider.get().getStaticValue();
    return toSoyData(SoyBidiUtils.getBidiFormatter(bidiGlobalDir).markAfter(text, isHtml));
  }


  @Override public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr text = args.get(0);
    JsExpr isHtml = (args.size() == 2) ? args.get(1) : null;

    String callText =
        "soy.$$bidiMarkAfter(" + bidiGlobalDirProvider.get().getCodeSnippet() + ", " +
        text.getText() + (isHtml != null ? ", " + isHtml.getText() : "") + ")";

    return new JsExpr(callText, Integer.MAX_VALUE);
  }


  @Override public JavaExpr computeForJavaSrc(List<JavaExpr> args) {
    JavaExpr text = args.get(0);
    JavaExpr isHtml = (args.size() == 2) ? args.get(1) : null;

    String bidiFunctionName = SoyBidiUtils.class.getName() + ".getBidiFormatter(" +
        bidiGlobalDirProvider.get().getCodeSnippet() + ").markAfter";

    return SoyJavaSrcFunctionUtils.toStringJavaExpr(
        JavaCodeUtils.genNewStringData(
            JavaCodeUtils.genFunctionCall(
                bidiFunctionName,
                JavaCodeUtils.genCoerceString(text),
                isHtml != null ? JavaCodeUtils.genCoerceBoolean(isHtml) : "false")));
  }

}
