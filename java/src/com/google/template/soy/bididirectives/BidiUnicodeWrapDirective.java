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

package com.google.template.soy.bididirectives;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.internal.i18n.SoyBidiUtils;
import com.google.template.soy.javasrc.restricted.JavaCodeUtils;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.javasrc.restricted.SoyJavaSrcFunctionUtils;
import com.google.template.soy.javasrc.restricted.SoyJavaSrcPrintDirective;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.tofu.restricted.SoyAbstractTofuPrintDirective;

import java.util.List;
import java.util.Set;


/**
 * A directive that maybe wraps the output within Unicode bidi control characters -- start character
 * is either LRE (U+202A) or RLE (U+202B), and end character is always PDF (U+202C). This wrapping
 * is only applied when the output text's bidi directionality is different from the bidi global
 * directionality.
 *
 * @author Kai Huang
 * @author Aharon Lanin
 */
@Singleton
public class BidiUnicodeWrapDirective extends SoyAbstractTofuPrintDirective
    implements SoyJsSrcPrintDirective, SoyJavaSrcPrintDirective {


  /** Provider for the current bidi global directionality. */
  private final Provider<BidiGlobalDir> bidiGlobalDirProvider;


  /**
   * @param bidiGlobalDirProvider Provider for the current bidi global directionality.
   */
  @Inject
  BidiUnicodeWrapDirective(Provider<BidiGlobalDir> bidiGlobalDirProvider) {
    this.bidiGlobalDirProvider = bidiGlobalDirProvider;
  }


  @Override public String getName() {
    return "|bidiUnicodeWrap";
  }


  @Override public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(0);
  }


  @Override public boolean shouldCancelAutoescape() {
    return false;
  }


  @Override public SoyData apply(SoyData value, List<SoyData> args) {
    String str = SoyBidiUtils.getBidiFormatter(bidiGlobalDirProvider.get().getStaticValue())
        .unicodeWrap(value.toString(), true);
    return SoyData.createFromExistingData(str);
  }


  @Override public JsExpr applyForJsSrc(JsExpr value, List<JsExpr> args) {
    String codeSnippet = bidiGlobalDirProvider.get().getCodeSnippet();
    return new JsExpr(
        "soy.$$bidiUnicodeWrap(" + codeSnippet + ", " + value.getText() + ")", Integer.MAX_VALUE);
  }


  @Override public JavaExpr applyForJavaSrc(JavaExpr value, List<JavaExpr> args) {
    String bidiFunctionName = SoyBidiUtils.class.getName() + ".getBidiFormatter(" +
        bidiGlobalDirProvider.get().getCodeSnippet() + ").unicodeWrap";

    return SoyJavaSrcFunctionUtils.toStringJavaExpr(
        JavaCodeUtils.genNewStringData(
            JavaCodeUtils.genFunctionCall(
                bidiFunctionName, JavaCodeUtils.genCoerceString(value), "true")));
  }

}
