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
import com.google.template.soy.internal.i18n.SoyBidiUtils;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.BidiGlobalDir;
import com.google.template.soy.tofu.restricted.SoyTofuPrintDirective;

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
public class BidiUnicodeWrapDirective implements SoyTofuPrintDirective, SoyJsSrcPrintDirective {


  /** Provider for the current bidi global directionality. */
  private final Provider<Integer> bidiGlobalDirProvider;


  /**
   * @param bidiGlobalDirProvider Provider for the current bidi global directionality.
   */
  @Inject
  BidiUnicodeWrapDirective(@BidiGlobalDir Provider<Integer> bidiGlobalDirProvider) {
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


  @Override public String applyForTofu(String str, List<SoyData> args) {

    return SoyBidiUtils.getBidiFormatter(bidiGlobalDirProvider.get()).unicodeWrap(str, true);
  }


  @Override public JsExpr applyForJsSrc(JsExpr str, List<JsExpr> args) {
    return new JsExpr(
        "soy.$$bidiUnicodeWrap(" + bidiGlobalDirProvider.get() + ", " + str.getText() + ")",
        Integer.MAX_VALUE);
  }

}
