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
import com.google.inject.Singleton;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.internal.i18n.BidiUtils;
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
 * Soy function that gets the bidi directionality of a text string (1 for LTR, -1 for RTL, or
 * 0 for none).
 *
 */
@Singleton
class BidiTextDirFunction extends SoyAbstractTofuFunction
    implements SoyJsSrcFunction, SoyJavaSrcFunction {


  @Inject
  BidiTextDirFunction() {}


  @Override public String getName() {
    return "bidiTextDir";
  }


  @Override public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1, 2);
  }


  @Override public SoyData compute(List<SoyData> args) {
    String text = args.get(0).stringValue();
    //noinspection SimplifiableConditionalExpression
    boolean isHtml = (args.size() == 2) ? args.get(1).booleanValue() : false /* default */;

    return toSoyData(BidiUtils.estimateDirection(text, isHtml).ord);
  }


  @Override public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr text = args.get(0);
    JsExpr isHtml = (args.size() == 2) ? args.get(1) : null;

    String callText = (isHtml != null) ?
        "soy.$$bidiTextDir(" + text.getText() + ", " + isHtml.getText() + ")" :
        "soy.$$bidiTextDir(" + text.getText() + ")";
    return new JsExpr(callText, Integer.MAX_VALUE);
  }


  @Override public JavaExpr computeForJavaSrc(List<JavaExpr> args) {
    JavaExpr text = args.get(0);
    JavaExpr isHtml = (args.size() == 2) ? args.get(1) : null;

    String bidiFunctionName = BidiUtils.class.getName() + ".estimateDirection";

    return SoyJavaSrcFunctionUtils.toIntegerJavaExpr(
        JavaCodeUtils.genNewIntegerData(
            JavaCodeUtils.genFunctionCall(
                bidiFunctionName,
                JavaCodeUtils.genCoerceString(text),
                isHtml != null ? JavaCodeUtils.genCoerceBoolean(isHtml) : "false") +
            ".ord"));
  }

}
