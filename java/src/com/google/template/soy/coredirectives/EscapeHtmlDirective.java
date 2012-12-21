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

package com.google.template.soy.coredirectives;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.javasrc.restricted.JavaCodeUtils;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.javasrc.restricted.SoyJavaSrcPrintDirective;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.shared.restricted.EscapingConventions;
import com.google.template.soy.tofu.restricted.SoyAbstractTofuPrintDirective;

import java.util.List;
import java.util.Set;


/**
 * A directive that HTML-escapes the output.
 *
 * @author Kai Huang
 */
@Singleton
public class EscapeHtmlDirective extends SoyAbstractTofuPrintDirective
    implements SoyJsSrcPrintDirective, SoyJavaSrcPrintDirective {


  public static final String NAME = "|escapeHtml";


  @Inject
  public EscapeHtmlDirective() {}


  @Override public String getName() {
    return NAME;
  }


  @Override public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(0);
  }


  @Override public boolean shouldCancelAutoescape() {
    return true;
  }


  @Override public SoyData apply(SoyData value, List<SoyData> args) {
    if (value instanceof SanitizedContent) {
      SanitizedContent sanitizedContent = (SanitizedContent) value;
      if (sanitizedContent.getContentKind() == SanitizedContent.ContentKind.HTML) {
        return value;
      }
    }
    return SoyData.createFromExistingData(
        EscapingConventions.EscapeHtml.INSTANCE.escape(value.toString()));
  }


  @Override public JsExpr applyForJsSrc(JsExpr value, List<JsExpr> args) {
    return new JsExpr("soy.$$escapeHtml(" + value.getText() + ")", Integer.MAX_VALUE);
  }


  @Override public JavaExpr applyForJavaSrc(JavaExpr value, List<JavaExpr> args) {
    return new JavaExpr(
        JavaCodeUtils.genFunctionCall(JavaCodeUtils.UTILS_LIB + ".$$escapeHtml", value.getText()),
        String.class, Integer.MAX_VALUE);
  }

}
