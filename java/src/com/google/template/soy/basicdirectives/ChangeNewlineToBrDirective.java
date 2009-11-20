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
import com.google.template.soy.javasrc.restricted.JavaCodeUtils;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.javasrc.restricted.SoyJavaSrcPrintDirective;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.tofu.restricted.SoyTofuPrintDirective;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;


/**
 * A directive that replaces newlines (\n, \r, or \r\n) with HTML line breaks (&lt;br&gt;).
 *
 * @author Felix Chang
 */
@Singleton
public class ChangeNewlineToBrDirective
    implements SoyTofuPrintDirective, SoyJsSrcPrintDirective, SoyJavaSrcPrintDirective {


  private static final Pattern NEWLINE_PATTERN = Pattern.compile("(\\r\\n|\\r|\\n)");


  @Inject
  public ChangeNewlineToBrDirective() {}


  @Override public String getName() {
    return "|changeNewlineToBr";
  }


  @Override public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(0);
  }


  @Override public boolean shouldCancelAutoescape() {
    return false;
  }


  @Override public String applyForTofu(String str, List<SoyData> args) {
    return NEWLINE_PATTERN.matcher(str).replaceAll("<br>");
  }


  @Override public JsExpr applyForJsSrc(JsExpr str, List<JsExpr> args) {
    return new JsExpr("soy.$$changeNewlineToBr(" + str.getText() + ")", Integer.MAX_VALUE);
  }


  @Override public JavaExpr applyForJavaSrc(JavaExpr str, List<JavaExpr> args) {
    return new JavaExpr(
        JavaCodeUtils.genFunctionCall(JavaCodeUtils.UTILS_LIB + ".$$changeNewlineToBr",
            str.getText()),
        String.class, Integer.MAX_VALUE);
  }

}
