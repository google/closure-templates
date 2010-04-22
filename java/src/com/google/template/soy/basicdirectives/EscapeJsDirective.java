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

package com.google.template.soy.basicdirectives;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.internal.base.CharEscapers;
import com.google.template.soy.javasrc.restricted.JavaCodeUtils;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.javasrc.restricted.SoyJavaSrcPrintDirective;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.tofu.restricted.SoyTofuPrintDirective;

import java.util.List;
import java.util.Set;


/**
 * A directive that escapes the output to valid Javascript strings.
 *
 * @author Alan Leung
 */
@Singleton
public class EscapeJsDirective
    implements SoyTofuPrintDirective, SoyJsSrcPrintDirective, SoyJavaSrcPrintDirective {


  @Inject
  public EscapeJsDirective() {}


  @Override public String getName() {
    return "|escapeJs";
  }


  @Override public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(0);
  }


  @Override public boolean shouldCancelAutoescape() {
    return true;
  }


  @Override public String applyForTofu(String str, List<SoyData> args) {
    // TODO: This is valid but the result is slightly different from what is expected.
    return CharEscapers.javascriptEscaper().escape(str);
  }


  @Override public JsExpr applyForJsSrc(JsExpr str, List<JsExpr> args) {
    return new JsExpr("soy.$$escapeJs(" + str.getText() + ")", Integer.MAX_VALUE);
  }


  @Override public JavaExpr applyForJavaSrc(JavaExpr str, List<JavaExpr> args) {
    return new JavaExpr(
        JavaCodeUtils.genFunctionCall(JavaCodeUtils.UTILS_LIB + ".$$escapeJs", str.getText()),
        String.class, Integer.MAX_VALUE);
  }

}
