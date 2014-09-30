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
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;

import java.util.List;
import java.util.Set;

import javax.inject.Inject;

/**
 * Soy function that gets the name of the end edge ('left' or 'right') for the current global bidi
 * directionality.
 *
 */
@Singleton
class BidiEndEdgeFunction implements SoyJavaFunction, SoyJsSrcFunction {


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


  @Override public SoyValue computeForJava(List<SoyValue> args) {

    return StringData.forValue(
        (bidiGlobalDirProvider.get().getStaticValue() < 0) ? "left" : "right");
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

}
